package com.logistics.agent.common.exception;

/**
 * 业务异常。
 *
 * <p>异常信息面向最终用户，使用友好文案，不暴露底层技术细节。</p>
 */
public class BizException extends RuntimeException {

	private final int code;

	public BizException(String message) {
		super(message);
		this.code = 500;
	}

	public BizException(int code, String message) {
		super(message);
		this.code = code;
	}

	public BizException(String message, Throwable cause) {
		super(message, cause);
		this.code = 500;
	}

	public int getCode() {
		return code;
	}

}
