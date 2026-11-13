package com.logistics.agent.common.context;

/**
 * 当前 Agent 轮次的线程上下文。
 *
 * <p>编排器在执行 Skill 前设置会话ID，MCP 服务端在工具执行中发起
 * Elicitation 请求时，客户端处理器通过本上下文将弹窗路由回对应会话。</p>
 */
public final class TurnContext {

	private static final ThreadLocal<String> SESSION_HOLDER = new ThreadLocal<>();

	private TurnContext() {
	}

	public static void set(String sessionId) {
		SESSION_HOLDER.set(sessionId);
	}

	public static String currentSession() {
		return SESSION_HOLDER.get();
	}

	public static void clear() {
		SESSION_HOLDER.remove();
	}

}
