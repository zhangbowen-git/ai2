package com.logistics.agent.skill;

import java.util.List;

import com.logistics.agent.mcp.McpClientSupport;
import com.logistics.agent.tool.HumanHandoffTool;
import org.springframework.stereotype.Component;

/**
 * Skill1：订单咨询 Skill。
 *
 * <p>负责订单、物流类查询业务：</p>
 * <ul>
 *     <li>绑定 MCP 工具：query_order_info（订单信息查询）、query_logistics（物流轨迹查询）；</li>
 *     <li>复用本地公共 Tool：transfer_to_human_agent（人工客服转接）；</li>
 *     <li>输出严格以工具返回结果为准，禁止编造数据。</li>
 * </ul>
 */
@Component
public class OrderConsultSkill extends AbstractSkill {

	/**
	 * 本 Skill 绑定的 MCP 远程工具。
	 */
	private static final List<String> MCP_TOOLS = List.of("query_order_info", "query_logistics");

	/**
	 * 本地兜底系统 Prompt（远程 Prompt 不可用时使用）。
	 */
	private static final String LOCAL_SYSTEM_PROMPT = """
			你是「智能客服」的订单咨询专家，只负责订单信息查询与物流轨迹查询业务。
			工作规范：
			1. 回答必须严格基于 query_order_info / query_logistics 工具返回的数据，禁止编造商品、金额、状态或物流节点；
			2. 工具返回为空或订单不存在时，友好提示用户核对订单号，不要臆造信息；
			3. 自动从对话上下文提取订单号（格式：SO 开头共13位，如 SO20260901001），缺少订单号时主动、简明地询问；
			4. 用户明确要求转人工时，调用 transfer_to_human_agent 工具；
			5. 语气专业简洁，不解答售后政策、退货等超出本职责范围的问题。
			""";

	public OrderConsultSkill(SkillChatClientFactory clientFactory, McpClientSupport mcpClientSupport,
			HumanHandoffTool humanHandoffTool) {
		super(clientFactory, mcpClientSupport, "订单咨询Skill", "order_consult_system",
				LOCAL_SYSTEM_PROMPT, MCP_TOOLS, humanHandoffTool);
	}

	@Override
	public String description() {
		return "负责订单信息与物流轨迹查询，绑定 MCP 订单/物流工具与本地人工转接工具。";
	}

}
