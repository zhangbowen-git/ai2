package com.logistics.mcp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import com.logistics.mcp.model.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 订单数据服务（示例实现：内置 Mock 数据）。
 *
 * <p>真实环境可替换为查询订单中心 / 数据库实现，对上层 Tool 无感知。</p>
 */
@Service
public class MockOrderDataService {

	private static final Logger log = LoggerFactory.getLogger(MockOrderDataService.class);

	/**
	 * 预置订单数据。key=订单号。
	 */
	private static final Map<String, OrderInfo> ORDER_DB = Map.of(
			"SO20260901001",
			new OrderInfo("SO20260901001", "U1001", "华为 Mate 60 Pro 12GB+512GB 雅丹黑", 1,
					new BigDecimal("6999.00"), "已发货", LocalDateTime.of(2026, 9, 1, 10, 20, 33)),
			"SO20260905002",
			new OrderInfo("SO20260905002", "U1001", "小米手环 8 Pro", 2,
					new BigDecimal("798.00"), "已签收", LocalDateTime.of(2026, 9, 5, 15, 8, 12)),
			"SO20260912003",
			new OrderInfo("SO20260912003", "U1002", "戴森 V12 Detect Slim 吸尘器", 1,
					new BigDecimal("3699.00"), "已支付", LocalDateTime.of(2026, 9, 12, 9, 45, 0)));

	/**
	 * 根据订单号查询订单。
	 *
	 * @param orderNo 订单号
	 * @return 订单信息，不存在返回 {@link Optional#empty()}
	 */
	public Optional<OrderInfo> findByOrderNo(String orderNo) {
		OrderInfo order = ORDER_DB.get(orderNo);
		log.info("[MCP] 查询订单 orderNo={}, hit={}", orderNo, order != null);
		return Optional.ofNullable(order);
	}

}
