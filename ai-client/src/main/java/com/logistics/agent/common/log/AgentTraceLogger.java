package com.logistics.agent.common.log;

import com.logistics.agent.common.model.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Agent 全链路日志记录器。
 *
 * <p>记录内容：会话ID、用户ID、用户提问、命中 Skill、调用工具
 * （MCP / 本地 Tool / 知识库）、入参、返回结果；
 * 同时包含知识库文档导入日志、向量入库日志。</p>
 *
 * <p>通过 {@link MDC} 输出 trace 字段，便于日志系统按 sessionId 检索全链路。</p>
 */
@Component
public class AgentTraceLogger {

	private static final Logger log = LoggerFactory.getLogger("AGENT-TRACE");

	/**
	 * 会话轮次开始。
	 */
	public void sessionStart(ChatSession session, String question) {
		MDC.put("sessionId", session.sessionId());
		MDC.put("userId", session.userId());
		log.info("[会话开始] sessionId={}, userId={}, question={}", session.sessionId(), session.userId(), question);
	}

	/**
	 * 意图识别结果。
	 */
	public void intent(ChatSession session, String intent, String reason) {
		log.info("[意图识别] sessionId={}, intent={}, reason={}", session.sessionId(), intent, reason);
	}

	/**
	 * Skill 路由。
	 */
	public void routeSkill(ChatSession session, String skillName) {
		log.info("[Skill 路由] sessionId={}, skill={}", session.sessionId(), skillName);
	}

	/**
	 * 工具调用（通用：MCP / 本地 Tool / 知识库检索）。
	 *
	 * @param toolType   工具类型：MCP / LOCAL / KNOWLEDGE
	 * @param toolName   工具名称
	 * @param arguments  入参
	 */
	public void toolCall(ChatSession session, String toolType, String toolName, String arguments) {
		log.info("[工具调用] sessionId={}, type={}, tool={}, args={}",
				session.sessionId(), toolType, toolName, arguments);
	}

	/**
	 * 工具返回结果。
	 */
	public void toolResult(ChatSession session, String toolType, String toolName, String result) {
		log.info("[工具返回] sessionId={}, type={}, tool={}, result={}",
				session.sessionId(), toolType, toolName, abbreviate(result));
	}

	/**
	 * 会话轮次结束。
	 */
	public void sessionEnd(ChatSession session) {
		log.info("[会话结束] sessionId={}", session.sessionId());
		MDC.remove("sessionId");
		MDC.remove("userId");
	}

	/**
	 * 知识库文档导入日志。
	 */
	public void kbImport(Long docId, String docName, String action, String detail) {
		log.info("[知识库导入] docId={}, docName={}, action={}, detail={}", docId, docName, action, detail);
	}

	/**
	 * 向量入库日志。
	 */
	public void vectorPersist(Long docId, int chunkCount) {
		log.info("[向量入库] docId={}, chunkCount={}", docId, chunkCount);
	}

	/**
	 * 截断超长结果，防止单条日志过大。
	 */
	private String abbreviate(String text) {
		if (text == null) {
			return null;
		}
		return text.length() > 1000 ? text.substring(0, 1000) + "...(truncated)" : text;
	}

}
