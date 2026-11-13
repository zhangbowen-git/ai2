package com.logistics.agent.skill;

import java.util.List;
import java.util.Locale;

import com.logistics.agent.common.model.ChatSession;
import com.logistics.agent.common.util.OrderNoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 意图路由器。
 *
 * <p>基于 LLM 结构化输出（{@code entity(Intent.class)}）对用户最新消息做意图分类，
 * 供 {@link AgentOrchestrator} 选择对应 Skill。分类器本身不绑定任何业务工具。</p>
 */
@Component
public class IntentRouter {

	private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

	private static final List<String> HUMAN_HANDOFF_TERMS = List.of(
			"转人工", "人工客服", "找人工", "人工接入", "联系人工");

	private static final List<String> AFTER_SALES_POLICY_TERMS = List.of(
			"退货规则", "换货规则", "退货政策", "换货政策", "售后政策", "售后规则",
			"售后时限", "保修政策", "保修期", "三包", "退换货规则", "退款时限");

	private static final List<String> KNOWLEDGE_QA_TERMS = List.of(
			"知识库", "负荷资源", "监测详情", "分路名称", "数据贯通",
			"功能点清单", "功能点", "接口说明", "接口调用", "字段说明",
			"功能说明", "系统使用说明", "业务流程", "平台规则", "操作说明",
			"配置说明", "页面说明", "菜单说明", "报表说明");

	private static final List<String> RETURN_REQUEST_TERMS = List.of(
			"申请退货", "我要退货", "办理退货", "提交退货",
			"申请换货", "我要换货", "申请退款", "办理退款");

	private static final List<String> LOGISTICS_TERMS = List.of(
			"物流", "快递", "轨迹", "运单", "货到哪", "送达", "派送", "运输");

	private static final List<String> ORDER_QUERY_TERMS = List.of(
			"订单状态", "订单详情", "我的订单", "订单金额", "商品信息",
			"下单时间", "订单号");

	/**
	 * 分类器系统 Prompt。
	 */
	private static final String CLASSIFIER_SYSTEM_PROMPT = """
			你是智能客服的意图分类器。请根据用户最新消息，从下列枚举中选择唯一最匹配的意图，只输出枚举值本身：
			- ORDER_QUERY：查询特定订单的详情（需提供订单号），如订单状态、商品、数量、金额。关键词：订单号SO开头、我的订单
			- LOGISTICS_QUERY：查询物流状态、快递轨迹、货到哪了、预计送达时间
			- RETURN_REQUEST：申请退货、换货、退款
			- AFTER_SALES_POLICY：咨询退换货规则、售后时限、保修政策等售后规则
			- KNOWLEDGE_QA：查询知识库文档中的内容，包括功能接口说明、分路名称列表、负荷资源监测详情、平台规则、业务流程、系统使用说明、数据贯通监测等文档类问题。注意：只要问题涉及"功能""接口""列表""流程""规则""说明"等关键词且不是查特定订单，优先归入此类
			- HUMAN_HANDOFF：要求转人工、找人工客服、人工接入
			- OUT_OF_SCOPE：与订单、物流、售后、知识库均无关的闲聊或其他问题

			判断优先级：含转人工 → HUMAN_HANDOFF；含订单号(SO开头) → ORDER_QUERY/LOGISTICS_QUERY；
			含售后政策 → AFTER_SALES_POLICY；含分路名称、负荷资源监测、功能点、接口说明、数据贯通等
			知识库特征 → KNOWLEDGE_QA；含退换货操作 → RETURN_REQUEST；
			含物流轨迹 → LOGISTICS_QUERY；含订单详情 → ORDER_QUERY。
			""";

	private final ChatClient.Builder chatClientBuilder;

	/**
	 * 分类器 ChatClient（懒加载，避免启动早期模型配置未就绪）。
	 */
	private volatile ChatClient classifierClient;

	public IntentRouter(ChatClient.Builder chatClientBuilder) {
		this.chatClientBuilder = chatClientBuilder;
	}

	/**
	 * 分类用户意图。
	 *
	 * @param question 用户最新消息
	 * @param session  会话
	 * @return 意图；明确规则优先，规则无法判断时调用 LLM，失败时安全兜底为 OUT_OF_SCOPE
	 */
	public Intent classify(String question, ChatSession session) {
		Intent ruleIntent = classifyByRules(question);
		if (ruleIntent != null) {
			log.info("【意图规则识别】 sessionId={}, intent={}, question={}",
					session.sessionId(), ruleIntent, question);
			return ruleIntent;
		}

		try {
			Intent intent = classifier().prompt()
					.system(CLASSIFIER_SYSTEM_PROMPT)
					.user(question)
					.call()			// ← 非流式调用，让 LLM 分类意图
					.entity(Intent.class);
			log.info("【意图识别结果】 sessionId={}, intent={}", session.sessionId(), intent);
			if (intent == null) {
				log.warn("意图识别返回空结果，按 OUT_OF_SCOPE 处理 sessionId={}", session.sessionId());
				return Intent.OUT_OF_SCOPE;
			}
			return intent;
		}
		catch (Exception ex) {
			Intent fallback = classifyByRules(question);
			if (fallback == null) {
				fallback = Intent.OUT_OF_SCOPE;
			}
			log.warn("意图识别异常，使用安全兜底 sessionId={}, fallback={}",
					session.sessionId(), fallback, ex);
			return fallback;
		}
	}

	/**
	 * 对语义边界明确的问题做确定性分类，避免模型波动导致知识库问题被误路由到订单咨询。
	 *
	 * @return 命中规则的意图；规则无法确定时返回 null，交给 LLM 分类
	 */
	static Intent classifyByRules(String question) {
		if (!StringUtils.hasText(question)) {
			return null;
		}
		String text = question.strip().toLowerCase(Locale.ROOT);

		if (containsAny(text, HUMAN_HANDOFF_TERMS)) {
			return Intent.HUMAN_HANDOFF;
		}
		if (OrderNoValidator.extract(text) != null) {
			return containsAny(text, LOGISTICS_TERMS) ? Intent.LOGISTICS_QUERY : Intent.ORDER_QUERY;
		}
		if (containsAny(text, AFTER_SALES_POLICY_TERMS)) {
			return Intent.AFTER_SALES_POLICY;
		}
		if (containsAny(text, KNOWLEDGE_QA_TERMS)) {
			return Intent.KNOWLEDGE_QA;
		}
		if (containsAny(text, RETURN_REQUEST_TERMS)) {
			return Intent.RETURN_REQUEST;
		}
		if (containsAny(text, LOGISTICS_TERMS)) {
			return Intent.LOGISTICS_QUERY;
		}
		if (containsAny(text, ORDER_QUERY_TERMS)) {
			return Intent.ORDER_QUERY;
		}
		return Intent.KNOWLEDGE_QA;
	}

	private static boolean containsAny(String text, List<String> terms) {
		return terms.stream().anyMatch(text::contains);
	}

	private ChatClient classifier() {
		if (classifierClient == null) {
			synchronized (this) {
				if (classifierClient == null) {
					classifierClient = chatClientBuilder.clone().build();
				}
			}
		}
		return classifierClient;
	}

}
