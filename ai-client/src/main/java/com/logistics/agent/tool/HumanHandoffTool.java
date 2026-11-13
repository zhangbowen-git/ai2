package com.logistics.agent.tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.logistics.agent.common.context.TurnContext;
import com.logistics.agent.kb.entity.AgentTicket;
import com.logistics.agent.kb.mapper.AgentTicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Spring AI 本地原生 Tool：人工客服转接。
 *
 * <p>识别用户转人工诉求，生成人工工单持久化，返回排队编号与预估接入时长。</p>
 *
 * <p><b>公共工具</b>：订单咨询 Skill、售后处理 Skill 均可绑定复用。</p>
 */
@Component
public class HumanHandoffTool {

	private static final Logger log = LoggerFactory.getLogger(HumanHandoffTool.class);

	private final AgentTicketMapper ticketMapper;

	/**
	 * 排队序号生成器（fallback：工单表计数不可用时使用）。
	 */
	private final AtomicInteger queueSequence = new AtomicInteger(0);

	public HumanHandoffTool(AgentTicketMapper ticketMapper) {
		this.ticketMapper = ticketMapper;
	}

	/**
	 * 创建人工客服工单。
	 *
	 * @param reason 转人工原因（可空）
	 * @param userId 用户ID（可空）
	 * @return 排队信息
	 */
	@Tool(name = "transfer_to_human_agent",
			description = "当用户明确要求转接人工客服、联系人工、找人工时调用。创建人工客服工单，"
					+ "返回工单编号、排队编号和预估接入时长。参数 reason 为转人工原因，userId 为用户ID（均可不填）。")
	public HandoffResult transferToHumanAgent(
			@ToolParam(required = false, description = "转人工原因") String reason,
			@ToolParam(required = false, description = "用户ID") String userId) {

		String sessionId = TurnContext.currentSession();
		int queueNo;
		try {
			Long waitingCount = ticketMapper.selectCount(new LambdaQueryWrapper<AgentTicket>()
					.eq(AgentTicket::getStatus, 0));
			queueNo = (waitingCount == null ? 0 : waitingCount.intValue()) + 1;
		}
		catch (Exception ex) {
			log.warn("查询排队工单数失败，使用本地序号: {}", ex.getMessage());
			queueNo = queueSequence.incrementAndGet();
		}

		String ticketNo = "CS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
				+ String.format("%03d", queueNo);
		int estimatedWaitMinutes = 2 + queueNo % 8;

		AgentTicket ticket = new AgentTicket();
		ticket.setTicketNo(ticketNo);
		ticket.setSessionId(sessionId);
		ticket.setUserId(StringUtils.hasText(userId) ? userId : "anonymous");
		ticket.setReason(StringUtils.hasText(reason) ? reason : "用户主动请求转人工客服");
		ticket.setQueueNo(queueNo);
		ticket.setStatus(0);
		ticket.setCreateTime(LocalDateTime.now());
		try {
			ticketMapper.insert(ticket);
		}
		catch (Exception ex) {
			log.warn("人工工单持久化失败（不影响排队结果返回）: {}", ex.getMessage());
		}

		String message = "已为您转接人工客服，当前排队编号为 " + queueNo
				+ "，预计 " + estimatedWaitMinutes + " 分钟内接入，请您耐心等待并保持页面在线。"
				+ "工单编号：" + ticketNo + "。";
		log.info("人工客服工单已创建 ticketNo={}, queueNo={}, sessionId={}", ticketNo, queueNo, sessionId);
		return new HandoffResult(ticketNo, queueNo, estimatedWaitMinutes, message);
	}

}
