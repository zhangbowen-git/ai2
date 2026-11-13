package com.logistics.agent.skill;

import java.util.List;

import com.logistics.agent.mcp.McpClientSupport;
import com.logistics.agent.tool.HumanHandoffTool;
import com.logistics.agent.tool.KnowledgeRetrievalTool;
import org.springframework.stereotype.Component;

/**
 * Skill2：售后处理 Skill。
 *
 * <p>负责售后政策咨询、退货申请、知识库问答业务：</p>
 * <ul>
 *     <li>绑定 MCP 工具：submit_return（退货提交，内置 Elicitation 二次确认）；</li>
 *     <li>绑定本地 Tool：search_knowledge_base（知识库向量检索）；</li>
 *     <li>复用本地公共 Tool：transfer_to_human_agent（人工客服转接）；</li>
 *     <li>售后规则以知识库 / MCP 资源原文为准，不擅自解读政策。</li>
 * </ul>
 */
@Component
public class AfterSalesSkill extends AbstractSkill {

	/**
	 * 本 Skill 绑定的 MCP 远程工具。
	 */
	private static final List<String> MCP_TOOLS = List.of("submit_return");

	/**
	 * 本地兜底系统 Prompt（远程 Prompt 不可用时使用）。
	 */
	private static final String LOCAL_SYSTEM_PROMPT = """
			你是「智能客服」的售后处理专家，负责售后政策咨询、退货申请与知识库问答。
			工作规范：
			1. 解答退换货规则、售后时限、保修政策前，必须先调用 search_knowledge_base 工具检索知识库，并严格依据召回的文档原文作答，不擅自解读或补充政策；
			2. 知识库无相关内容时，如实告知用户暂无相关政策信息，并可建议转接人工客服；
			3. 用户申请退货时，自动复用会话中的订单号（SO 开头共13位），缺少订单号先收集；调用 submit_return 提交后，会弹出二次确认窗口，用户确认后系统才会真正提交，请勿跳过确认流程或声称已提交而实际未确认；
			4. 用户取消退货时，明确告知本次申请未提交；
			5. 用户明确要求转人工时，调用 transfer_to_human_agent 工具；
			6. 语气严谨、专业、有同理心，不回答订单与物流查询等超出本职责范围的问题。
			""";

	public AfterSalesSkill(SkillChatClientFactory clientFactory, McpClientSupport mcpClientSupport,
			KnowledgeRetrievalTool knowledgeRetrievalTool, HumanHandoffTool humanHandoffTool) {
		super(clientFactory, mcpClientSupport, "售后处理Skill", "after_sales_system",
				LOCAL_SYSTEM_PROMPT, MCP_TOOLS, knowledgeRetrievalTool, humanHandoffTool);
	}

	@Override
	public String description() {
		return "负责售后政策咨询、知识库问答与退货申请（含二次确认），绑定 MCP 退货工具与本地知识库检索工具。";
	}

}
