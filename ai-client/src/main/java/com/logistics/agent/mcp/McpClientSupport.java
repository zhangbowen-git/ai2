package com.logistics.agent.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MCP 客户端支撑组件。
 *
 * <p>基于自动装配的 {@link McpSyncClient} 提供：</p>
 * <ul>
 *     <li>MCP Resource 读取（售后 SOP 文档，与向量知识库互补/二选一）；</li>
 *     <li>MCP Prompt 远程拉取（Skill 角色描述、回答规范动态下发，无需重启应用）。</li>
 * </ul>
 *
 * <p>所有方法失败均返回 {@link Optional#empty()}，由调用方走本地兜底，
 * 避免底层连接异常影响主链路。</p>
 */
@Component
public class McpClientSupport {

	private static final Logger log = LoggerFactory.getLogger(McpClientSupport.class);

	private final List<McpSyncClient> mcpClients;

	public McpClientSupport(List<McpSyncClient> mcpClients) {
		this.mcpClients = mcpClients;
	}

	/**
	 * 读取 MCP Resource 文本内容。
	 *
	 * @param uri 资源URI，如 sop://after-sales/return-policy
	 * @return 资源文本；读取失败或无内容返回 empty
	 */
	public Optional<String> readResource(String uri) {
		McpSyncClient client = primaryClient();
		if (client == null) {
			return Optional.empty();
		}
		try {
			ReadResourceResult result = client.readResource(new ReadResourceRequest(uri));
			String text = result.contents().stream()
					.filter(TextResourceContents.class::isInstance)
					.map(TextResourceContents.class::cast)
					.map(TextResourceContents::text)
					.reduce((a, b) -> a + "\n" + b)
					.orElse(null);
			return Optional.ofNullable(text);
		}
		catch (Exception ex) {
			log.warn("读取 MCP Resource 失败 uri={}, reason={}", uri, ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * 拉取远程托管 Prompt 文本（拼接全部消息内容）。
	 *
	 * @param name      Prompt 名称，如 order_consult_system
	 * @param arguments 模板参数（可空）
	 * @return Prompt 文本；拉取失败返回 empty
	 */
	public Optional<String> getPromptText(String name, Map<String, Object> arguments) {
		McpSyncClient client = primaryClient();
		if (client == null) {
			return Optional.empty();
		}
		try {
			GetPromptResult result = client.getPrompt(new GetPromptRequest(name,
					arguments != null ? arguments : Map.of()));
			String text = result.messages().stream()
					.map(PromptMessage::content)
					.filter(TextContent.class::isInstance)
					.map(TextContent.class::cast)
					.map(TextContent::text)
					.reduce((a, b) -> a + "\n" + b)
					.orElse(null);
			return Optional.ofNullable(text);
		}
		catch (Exception ex) {
			log.warn("拉取 MCP Prompt 失败 name={}, reason={}", name, ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * 取主 MCP 客户端（当前仅一个 order-mcp 连接）。
	 */
	private McpSyncClient primaryClient() {
		return (mcpClients != null && !mcpClients.isEmpty()) ? mcpClients.get(0) : null;
	}

}
