package com.logistics.agent.skill;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Skill 级 {@link ChatClient} 工厂。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>从 MCP 自动发现的工具回调中，按 Skill 声明的工具名过滤绑定；</li>
 *     <li>同时挂载 Skill 的 Spring AI 本地原生 Tool（@Tool Bean）；</li>
 *     <li>共享同一 {@link ChatMemory}，每个 Skill 独立 ChatClient 实例，懒加载并缓存。</li>
 * </ul>
 */
@Component
public class SkillChatClientFactory {

	private final ChatClient.Builder chatClientBuilder;

	private final ChatMemory chatMemory;

	private final SyncMcpToolCallbackProvider mcpToolCallbackProvider;

	private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

	public SkillChatClientFactory(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
			SyncMcpToolCallbackProvider mcpToolCallbackProvider) {
		this.chatClientBuilder = chatClientBuilder;
		this.chatMemory = chatMemory;
		this.mcpToolCallbackProvider = mcpToolCallbackProvider;
	}

	/**
	 * 获取或创建 Skill 专属 ChatClient。
	 *
	 * @param skillName    Skill 唯一名称（缓存键）
	 * @param mcpToolNames 需要绑定的 MCP 原始工具名
	 * @param localTools   需要绑定的本地 @Tool Bean
	 * @return 已配置的 ChatClient
	 */
	public ChatClient getOrCreate(String skillName, List<String> mcpToolNames, Object... localTools) {
		return clientCache.computeIfAbsent(skillName, key -> build(mcpToolNames, localTools));
	}

	private ChatClient build(List<String> mcpToolNames, Object... localTools) {
		List<ToolCallback> selectedMcpCallbacks = Arrays.stream(mcpToolCallbackProvider.getToolCallbacks())
				.filter(callback -> matchesMcpTool(callback, mcpToolNames))
				.toList();

		return chatClientBuilder.clone()
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.defaultToolCallbacks(selectedMcpCallbacks)
				.defaultTools(localTools)
				.build();
	}

	/**
	 * 判断回调是否为 Skill 需要的 MCP 工具：以原始工具名（去除连接前缀）匹配。
	 */
	private boolean matchesMcpTool(ToolCallback callback, List<String> requiredNames) {
		if (callback instanceof SyncMcpToolCallback syncCallback) {
			return requiredNames.contains(syncCallback.getOriginalToolName());
		}
		return requiredNames.contains(callback.getToolDefinition().name());
	}

}
