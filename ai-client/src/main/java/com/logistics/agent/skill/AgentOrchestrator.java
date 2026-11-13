package com.logistics.agent.skill;

import com.logistics.agent.common.log.AgentTraceLogger;
import com.logistics.agent.common.model.ChatSession;
import com.logistics.agent.common.util.OrderNoValidator;
import com.logistics.agent.mcp.ElicitationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent 编排器。
 *
 * <p>对话主链路：意图识别 → Skill 路由 → 流式回答；负责 Elicitation 活跃会话
 * 生命周期标记、意图兜底与统一异常友好文案。多轮记忆、工具调用由各 Skill 的
 * ChatClient / Advisor 链完成。</p>
 */
@Service
public class AgentOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

	/**
	 * 无关问题兜底回复。
	 */
	private static final String OUT_OF_SCOPE_REPLY = "抱歉，我是智能客服助手，主要为您提供订单查询、"
			+ "物流跟踪、退换货及售后政策咨询服务。您可以换一个与订单或售后相关的问题，"
			+ "也可以输入“转人工”，我会为您接入人工客服。";

	/**
	 * 处理异常统一友好文案。
	 */
	private static final String ERROR_REPLY = "非常抱歉，处理您的问题时服务出现异常，请稍后再试，"
			+ "或输入“转人工”联系人工客服。";

	private final IntentRouter intentRouter;

	private final OrderConsultSkill orderConsultSkill;

	private final AfterSalesSkill afterSalesSkill;

	private final KnowledgeQaSkill knowledgeQaSkill;

	private final ElicitationStore elicitationStore;

	private final AgentTraceLogger traceLogger;

	public AgentOrchestrator(IntentRouter intentRouter, OrderConsultSkill orderConsultSkill,
			AfterSalesSkill afterSalesSkill, KnowledgeQaSkill knowledgeQaSkill,
			ElicitationStore elicitationStore, AgentTraceLogger traceLogger) {
		this.intentRouter = intentRouter;
		this.orderConsultSkill = orderConsultSkill;
		this.afterSalesSkill = afterSalesSkill;
		this.knowledgeQaSkill = knowledgeQaSkill;
		this.elicitationStore = elicitationStore;
		this.traceLogger = traceLogger;
	}

	/**
	 * 对话主入口（流式）。
	 *
	 * @param question 用户问题
	 * @param session  会话
	 * @return 回答内容流
	 */
	public Flux<String> chat(String question, ChatSession session) {
		// 会话启动
		traceLogger.sessionStart(session, question);
		// 标记会话活跃
		elicitationStore.markActive(session.sessionId());
		// 输入校验：用户消息中疑似订单号但格式非法时，直接友好提示核对，不进入工具调用
		String malformedOrderNo = OrderNoValidator.findMalformed(question);
		if (malformedOrderNo != null) {
			elicitationStore.markInactive(session.sessionId());
			return Flux.just("您提供的订单号 " + malformedOrderNo + " 格式似乎不正确。"
					+ "正确的订单号以 SO 开头，后接 11 位数字（例如 SO20260901001），请您核对后重新提供。");
		}
		// 意图识别
		Intent  intent = intentRouter.classify(question, session);
		// 意图 → Skill 路由
		Skill skill = routeSkill(intent);
		if (skill == null) {
			elicitationStore.markInactive(session.sessionId());
			return Flux.just(OUT_OF_SCOPE_REPLY);
		}
		// 记录意图识别结果
		traceLogger.routeSkill(session, skill.name());
		try {
			return skill.answer(question, session)
					.onErrorResume(ex -> {
						log.error("Skill 执行异常 skill={}, sessionId={}", skill.name(), session.sessionId(), ex);
						return Flux.just(ERROR_REPLY);
					})
					.doFinally(signal -> elicitationStore.markInactive(session.sessionId()));
		}
		catch (Exception ex) {
			log.error("对话启动异常 sessionId={}", session.sessionId(), ex);
			elicitationStore.markInactive(session.sessionId());
			return Flux.just(ERROR_REPLY);
		}
	}

	/**
	 * 意图 → Skill 路由。
	 *
	 * @return Skill；OUT_OF_SCOPE 返回 null（走兜底文案）
	 */
	private Skill routeSkill(Intent intent) {
		return switch (intent) {
			case ORDER_QUERY, LOGISTICS_QUERY, HUMAN_HANDOFF -> orderConsultSkill;
			case RETURN_REQUEST, AFTER_SALES_POLICY -> afterSalesSkill;
			case KNOWLEDGE_QA -> knowledgeQaSkill;
			case OUT_OF_SCOPE -> null;
		};
	}

}
