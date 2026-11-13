package com.logistics.agent.common.api;

/**
 * 统一响应结构。
 *
 * @param code    状态码：200 成功；其他为业务/系统错误码
 * @param message 提示信息（面向用户的友好文案）
 * @param data    业务数据
 * @param <T>     数据类型
 */
public record R<T>(int code, String message, T data) {

	public static <T> R<T> ok(T data) {
		return new R<>(200, "success", data);
	}

	public static <T> R<T> ok() {
		return new R<>(200, "success", null);
	}

	public static <T> R<T> fail(int code, String message) {
		return new R<>(code, message, null);
	}

}
