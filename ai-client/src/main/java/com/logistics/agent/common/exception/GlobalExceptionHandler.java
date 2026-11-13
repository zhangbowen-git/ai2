package com.logistics.agent.common.exception;

import com.logistics.agent.common.api.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>统一将底层异常转换为友好文案返回，避免向前端暴露技术细节；
 * SSE 流式链路中的异常在编排器内部捕获处理。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 业务异常：按异常携带的友好文案返回。
	 */
	@ExceptionHandler(BizException.class)
	public R<Void> handleBizException(BizException ex) {
		log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
		return R.fail(ex.getCode(), ex.getMessage());
	}

	/**
	 * 参数非法异常。
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public R<Void> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("参数异常: {}", ex.getMessage());
		return R.fail(400, ex.getMessage());
	}

	/**
	 * 上传文件超限。
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		log.warn("上传文件超过大小限制: {}", ex.getMessage());
		return R.fail(413, "上传文件过大，请压缩或拆分后重新上传。");
	}

	/**
	 * 静态资源不存在（最常见的是浏览器自动请求 favicon.ico）。
	 *
	 * <p>这是正常的 404 请求，不应作为系统异常输出 ERROR 堆栈。</p>
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public R<Void> handleNoResourceFound(NoResourceFoundException ex) {
		log.debug("静态资源不存在: {}", ex.getResourcePath());
		return R.fail(404, "资源不存在。");
	}

	/**
	 * 兜底异常：统一友好文案，底层细节仅记录日志。
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public R<Void> handleException(Exception ex) {
		log.error("系统异常", ex);
		return R.fail(500, "服务暂时开小差了，请稍后再试～如持续出现可联系人工客服处理。");
	}

}
