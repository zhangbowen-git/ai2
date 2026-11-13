package com.logistics.agent.tool;

/**
 * 人工客服转接结果。
 *
 * @param ticketNo       工单编号
 * @param queueNo        排队编号
 * @param estimatedWait  预估接入时长（分钟）
 * @param message        面向用户的说明文案
 */
public record HandoffResult(String ticketNo, int queueNo, int estimatedWait, String message) {
}
