package com.logistics.mcp.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单基础信息（McpTool 返回模型）。
 *
 * @param orderNo     订单号
 * @param userId      所属用户ID（用于订单归属校验）
 * @param productName 商品名称
 * @param quantity    商品数量
 * @param amount      订单金额
 * @param orderStatus 订单状态：已下单/已支付/已发货/已完成/已取消
 * @param createTime  下单时间
 */
public record OrderInfo(String orderNo, String userId, String productName, Integer quantity, BigDecimal amount,
						String orderStatus, LocalDateTime createTime) {
}
