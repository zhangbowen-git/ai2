package com.logistics.mcp.resource;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

/**
 * MCP Resource：售后 SOP 文档资源。
 *
 * <p>与向量知识库二选一/互为补充：Agent 解答售后政策时，可读取本资源原文，
 * 也可通过本地知识库检索 Tool 召回向量片段。</p>
 */
@Service
public class AfterSalesSopResource {

	/**
	 * 七天无理由退货规则。
	 */
	private static final String RETURN_POLICY = """
			《七天无理由退货规则》

			1. 适用范围：消费者在收到商品后 7 日内（自签收次日零时起计算），商品完好、不影响二次销售的，可申请无理由退货。
			2. 不适用商品：
			   (1) 消费者定制的商品；
			   (2) 鲜活易腐的商品；
			   (3) 在线下载或者消费者拆封的音像制品、计算机软件等数字化商品；
			   (4) 交付的报纸、期刊；
			   (5) 其他根据商品性质并经消费者在购买时确认不宜退货的商品。
			3. 退回运费：无理由退货的，退回商品的运费由消费者承担；商品质量问题的，运费由商家承担。
			4. 退款时限：商家收到退回商品并确认完好后，应在 7 个工作日内原路退回货款。
			""";

	/**
	 * 换货规则。
	 */
	private static final String EXCHANGE_POLICY = """
			《换货规则》

			1. 商品签收后 15 日内出现性能故障的，消费者可选择换货或修理。
			2. 换货时，商家应当免费为消费者更换同型号同规格的商品。
			3. 换货后的商品，三包有效期自换货之日起重新计算。
			4. 同型号同规格产品停产的，消费者可选择退货或更换不低于原产品性能的其他型号。
			""";

	/**
	 * 保修政策。
	 */
	private static final String WARRANTY_POLICY = """
			《保修政策》

			1. 整机保修 1 年，主要部件（主板、屏幕、电池）保修 2 年，自商品签收之日起计算。
			2. 保修范围内的性能故障，维修免收人工费、配件费。
			3. 下列情况不属于免费保修范围：
			   (1) 超过三包有效期的；
			   (2) 人为损坏（摔落、进液、私自拆修等）；
			   (3) 不可抗力造成的损坏；
			   (4) 无法提供有效购买凭证的。
			""";

	private static final Map<String, String> SOP_DOCS = Map.of(
			"return-policy", RETURN_POLICY,
			"exchange-policy", EXCHANGE_POLICY,
			"warranty-policy", WARRANTY_POLICY);

	/**
	 * 按文档类型读取售后 SOP。
	 *
	 * @param docType 文档类型：return-policy 退货规则 / exchange-policy 换货规则 / warranty-policy 保修政策
	 * @return SOP 文档原文；文档不存在返回提示文本
	 */
	@McpResource(uri = "sop://after-sales/{docType}",
			name = "after_sales_sop",
			title = "售后 SOP 文档",
			description = "退换货规则、换货规则、保修政策等售后标准作业文档。"
					+ "docType 取值：return-policy（退货规则）、exchange-policy（换货规则）、warranty-policy（保修政策）。",
			mimeType = "text/plain")
	public String getAfterSalesSop(String docType) {
		String content = SOP_DOCS.get(docType);
		if (content == null) {
			return "未找到文档类型 " + docType + "，可选文档：return-policy、exchange-policy、warranty-policy。";
		}
		return content;
	}

}
