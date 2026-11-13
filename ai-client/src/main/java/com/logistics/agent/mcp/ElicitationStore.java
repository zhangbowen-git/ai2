package com.logistics.agent.mcp;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.logistics.agent.common.context.TurnContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
@Component
public class ElicitationStore {

	private static final Logger log = LoggerFactory.getLogger(ElicitationStore.class);

	/** 默认会话标识（无任何活跃会话时兜底）。 */
	private static final String DEFAULT_SESSION = "default-session";

	/** 活跃会话：sessionId → 最近活跃时间戳。 */
	private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();

	/** 待确认请求：sessionId → pending。 */
	private final ConcurrentHashMap<String, PendingConfirmation> pendings = new ConcurrentHashMap<>();

	/** SSE 弹窗 presenter：sessionId → 消息消费者。 */
	private final ConcurrentHashMap<String, Consumer<String>> presenters = new ConcurrentHashMap<>();

	/**
	 * 待确认请求内部模型。
	 */
	private record PendingConfirmation(String message, CompletableFuture<ConfirmationOutcome> future,
			long deadlineMillis) {
	}

	/**
	 * 注册 SSE 弹窗 presenter。
	 */
	public void registerPresenter(String sessionId, Consumer<String> presenter) {
		presenters.put(sessionId, presenter);
	}

	/**
	 * 移除 presenter。
	 */
	public void removePresenter(String sessionId) {
		presenters.remove(sessionId);
	}

	/**
	 * 标记会话进入活跃对话轮次。
	 */
	public void markActive(String sessionId) {
		if (StringUtils.hasText(sessionId)) {
			activeSessions.put(sessionId, System.currentTimeMillis());
		}
	}

	/**
	 * 标记会话轮次结束。
	 */
	public void markInactive(String sessionId) {
		if (StringUtils.hasText(sessionId)) {
			activeSessions.remove(sessionId);
		}
	}

	/**
	 * 选取当前 Elicitation 归属会话：优先线程上下文，其次最近活跃会话，最后默认会话。
	 */
	public String pickActiveSession() {
		String threadSession = TurnContext.currentSession();
		if (StringUtils.hasText(threadSession)) {
			return threadSession;
		}
		return activeSessions.entrySet().stream()
				.max(Comparator.comparingLong(Map.Entry::getValue))
				.map(Map.Entry::getKey)
				.orElse(DEFAULT_SESSION);
	}

	/**
	 * 阻塞等待用户确认。
	 *
	 * @param sessionId     会话ID
	 * @param message       弹窗确认文案
	 * @param timeoutSeconds 最大等待秒数
	 * @return 确认结果（超时 / 异常均按用户未确认处理）
	 */
	public ConfirmationOutcome awaitConfirmation(String sessionId, String message, long timeoutSeconds) {
		CompletableFuture<ConfirmationOutcome> future = new CompletableFuture<>();
		PendingConfirmation pending = new PendingConfirmation(message, future,
				System.currentTimeMillis() + timeoutSeconds * 1000L);

		PendingConfirmation stale = pendings.put(sessionId, pending);
		if (stale != null) {
			stale.future().complete(ConfirmationOutcome.of(false, null, true));
		}

		// 推送 SSE 确认弹窗
		Consumer<String> presenter = presenters.get(sessionId);
		if (presenter != null) {
			try {
				presenter.accept(message);
			}
			catch (Exception ex) {
				log.warn("推送 Elicitation 弹窗失败 sessionId={}", sessionId, ex);
			}
		}
		else {
			log.warn("会话无 SSE presenter，确认弹窗无法下发 sessionId={}", sessionId);
		}

		try {
			return future.get(timeoutSeconds, TimeUnit.SECONDS);
		}
		catch (TimeoutException ex) {
			log.info("Elicitation 等待用户确认超时 sessionId={}", sessionId);
			return ConfirmationOutcome.of(false, null, true);
		}
		catch (Exception ex) {
			log.warn("Elicitation 等待确认异常 sessionId={}", sessionId, ex);
			return ConfirmationOutcome.of(false, null, false);
		}
		finally {
			pendings.remove(sessionId, pending);
		}
	}

	/**
	 * 用户经确认接口提交结果。
	 *
	 * @param sessionId 会话ID
	 * @param confirmed 是否确认
	 * @param comment   补充说明
	 * @return 是否存在待确认请求并完成
	 */
	public boolean complete(String sessionId, boolean confirmed, String comment) {
		PendingConfirmation pending = pendings.get(sessionId);
		if (pending == null) {
			return false;
		}
		return pending.future().complete(ConfirmationOutcome.of(confirmed, comment, false));
	}

}
