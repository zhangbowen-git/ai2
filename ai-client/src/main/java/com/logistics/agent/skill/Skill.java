package com.logistics.agent.skill;

import reactor.core.publisher.Flux;

import com.logistics.agent.common.model.ChatSession;

/**
 * 业务 Skill 抽象。
 *
 * <p>Spring AI 2.0 无官方独立的「Skill」注解，这里自建 Skill 契约：
 * 每个 Skill 封装专属角色 Prompt、绑定的工具集合（MCP 远程 Tool / Spring AI 本地 Tool）
 * 与统一的流式回答入口。</p>
 */
public interface Skill {

	/**
	 * Skill 名称（用于全链路日志标识）。
	 */
	String name();

	/**
	 * Skill 职责描述。
	 */
	String description();

	/**
	 * 流式回答用户问题。
	 *
	 * @param question 用户问题
	 * @param session  会话（含 sessionId / userId）
	 * @return 回答内容流
	 */
	Flux<String> answer(String question, ChatSession session);

}
