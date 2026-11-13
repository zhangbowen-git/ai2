package com.logistics.agent.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import com.logistics.agent.common.api.R;
import com.logistics.agent.common.context.TurnContext;
import com.logistics.agent.common.model.ChatSession;
import com.logistics.agent.mcp.ElicitationStore;
import com.logistics.agent.skill.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话接口 Controller。
 *
 * <p>提供：</p>
 * <ul>
 *     <li>SSE 流式对话（ 增量内容 delta / elicitation（诱导/征询）机制 / done本轮回答完成 / error 事件）；</li>
 *     <li>同步对话接口（非流式场景）；</li>
 *     <li>MCP Elicitation 用户确认回传接口。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	/**
	 * 同步对话等待上限。
	 */
	private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(120);

	/**
	 * Agent 编排器。
	 *
	 * <p>对话主链路：意图识别 → Skill 路由 → 流式回答；负责 Elicitation 活跃会话
	 * 生命周期标记、意图兜底与统一异常友好文案。多轮记忆、工具调用由各 Skill 的
	 * ChatClient / Advisor 链完成。</p>
	 */
	private final AgentOrchestrator orchestrator;

	/**
	 * MCP Elicitation 会话注册表。
	 *
	 * <p>解决「MCP 连接在多会话间共享、Elicitation 请求不自带会话标识」的问题：</p>
	 * <ol>
	 *     <li>每个对话轮次开始时标记会话为活跃（带时间戳）；</li>
	 *     <li>Elicitation 请求到达时，选取最近活跃会话作为确认对象；</li>
	 *     <li>通过该会话注册的 SSE presenter 把确认弹窗推送给前端；</li>
	 *     <li>阻塞等待用户经确认接口返回结果，支持超时自动取消。</li>
	 * </ol>
	 */
	private final ElicitationStore elicitationStore;

	private final Executor chatTaskExecutor;

	public ChatController(AgentOrchestrator orchestrator, ElicitationStore elicitationStore,
			@Qualifier("chatTaskExecutor") Executor chatTaskExecutor) {
		this.orchestrator = orchestrator;
		this.elicitationStore = elicitationStore;
		this.chatTaskExecutor = chatTaskExecutor;
	}

	/**
	 * SSE 流式对话。
	 *
	 * @param message   用户消息
	 * @param sessionId 会话ID（空则自动生成）
	 * @param userId    用户ID（可空）
	 * @return SSE Emitter
	 */
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@RequestParam("message") String message,
			@RequestParam(value = "sessionId", required = false) String sessionId,
			@RequestParam(value = "userId", required = false) String userId) {

		String sessionIdStr = StringUtils.hasText(sessionId) ? sessionId : UUID.randomUUID().toString();
		SseEmitter emitter = new SseEmitter(0L);

		// 注册 Elicitation 弹窗 presenter（必须先于对话执行，确保确认弹窗能即时下发）
		elicitationStore.registerPresenter(sessionIdStr, dialogMessage -> {
			try {
				emitter.send(SseEmitter.event().name("elicitation").data(dialogMessage));
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		});

		String resolvedUserId = StringUtils.hasText(userId) ? userId : "anonymous";
//		chatTaskExecutor.execute(() -> runTurn(message, conversationId, resolvedUserId, emitter));
		runTurn(message, sessionIdStr, resolvedUserId, emitter);
		return emitter;
	}

	/**
	 * 同步对话接口。
	 */
	@PostMapping("/send")
	public R<ChatReplyVO> send(@RequestBody ChatRequest request) {
		if (request == null || !StringUtils.hasText(request.message())) {
			return R.fail(400, "消息内容不能为空。");
		}
		String conversationId = StringUtils.hasText(request.sessionId()) ? request.sessionId()
				: UUID.randomUUID().toString();
		String userId = StringUtils.hasText(request.userId()) ? request.userId() : "anonymous";
		ChatSession session = new ChatSession(conversationId, userId);

		String content;
		try {
			content = orchestrator.chat(request.message(), session)
					.collect(Collectors.joining())
					.block(BLOCK_TIMEOUT);
		}
		catch (Exception ex) {
			log.error("同步对话异常 sessionId={}", conversationId, ex);
			return R.fail(500, "对话服务暂时不可用，请稍后再试。");
		}
		return R.ok(new ChatReplyVO(conversationId, content == null ? "" : content));
	}

	/**
	 * Elicitation 用户确认回传。
	 *
	 * @param sessionId 会话ID
	 * @param confirmed 是否确认
	 * @param comment   补充说明（可空）
	 * @return 是否命中待确认请求
	 */
	@PostMapping("/confirm")
	public R<Boolean> confirm(@RequestParam("sessionId") String sessionId,
			@RequestParam("confirmed") boolean confirmed,
			@RequestParam(value = "comment", required = false) String comment) {
		boolean completed = elicitationStore.complete(sessionId, confirmed, comment);
		if (!completed) {
			return R.fail(404, "未找到待确认请求，可能已超时或已处理。");
		}
		return R.ok(Boolean.TRUE);
	}

	/**
	 * 异步执行单个对话轮次：保持同一工作线程贯穿全程（ThreadLocal 上下文 + 阻塞迭代）。
	 */
	private void runTurn(String message, String sessionId, String userId, SseEmitter emitter) {
		ChatSession session = new ChatSession(sessionId, userId);
		TurnContext.set(sessionId);
		try {
			orchestrator.chat(message, session).toIterable().forEach(chunk -> {
				try {
					emitter.send(SseEmitter.event().name("delta").data(chunk));
				}catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
			emitter.send(SseEmitter.event().name("done").data(sessionId));
			emitter.complete();
		}
		catch (Exception ex) {
			log.error("SSE 对话轮次异常 sessionId={}", sessionId, ex);
			try {
				emitter.send(SseEmitter.event().name("error").data("服务暂时异常，请稍后再试。"));
			}
			catch (IOException ignored) {
				// 客户端可能已断开
			}
			emitter.complete();
		}
		finally {
			TurnContext.clear();
			elicitationStore.removePresenter(sessionId);
		}
	}

	/**
	 * 同步对话请求体。
	 *
	 * @param message   消息内容
	 * @param sessionId 会话ID（可空）
	 * @param userId    用户ID（可空）
	 */
	public record ChatRequest(String message, String sessionId, String userId) {
	}

	/**
	 * 同步对话响应体。
	 *
	 * @param sessionId 会话ID
	 * @param content   完整回答
	 */
	public record ChatReplyVO(String sessionId, String content) {
	}

}
