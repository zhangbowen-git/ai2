package com.logistics.agent.common.model;

/**
 * 一次对话所属会话。
 *
 * @param sessionId 会话ID（贯穿多轮对话，对应 ChatMemory 的 conversationId）
 * @param userId    用户ID（用于订单归属 / 知识库权限控制）
 */
public record ChatSession(String sessionId, String userId) {
}
