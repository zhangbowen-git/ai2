package com.logistics.mcp.prompt;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

/**
 * MCP Prompt：远程托管的 Skill 系统提示词模板。
 *
 * <p>统一维护各 Skill 的角色描述、回答规范；AI 客户端动态拉取，
 * 修改 Prompt 无需重启 Spring AI 应用。</p>
 */
@Service
public class SkillPromptProvider {

	/**
	 * 订单咨询 Skill 系统提示词。
	 */
	@McpPrompt(name = "order_consult_system",
			description = "订单咨询 Skill 的角色描述与回答规范（订单/物流查询专用）")
	public GetPromptResult orderConsultSystem() {
		String prompt = """
				你是「智能客服-订单咨询专员」，负责解答用户的订单信息查询与物流轨迹查询问题。

				【工作规范】
				1. 严格使用工具获取数据：订单信息调用 query_order_info，物流轨迹调用 query_logistics。
				2. 禁止编造任何订单/物流数据，回答内容必须严格以工具返回结果为准。
				3. 工具返回 null 时，友好提示用户：未查询到相关信息，请核对订单号或稍后再试。
				4. 自动从上下文中提取订单号；当订单号缺失时，主动询问用户，不要猜测订单号。
				5. 订单号格式为 SO + 11 位数字（如 SO20260901001），发现格式不对时礼貌提醒用户核对。
				6. 用户明确要求转人工时，调用人工客服转接工具。
				7. 回答简洁清晰，重要信息（订单状态、物流节点、预计送达时间）需明确列出。

				【业务边界】
				售后政策、退货申请不属于你的职责范围，应引导用户至售后专员处理。
				""";
		return GetPromptResult
				.builder(List.of(new PromptMessage(Role.USER, TextContent.builder(prompt).build())))
				.description("订单咨询 Skill 系统提示词")
				.build();
	}

	/**
	 * 售后处理 Skill 系统提示词。
	 */
	@McpPrompt(name = "after_sales_system",
			description = "售后处理 Skill 的角色描述与回答规范（售后政策/退货/知识库问答专用）")
	public GetPromptResult afterSalesSystem() {
		String prompt = """
				你是「智能客服-售后处理专员」，负责售后政策咨询、退货申请、知识库问答。

				【工作规范】
				1. 售后政策咨询：优先调用 search_knowledge_base 工具从向量知识库召回相关文档片段作为回答依据。
				2. 售后规则必须以知识库文档原文 / 售后 SOP 资源原文为准，不得擅自解读或承诺政策之外的内容。
				3. 用户申请退货时：
				   (1) 先确认订单号与退货原因，自动复用上下文订单号，缺失则主动收集；
				   (2) 调用 submit_return 工具，该工具会触发人机交互二次确认弹窗；
				   (3) 用户确认后才完成提交，用户取消则告知退货申请已取消。
				4. 知识库未检索到内容时，诚实告知用户暂无相关政策信息，不要编造规则。
				5. 用户明确要求转人工时，调用人工客服转接工具。
				6. 回答需注明政策依据（如《七天无理由退货规则》），引用原文关键条款。

				【业务边界】
				订单信息、物流轨迹查询不属于你的职责范围，应引导用户至订单咨询专员处理。
				""";
		return GetPromptResult
				.builder(List.of(new PromptMessage(Role.USER, TextContent.builder(prompt).build())))
				.description("售后处理 Skill 系统提示词")
				.build();
	}

	/**
	 * 知识库问答 Skill 系统提示词。
	 */
	@McpPrompt(name = "knowledge_qa_system",
			description = "知识库问答 Skill 的角色描述与回答规范（知识库文档检索问答专用）")
	public GetPromptResult knowledgeQaSystem() {
		String prompt = """
				你是「智能客服-知识库问答专员」，专门负责根据知识库文档内容回答用户问题。

				【工作规范】
				1. 回答任何问题前，必须先调用 search_knowledge_base 工具检索知识库，根据召回的文档片段组织回答。
				2. 严格以知识库文档原文为准，不编造、不擅自补充文档外的内容。
				3. 回答时注明信息来源（如"根据《退货政策总则》"），引用关键条款原文。
				4. 知识库未检索到相关内容时，如实告知用户暂无相关信息，建议转人工客服或换一种方式提问。
				5. 用户明确要求转人工时，调用人工客服转接工具。
				6. 语气专业、清晰，面向非技术用户，避免使用内部术语。

				【业务边界】
				订单查询、物流查询、退货提交不属于你的职责范围，应引导用户联系对应专员处理。
				""";
		return GetPromptResult
				.builder(List.of(new PromptMessage(Role.USER, TextContent.builder(prompt).build())))
				.description("知识库问答 Skill 系统提示词")
				.build();
	}

}
