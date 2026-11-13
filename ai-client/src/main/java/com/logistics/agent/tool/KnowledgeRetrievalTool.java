package com.logistics.agent.tool;

import java.util.List;

import com.logistics.agent.kb.service.KnowledgeQueryService;
import com.logistics.agent.kb.service.RetrievedSnippet;
import com.logistics.agent.mcp.McpClientSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Spring AI 本地原生 Tool：知识库检索。
 *
 * <p>接收用户问题，调用 Milvus 向量库相似度查询，召回相关文档片段返回给 Agent；
 * 向量库无召回时，兜底读取 MCP Resource 售后 SOP（二选一/互补能力）。</p>
 */
@Component
public class KnowledgeRetrievalTool {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalTool.class);

	private final KnowledgeQueryService knowledgeQueryService;

	private final McpClientSupport mcpClientSupport;

	public KnowledgeRetrievalTool(KnowledgeQueryService knowledgeQueryService,
			McpClientSupport mcpClientSupport) {
		this.knowledgeQueryService = knowledgeQueryService;
		this.mcpClientSupport = mcpClientSupport;
	}

	/**
	 * 检索售后知识库。
	 *
	 * @param question 用户问题
	 * @param category 知识库分类（可空，默认售后类）
	 * @return 召回片段原文与来源说明；无结果时兜底 MCP SOP
	 */
	@Tool(name = "search_knowledge_base",
			description = "从向量知识库中检索与用户问题最相关的文档片段，用于解答各类知识库文档问题，"
					+ "包括售后政策、平台规则、功能接口说明、物流规则等。参数 question 为用户的问题原文；"
					+ "category 为文档分类（如 after_sales、logistics、order_policy），不填则检索全部分类。"
					+ "返回知识库片段原文及来源文档。")
	public String searchKnowledgeBase(
			@ToolParam(description = "用户的问题原文", required = true) String question,
			@ToolParam(required = false, description = "知识库分类，如 after_sales、logistics、order_policy，不填则检索全部") String category) {

		String queryCategory = StringUtils.hasText(category) ? category : null;
		List<RetrievedSnippet> snippets;
		try {
			snippets = knowledgeQueryService.search(question, queryCategory, 4);
		}
		catch (Exception ex) {
			log.error("知识库检索工具调用异常 question={}", question, ex);
			return "知识库检索服务暂时不可用，请稍后再试；如问题紧急可申请转接人工客服。";
		}

		if (snippets != null && !snippets.isEmpty()) {
			StringBuilder builder = new StringBuilder("从知识库检索到以下参考内容，请严格依据原文回答：\n\n");
			for (int i = 0; i < snippets.size(); i++) {
				RetrievedSnippet snippet = snippets.get(i);
				builder.append("【片段").append(i + 1).append("｜来源文档：").append(snippet.docName()).append("】\n")
						.append(snippet.content()).append("\n\n");
			}
			return builder.toString();
		}

		// 向量库无召回：仅在售后分类下兜底读取 MCP Resource 售后 SOP
		if ("after_sales".equals(queryCategory)) {
			log.info("向量库无召回（after_sales），尝试读取 MCP SOP Resource question={}", question);
			String sopContent = mcpClientSupport.readResource("sop://after-sales/return-policy").orElse(null);
			if (StringUtils.hasText(sopContent)) {
				return "知识库暂无匹配内容，以下为售后 SOP 标准文档原文，请依据原文回答：\n\n" + sopContent;
			}
		}
		return "未检索到与该问题相关的知识库内容，请如实告知用户暂无相关信息，"
				+ "并可建议用户转接人工客服咨询。";
	}

}
