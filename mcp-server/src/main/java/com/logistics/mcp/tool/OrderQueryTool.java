package com.logistics.mcp.tool;

import com.logistics.mcp.model.OrderInfo;
import com.logistics.mcp.service.MockOrderDataService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP Tool：订单信息查询。
 *
 * <p>可被任意 MCP 客户端（Spring AI 应用、Claude Desktop 等）发现并复用。</p>
 */
@Service
public class OrderQueryTool {

	private final MockOrderDataService orderDataService;

	public OrderQueryTool(MockOrderDataService orderDataService) {
		this.orderDataService = orderDataService;
	}

	/**
	 * 根据订单号查询订单基础信息。
	 *
	 * @param orderNo 订单号，格式 SO + 11 位数字，例如 SO20260901001
	 * @return 订单信息；订单不存在时返回 null（由调用方 / Agent 给出友好提示）
	 */
	@McpTool(name = "query_order_info",
			description = "根据订单号查询订单基础信息，包含商品名称、数量、订单金额、下单时间、订单状态。"
					+ "参数 orderNo 为订单号，格式 SO+11位数字，例如 SO20260901001。订单不存在时返回 null。")
	public OrderInfo queryOrderInfo(
			@McpToolParam(description = "订单号，例如 SO20260901001", required = true) String orderNo) {
		return orderDataService.findByOrderNo(orderNo).orElse(null);
	}

}
