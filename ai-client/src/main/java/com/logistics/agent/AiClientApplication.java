package com.logistics.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI 2.0 智能客服 AI Client 启动类。
 *
 * <p>核心能力：</p>
 * <ul>
 *     <li>自然语言意图识别 + 智能路由（订单咨询 Skill / 售后处理 Skill）</li>
 *     <li>多轮会话记忆、SSE 流式输出</li>
 *     <li>混合调用 MCP 远程 Tool + Spring AI 本地 Tool + 向量知识库检索</li>
 *     <li>知识库管理：文档导入、切片、向量化入 Milvus、元数据持久化</li>
 * </ul>
 *
 * @author senior engineer
 */
@SpringBootApplication
@MapperScan("com.logistics.agent.**.mapper")
public class AiClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiClientApplication.class, args);
	}

}
