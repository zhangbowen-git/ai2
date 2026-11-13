package com.logistics.mcp.model;

import java.util.List;

/**
 * 物流轨迹信息（McpTool 返回模型）。
 *
 * @param orderNo           订单号
 * @param carrier           快递公司
 * @param trackingNo        运单号
 * @param logisticsStatus   物流状态：运输中/派送中/已签收
 * @param nodes             物流节点轨迹（按时间倒序，最新在前）
 * @param estimatedDelivery 预计送达时间
 */
public record LogisticsInfo(String orderNo, String carrier, String trackingNo, String logisticsStatus,
							List<LogisticsNode> nodes, String estimatedDelivery) {

	/**
	 * 物流节点。
	 *
	 * @param time        节点时间
	 * @param description 节点描述
	 */
	public record LogisticsNode(String time, String description) {
	}

}
