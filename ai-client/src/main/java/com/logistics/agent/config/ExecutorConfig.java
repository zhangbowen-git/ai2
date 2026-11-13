package com.logistics.agent.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池配置。
 *
 * <p>SSE 对话请求返回 {@code SseEmitter} 后，编排逻辑在该线程池异步执行，
 * 避免阻塞 Servlet 容器线程。</p>
 */
@Configuration
public class ExecutorConfig {

	/**
	 * 对话编排线程池。
	 */
	@Bean("chatTaskExecutor")
	public ThreadPoolTaskExecutor chatTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(8);
		executor.setMaxPoolSize(32);
		executor.setQueueCapacity(200);
		executor.setThreadNamePrefix("chat-agent-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

}
