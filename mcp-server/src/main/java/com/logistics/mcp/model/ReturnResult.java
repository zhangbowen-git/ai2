package com.logistics.mcp.model;

/**
 * 退货申请提交结果（McpTool 返回模型）。
 *
 * @param returnNo 退货单号（提交成功时有值）
 * @param orderNo  关联订单号
 * @param reason   退货原因
 * @param status   状态：SUBMITTED 已提交 / CANCELLED 用户取消 / PENDING 待确认 / FAILED 失败
 * @param message  面向用户的说明文案
 */
public record ReturnResult(String returnNo, String orderNo, String reason, String status, String message) {

	public static ReturnResult submitted(String returnNo, String orderNo, String reason) {
		return new ReturnResult(returnNo, orderNo, reason, "SUBMITTED",
				"退货申请已提交成功，退货单号：" + returnNo + "。工作人员将在 1-3 个工作日内完成审核，审核结果会通过短信通知您。");
	}

	public static ReturnResult cancelled(String orderNo) {
		return new ReturnResult(null, orderNo, null, "CANCELLED", "好的，已取消本次退货申请，您的订单保持原状。");
	}

	public static ReturnResult pending(String orderNo, String reason) {
		return new ReturnResult(null, orderNo, reason, "PENDING", "退货申请尚未提交，需要您确认信息后才会执行提交。");
	}

	public static ReturnResult failed(String orderNo, String message) {
		return new ReturnResult(null, orderNo, null, "FAILED", message);
	}

}
