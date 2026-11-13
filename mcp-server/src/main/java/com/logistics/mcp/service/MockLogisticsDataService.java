package com.logistics.mcp.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.logistics.mcp.model.LogisticsInfo;
import com.logistics.mcp.model.LogisticsInfo.LogisticsNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 物流数据服务（示例实现：内置 Mock 轨迹数据）。
 *
 * <p>真实环境可替换为调用快递 100 / 物流中台接口。</p>
 */
@Service
public class MockLogisticsDataService {

	private static final Logger log = LoggerFactory.getLogger(MockLogisticsDataService.class);

	private static final Map<String, LogisticsInfo> LOGISTICS_DB = Map.of(
			"SO20260901001",
			new LogisticsInfo("SO20260901001", "顺丰速运", "SF1024687952", "运输中",
					List.of(
							new LogisticsNode("2026-09-20 08:30", "快件已到达【杭州转运中心】，正在分拣"),
							new LogisticsNode("2026-09-19 22:11", "快件已从【深圳集散中心】发出"),
							new LogisticsNode("2026-09-19 14:05", "顺丰速运已收取快件")),
					"2026-09-21 18:00 前"),
			"SO20260905002",
			new LogisticsInfo("SO20260905002", "京东物流", "JD99871234501", "已签收",
					List.of(
							new LogisticsNode("2026-09-07 16:42", "快件已由本人签收，感谢使用京东物流"),
							new LogisticsNode("2026-09-07 09:15", "快递员正在为您派送，请保持电话畅通"),
							new LogisticsNode("2026-09-07 07:02", "快件已到达【杭州西湖区营业部】")),
					"2026-09-07 18:00 前"));

	/**
	 * 根据订单号查询物流轨迹。
	 *
	 * @param orderNo 订单号
	 * @return 物流信息，暂无轨迹返回 {@link Optional#empty()}
	 */
	public Optional<LogisticsInfo> findByOrderNo(String orderNo) {
		LogisticsInfo info = LOGISTICS_DB.get(orderNo);
		log.info("[MCP] 查询物流 orderNo={}, hit={}", orderNo, info != null);
		return Optional.ofNullable(info);
	}

}
