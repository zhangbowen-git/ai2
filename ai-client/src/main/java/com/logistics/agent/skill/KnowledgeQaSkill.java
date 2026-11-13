package com.logistics.agent.skill;

import java.util.List;

import com.logistics.agent.mcp.McpClientSupport;
import com.logistics.agent.tool.HumanHandoffTool;
import com.logistics.agent.tool.KnowledgeRetrievalTool;
import org.springframework.stereotype.Component;

/**
 * Skill3：知识库问答 Skill。
 *
 * <p>专门负责知识库内容问答：接收用户问题 → 调用向量知识库检索Tool
 * 召回相关文档片段 → 严格依据召回内容作答。不绑定任何 MCP 远程工具。</p>
 * <ul>
 *     <li>绑定本地 Tool：search_knowledge_base（知识库向量检索）；</li>
 *     <li>复用本地公共 Tool：transfer_to_human_agent（人工客服转接）；</li>
 *     <li>严格以知识库文档原文为准，无召回内容时如实告知，不编造。</li>
 * </ul>
 */
@Component
public class KnowledgeQaSkill extends AbstractSkill {

	/**
	 * 本 Skill 不绑定 MCP 远程工具。
	 */
	private static final List<String> MCP_TOOLS = List.of();

	/**
	 * 本地兜底系统 Prompt（远程 Prompt 不可用时使用）。
	 */
	private static final String LOCAL_SYSTEM_PROMPT = """
			你是「智能客服-知识库问答专员」，专门负责根据知识库文档内容回答用户问题。
			工作规范：
			1. 回答任何问题前，必须先调用 search_knowledge_base 工具检索知识库，根据召回的文档片段组织回答；
			2. 严格以知识库文档原文为准，不编造、不擅自补充文档外的内容；
			3. 回答时注明信息来源（如"根据《退货政策总则》"），引用关键条款原文；
			4. 知识库未检索到相关内容时，如实告知用户暂无相关信息，并建议转人工客服或换一种方式提问；
			5. 用户明确要求转人工时，调用 transfer_to_human_agent 工具；
			6. 语气专业、清晰，面向非技术用户，避免使用内部术语；
			7. 不回答订单查询、物流查询、退货提交等超出知识库问答范围的问题，引导用户联系对应专员。
			""";

	public KnowledgeQaSkill(SkillChatClientFactory clientFactory, McpClientSupport mcpClientSupport,
			KnowledgeRetrievalTool knowledgeRetrievalTool, HumanHandoffTool humanHandoffTool) {
		super(clientFactory, mcpClientSupport, "知识库问答Skill", "knowledge_qa_system",
				LOCAL_SYSTEM_PROMPT, MCP_TOOLS, knowledgeRetrievalTool, humanHandoffTool);
	}

	@Override
	public String description() {
		return "负责知识库内容问答，绑定本地知识库检索工具，严格依据召回文档原文作答。";
	}

}
