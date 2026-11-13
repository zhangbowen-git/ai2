package com.logistics.agent.skill;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.logistics.agent.common.model.ChatSession;
import com.logistics.agent.mcp.McpClientSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

/**
 * Skill 抽象基类。
 *
 * <p>封装通用执行流程：解析系统 Prompt（优先远程 MCP Prompt 动态拉取，失败使用本地兜底）
 * → 绑定会话记忆ID → 流式调用。子类只需声明身份、远程 Prompt 名称、工具集合与本地系统 Prompt。</p>
 */
public abstract class AbstractSkill implements Skill {

	private static final Logger log = LoggerFactory.getLogger(AbstractSkill.class);

	private final SkillChatClientFactory clientFactory;

	private final McpClientSupport mcpClientSupport;

	private final String skillName;

	// 远程 Prompt 名称
	private final String remotePromptName;

	private final String localSystemPrompt;

	private final List<String> mcpToolNames;

	private final Object[] localTools;

	/**
	 * 已确认不支持流式响应的 Skill。Qwen 某些流式分片缺少 choices 时，
	 * Spring AI 会抛出 NoSuchElementException("No value present")；首次失败后
	 * 该 Skill 在当前进程内直接使用非流式调用，避免每轮重复失败。
	 */
	private final Set<String> nonStreamingSkills = ConcurrentHashMap.newKeySet();

	/**
	 * 构造 Skill 基类。
	 *
	 * @param clientFactory     ChatClient 工厂
	 * @param mcpClientSupport  MCP 支撑（远程 Prompt）
	 * @param skillName         Skill 名称
	 * @param remotePromptName  远程 MCP Prompt 名称
	 * @param localSystemPrompt 本地兜底系统 Prompt
	 * @param mcpToolNames      绑定的 MCP 工具名
	 * @param localTools        绑定的本地 Tool Bean
	 */
	protected AbstractSkill(SkillChatClientFactory clientFactory, McpClientSupport mcpClientSupport,
			String skillName, String remotePromptName, String localSystemPrompt, List<String> mcpToolNames,
			Object... localTools) {
		this.clientFactory = clientFactory;
		this.mcpClientSupport = mcpClientSupport;
		this.skillName = skillName;
		this.remotePromptName = remotePromptName;
		this.localSystemPrompt = localSystemPrompt;
		this.mcpToolNames = mcpToolNames;
		this.localTools = localTools;
	}

	@Override
	public String name() {
		return skillName;
	}

	@Override
	public Flux<String> answer(String question, ChatSession session) {
		ChatClient chatClient = clientFactory.getOrCreate(skillName, mcpToolNames, localTools);
		String systemPrompt = resolveSystemPrompt();

		if (nonStreamingSkills.contains(skillName)) {
			return callNonStreaming(chatClient, systemPrompt, question, session);
		}

		// 优先流式调用；若 LLM 流式响应格式不兼容（如部分 Qwen 流式 chunk 缺少 choices），
		// 首次自动降级为非流式调用，并熔断该 Skill 的后续流式请求。
		return chatClient.prompt()
				.system(systemPrompt)	// ← 流式调用，SSE 逐字返回
				.user(question)
				.advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, session.sessionId()))
				.stream()
				.content()
				.onErrorResume(ex -> {
					if (isStreamingIncompatibility(ex)) {
						nonStreamingSkills.add(skillName);
						log.info("Qwen 流式响应不兼容，Skill 已切换为非流式 skill={}, reason={}",
								skillName, ex.getMessage());
					}
					else {
						log.warn("流式调用异常，降级为非流式 skill={}", skillName, ex);
					}
					return callNonStreaming(chatClient, systemPrompt, question, session);
				});
	}

	/**
	 * 非流式调用。SSE 层仍会收到同一个 Flux，只是内容作为完整片段一次发送。
	 */
	private Flux<String> callNonStreaming(ChatClient chatClient, String systemPrompt, String question,
			ChatSession session) {
		try {
			String content = chatClient.prompt()
					.system(systemPrompt)
					.user(question)
					.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.sessionId()))
					.call()
					.content();
			return Flux.just(content == null ? "抱歉，服务暂时异常，请稍后再试。" : content);
		}
		catch (Exception ex) {
			log.error("非流式调用失败 skill={}", skillName, ex);
			return Flux.just("抱歉，服务暂时异常，请稍后再试，或输入\"转人工\"联系人工客服。");
		}
	}

	/**
	 * 判断是否为 Qwen 流式空 choices 引起的格式兼容异常。
	 */
	private boolean isStreamingIncompatibility(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof NoSuchElementException
					|| (current.getMessage() != null && current.getMessage().contains("No value present"))) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	/**
	 * 解析系统 Prompt：每轮优先从 MCP Server 动态拉取，保证远程修改即时生效；
	 * 拉取失败时使用本地兜底 Prompt。
	 */
	private String resolveSystemPrompt() {
		return mcpClientSupport.getPromptText(remotePromptName, Map.of()).orElse(localSystemPrompt);
	}

}
