package com.logistics.agent.kb.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 人工客服工单实体（本地「人工客服转接 Tool」生成）。
 */
@TableName("agent_ticket")
public class AgentTicket {

	@TableId(type = IdType.ASSIGN_ID)
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

	/** 工单编号。 */
	private String ticketNo;

	/** 来源会话ID。 */
	private String sessionId;

	/** 用户ID。 */
	private String userId;

	/** 转人工原因。 */
	private String reason;

	/** 排队序号。 */
	private Integer queueNo;

	/** 状态：0 排队中 / 1 已接入 / 2 已结束。 */
	private Integer status;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTicketNo() {
		return ticketNo;
	}

	public void setTicketNo(String ticketNo) {
		this.ticketNo = ticketNo;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Integer getQueueNo() {
		return queueNo;
	}

	public void setQueueNo(Integer queueNo) {
		this.queueNo = queueNo;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
