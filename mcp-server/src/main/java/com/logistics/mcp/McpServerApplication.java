package com.logistics.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server 独立服务启动类。
 *
 * <p>对外通过 Streamable HTTP 协议暴露：</p>
 * <ul>
 *     <li>McpTool：订单查询、物流轨迹查询、退货申请提交（含 Elicitation 人机确认）</li>
 *     <li>McpResource：售后 SOP 文档</li>
 *     <li>McpPrompt：远程托管的 Skill 系统提示词</li>
 * </ul>
 *
 * @author senior engineer
 */
@SpringBootApplication
public class McpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpServerApplication.class, args);
	}

}
