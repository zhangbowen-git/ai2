package com.logistics.agent.mcp;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MCP Elicitation 客户端处理器。
 *
 * <p>当 MCP Server 在工具执行中发起 Elicitation（退货提交前二次确认）时，
 * 本处理器被自动调用：归属会话 → 推送确认弹窗 → 等待用户响应，
 * 最终按 MCP 协议返回 {@link ElicitResult}。</p>
 *
 * <p>content 字段与 MCP Server 端 {@code ReturnConfirmation} 结构化模式对应：
 * {@code confirmed}（Boolean）、{@code comment}（String）。</p>
 */
@Component
public class McpElicitationHandler {

	private static final Logger log = LoggerFactory.getLogger(McpElicitationHandler.class);

	private final ElicitationStore elicitationStore;

	private final long awaitTimeoutSeconds;

	public McpElicitationHandler(ElicitationStore elicitationStore,
			@Value("${agent.elicitation.await-timeout-seconds:120}") long awaitTimeoutSeconds) {
		this.elicitationStore = elicitationStore;
		this.awaitTimeoutSeconds = awaitTimeoutSeconds;
	}

	/**
	 * 处理来自 order-mcp 连接的 Elicitation 请求。
	 *
	 * @param request Elicitation 请求（含确认文案）
	 * @return 用户确认结果
	 */
	@McpElicitation(clients = "order-mcp")
	public ElicitResult handleElicitation(ElicitRequest request) {
		String sessionId = elicitationStore.pickActiveSession();
		String message = StringUtils.hasText(request.message()) ? request.message() : "请确认是否继续当前操作。";
		log.info("收到 MCP Elicitation 请求 sessionId={}", sessionId);

		ConfirmationOutcome outcome = elicitationStore.awaitConfirmation(sessionId, message, awaitTimeoutSeconds);

		if (outcome.confirmed()) {
			String comment = StringUtils.hasText(outcome.comment()) ? outcome.comment() : "";
			return ElicitResult.builder(ElicitResult.Action.ACCEPT)
					.content(Map.of("confirmed", true, "comment", comment))
					.build();
		}
		log.info("Elicitation 用户未确认/取消/超时 sessionId={}, timedOut={}", sessionId, outcome.timedOut());
		return ElicitResult.builder(ElicitResult.Action.DECLINE)
				.content(Map.of("confirmed", false))
				.build();
	}

}
