package com.logistics.agent.mcp;

/**
 * Elicitation 确认结果。
 *
 * @param confirmed 用户是否确认
 * @param comment   用户补充说明（可空）
 * @param timedOut  是否等待超时
 */
public record ConfirmationOutcome(boolean confirmed, String comment, boolean timedOut) {

	/**
	 * 构造确认结果。
	 */
	public static ConfirmationOutcome of(boolean confirmed, String comment, boolean timedOut) {
		return new ConfirmationOutcome(confirmed, comment, timedOut);
	}

}
