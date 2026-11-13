package com.logistics.agent.common.util;

import java.util.regex.Pattern;

/**
 * 订单号格式校验工具。
 *
 * <p>订单号规则：前缀 SO + 11 位数字，例如 SO20260901001。</p>
 */
public final class OrderNoValidator {

	/**
	 * 订单号正则：SO + 11 位数字。
	 */
	private static final Pattern ORDER_NO_PATTERN = Pattern.compile("^SO\\d{11}$");

	private OrderNoValidator() {
	}

	/**
	 * 校验订单号格式是否合法。
	 *
	 * @param orderNo 订单号
	 * @return true 合法；false 非法
	 */
	public static boolean isValid(String orderNo) {
		return orderNo != null && ORDER_NO_PATTERN.matcher(orderNo).matches();
	}

	/**
	 * 从文本中提取订单号（取第一个匹配项）。
	 *
	 * @param text 文本
	 * @return 订单号；未找到返回 null
	 */
	public static String extract(String text) {
		if (text == null) {
			return null;
		}
		var matcher = Pattern.compile("SO\\d{11}").matcher(text);
		return matcher.find() ? matcher.group() : null;
	}

	/**
	 * 查找文本中疑似订单号（SO 后紧跟数字）但格式不合法的片段。
	 *
	 * @param text 文本
	 * @return 第一个非法疑似订单号；不存在返回 null
	 */
	public static String findMalformed(String text) {
		if (text == null) {
			return null;
		}
		var matcher = Pattern.compile("SO\\d+").matcher(text);
		while (matcher.find()) {
			String candidate = matcher.group();
			if (!isValid(candidate)) {
				return candidate;
			}
		}
		return null;
	}

}
