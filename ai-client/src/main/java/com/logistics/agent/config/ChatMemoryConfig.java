package com.logistics.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮会话记忆配置。
 *
 * <p>{@link MessageWindowChatMemory} 以滑动消息窗口保留会话内上下文
 * （订单号、历史问答），用户无需重复输入信息。</p>
 *
 * <p>示例使用内存存储；生产环境可替换为 {@code JdbcChatMemoryRepository}
 * （MySQL 持久化），对上层 Skill 无感知。</p>
 */
@Configuration
public class ChatMemoryConfig {

	/**
	 * 会话记忆：每个会话最多保留最近 20 条消息。
	 */
	@Bean
	public ChatMemory chatMemory() {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.maxMessages(20)
				.build();
	}

}
