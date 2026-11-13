package com.logistics.agent.skill;

/**
 * 对话意图枚举（LLM 结构化意图识别结果）。
 */
public enum Intent {

	/** 订单信息查询。 */
	ORDER_QUERY,

	/** 物流轨迹查询。 */
	LOGISTICS_QUERY,

	/** 退货 / 换货等售后申请。 */
	RETURN_REQUEST,

	/** 售后政策 / 规则咨询。 */
	AFTER_SALES_POLICY,

	/** 通用知识库问答。 */
	KNOWLEDGE_QA,

	/** 转人工客服。 */
	HUMAN_HANDOFF,

	/** 无关问题 / 闲聊兜底。 */
	OUT_OF_SCOPE

}
