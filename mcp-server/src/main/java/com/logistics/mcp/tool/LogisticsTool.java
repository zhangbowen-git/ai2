package com.logistics.mcp.tool;

import com.logistics.mcp.model.LogisticsInfo;
import com.logistics.mcp.service.MockLogisticsDataService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP Tool：物流轨迹查询。
 */
@Service
public class LogisticsTool {

	private final MockLogisticsDataService logisticsDataService;

	public LogisticsTool(MockLogisticsDataService logisticsDataService) {
		this.logisticsDataService = logisticsDataService;
	}

	/**
	 * 根据订单号查询实时物流轨迹。
	 *
	 * @param orderNo 订单号
	 * @return 物流信息（快递公司、运单号、节点轨迹、预计送达时间）；暂无物流信息返回 null
	 */
	@McpTool(name = "query_logistics",
			description = "根据订单号查询实时物流轨迹，包含快递公司、运单号、最新物流节点、预计送达时间。"
					+ "参数 orderNo 为订单号，例如 SO20260901001。订单尚未发货或暂无轨迹时返回 null。")
	public LogisticsInfo queryLogistics(
			@McpToolParam(description = "订单号，例如 SO20260901001", required = true) String orderNo) {
		return logisticsDataService.findByOrderNo(orderNo).orElse(null);
	}

}
