package com.logistics.mcp.tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import com.logistics.mcp.model.OrderInfo;
import com.logistics.mcp.model.ReturnConfirmation;
import com.logistics.mcp.model.ReturnResult;
import com.logistics.mcp.service.MockOrderDataService;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.stereotype.Service;

/**
 * MCP Tool：退货申请提交。
 *
 * <p><b>MCP Elicitation 人机交互确认机制：</b>提交退货单前，
 * 通过 {@code context.elicit(...)} 向用户弹窗确认订单号与退货原因，
 * 用户明确确认后才执行真正的提交，防止误提交。</p>
 */
@Service
public class ReturnOrderTool {

	private static final Logger log = LoggerFactory.getLogger(ReturnOrderTool.class);

	private final MockOrderDataService orderDataService;

	private final AtomicInteger returnSequence = new AtomicInteger(0);

	public ReturnOrderTool(MockOrderDataService orderDataService) {
		this.orderDataService = orderDataService;
	}

	/**
	 * 提交退货申请（提交前强制人机二次确认）。
	 *
	 * @param context MCP 请求上下文，用于发起 Elicitation
	 * @param orderNo 订单号
	 * @param reason  退货原因
	 * @return 退货提交结果
	 */
	@McpTool(name = "submit_return",
			description = "提交退货申请。注意：该工具在真正提交前会通过 MCP Elicitation 弹窗向用户二次确认"
					+ "订单号与退货原因，只有用户确认后才会提交；用户取消则不执行任何操作。"
					+ "参数 orderNo 为订单号，reason 为退货原因。")
	public ReturnResult submitReturn(McpSyncRequestContext context,
			@McpToolParam(description = "订单号，例如 SO20260901001", required = true) String orderNo,
			@McpToolParam(description = "退货原因，例如：商品质量问题、七天无理由退货", required = true) String reason) {

		log.info("[MCP] 收到退货申请 orderNo={}, reason={}", orderNo, reason);

		// 1. 校验订单是否存在
		OrderInfo order = orderDataService.findByOrderNo(orderNo).orElse(null);
		if (order == null) {
			log.warn("[MCP] 退货申请失败，订单不存在 orderNo={}", orderNo);
			return ReturnResult.failed(orderNo, "未查询到订单 " + orderNo + "，请核对订单号后再申请退货。");
		}

		// 2. MCP Elicitation 人机交互二次确认
		if (context.elicitEnabled()) {
			String dialogMessage = """
					请确认您的退货申请信息：
					订单号：%s
					商品：%s
					退货原因：%s
					请点击【确认】提交退货申请，或点击【取消】放弃本次申请。"""
				.formatted(orderNo, order.productName(), reason);

			StructuredElicitResult<ReturnConfirmation> elicitResult =
					context.elicit(builder -> builder.message(dialogMessage), ReturnConfirmation.class);

			if (elicitResult.action() != ElicitResult.Action.ACCEPT
					|| elicitResult.structuredContent() == null
					|| !Boolean.TRUE.equals(elicitResult.structuredContent().confirmed())) {
				log.info("[MCP] 用户取消退货 orderNo={}", orderNo);
				return ReturnResult.cancelled(orderNo);
			}
			log.info("[MCP] 用户已确认退货 orderNo={}", orderNo);
		}
		else {
			// 客户端不支持 Elicitation 时，不允许直接提交（防止误操作）
			log.warn("[MCP] 客户端不支持 Elicitation，退货申请挂起 orderNo={}", orderNo);
			return ReturnResult.pending(orderNo, reason);
		}

		// 3. 用户已确认，执行退货单提交（示例：生成退货单号）
		String returnNo = "RT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
				+ String.format("%03d", returnSequence.incrementAndGet());
		log.info("[MCP] 退货申请提交成功 returnNo={}, orderNo={}", returnNo, orderNo);
		return ReturnResult.submitted(returnNo, orderNo, reason);
	}

}
