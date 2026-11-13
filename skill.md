# Spring AI 2.0 智能客服 — Skill 架构说明

> 本文档描述项目中 3 个业务 Skill 的设计、绑定工具、路由机制与扩展方式。

---

## 一、Skill 架构总览

```
用户消息
  │
  ▼
AgentOrchestrator（编排器）
  │
  ├── 1. 订单号格式前置校验（OrderNoValidator.findMalformed）
  │
  ├── 2. 意图识别（IntentRouter → LLM entity(Intent.class) 结构化输出）
  │      返回 7 类意图：ORDER_QUERY / LOGISTICS_QUERY / RETURN_REQUEST
  │      / AFTER_SALES_POLICY / KNOWLEDGE_QA / HUMAN_HANDOFF / OUT_OF_SCOPE
  │
  ├── 3. Skill 路由（routeSkill）
  │      ORDER_QUERY / LOGISTICS_QUERY / HUMAN_HANDOFF → 订单咨询 Skill
  │      RETURN_REQUEST / AFTER_SALES_POLICY           → 售后处理 Skill
  │      KNOWLEDGE_QA                                   → 知识库问答 Skill
  │      OUT_OF_SCOPE                                   → 兜底友好拒绝
  │
  └── 4. Skill.answer() → ChatClient 流式调用 → SSE 返回
```

---

## 二、Skill 接口定义

```java
public interface Skill {
    String name();
    String description();
    Flux<String> answer(String question, ChatSession session);
}
```

Spring AI 2.0 GA 无官方独立的 `@Skill` 注解，项目自建 `Skill` 接口 + `AbstractSkill` 基类封装：
- 专属角色 Prompt（优先远程 MCP Prompt 动态拉取，失败使用本地兜底）
- 绑定工具集合（MCP 远程 Tool + Spring AI 本地 Tool）
- 共享 ChatMemory（MessageWindowChatMemory，20 条窗口）
- 流式输出（`.stream().content()` → `Flux<String>`）

---

## 三、Skill1：订单咨询 Skill

| 维度 | 内容 |
|------|------|
| **类** | `OrderConsultSkill` |
| **职责** | 订单信息查询、物流轨迹查询 |
| **远程 MCP Prompt** | `order_consult_system`（动态拉取，本地兜底 Prompt 见类常量） |
| **绑定 MCP 工具** | `query_order_info`（订单基础信息）、`query_logistics`（物流节点 + 预计送达） |
| **绑定本地 Tool** | `transfer_to_human_agent`（人工转接，公共复用） |
| **输出约束** | 严格以 MCP 工具返回结果为准，禁止编造数据；数据为空时友好提示 |
| **参数提取** | 自动从上下文提取订单号（`SO + 11位数字`），缺失时主动询问 |

---

## 四、Skill2：售后处理 Skill

| 维度 | 内容 |
|------|------|
| **类** | `AfterSalesSkill` |
| **职责** | 售后政策咨询、退货申请 |
| **远程 MCP Prompt** | `after_sales_system`（动态拉取，本地兜底 Prompt 见类常量） |
| **绑定 MCP 工具** | `submit_return`（退货提交，内置 MCP Elicitation 二次确认） |
| **绑定本地 Tool** | `search_knowledge_base`（向量知识库检索）、`transfer_to_human_agent`（人工转接） |
| **输出约束** | 售后规则以知识库 / MCP 资源原文为准，不擅自解读政策；退货信息二次确认防误提交 |
| **知识库路由** | 优先调用 `search_knowledge_base` 向量检索；无召回兜底读 MCP Resource SOP 文档 |

---

## 五、Skill3：知识库问答 Skill

| 维度 | 内容 |
|------|------|
| **类** | `KnowledgeQaSkill` |
| **职责** | 知识库内容问答：接收用户问题 → 向量检索 → 依据召回文档原文作答 |
| **远程 MCP Prompt** | `knowledge_qa_system`（动态拉取，本地兜底 Prompt 见类常量） |
| **绑定 MCP 工具** | 无（纯本地工具驱动） |
| **绑定本地 Tool** | `search_knowledge_base`（向量知识库检索）、`transfer_to_human_agent`（人工转接） |
| **输出约束** | 严格以知识库文档原文为准，注明信息来源；无召回时如实告知，不编造 |
| **与售后 Skill 区别** | 售后 Skill 聚焦退货流程 + 售后政策；知识库 Skill 面向通用文档问答（平台规则、功能说明等） |

---

## 六、工具绑定矩阵

| 工具 | 类型 | 来源 | 订单咨询 Skill | 售后处理 Skill | 知识库问答 Skill |
|------|------|------|:---:|:---:|:---:|
| `query_order_info` | MCP Tool | MCP Server 8081 | ✓ | | |
| `query_logistics` | MCP Tool | MCP Server 8081 | ✓ | | |
| `submit_return` | MCP Tool | MCP Server 8081（含 Elicitation） | | ✓ | |
| `search_knowledge_base` | 本地 Tool | AI Client（@Tool） | | ✓ | ✓ |
| `transfer_to_human_agent` | 本地 Tool | AI Client（@Tool，公共复用） | ✓ | ✓ | ✓ |

---

## 七、SkillChatClientFactory 工作机制

每个 Skill 拥有独立的 `ChatClient` 实例（懒加载缓存）：

1. 从 `SyncMcpToolCallbackProvider` 自动发现的全量 MCP 工具中，按 Skill 声明的工具名过滤
2. 通过 `SyncMcpToolCallback.getOriginalToolName()` 匹配原始工具名（去除连接前缀）
3. 同时挂载 Skill 声明的本地 `@Tool` Bean
4. 统一配置 `MessageChatMemoryAdvisor`，以 `sessionId` 作为 `CONVERSATION_ID`
5. `.clone()` 基于自动装配的全局 `ChatClient.Builder` 克隆，不污染默认配置

```java
chatClientBuilder.clone()
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .defaultToolCallbacks(selectedMcpCallbacks)  // 按名过滤后的 MCP 工具
    .defaultTools(localTools)                     // 本地 @Tool Bean
    .build();
```

---

## 八、意图路由器

`IntentRouter` 使用独立的轻量 `ChatClient`（无工具绑定）做意图分类：

- **输入**：用户最新消息
- **输出**：`Intent` 枚举（LLM `entity(Intent.class)` 结构化输出）
- **异常兜底**：分类异常时默认 `ORDER_QUERY`，避免对话死路
- **无关问题**：`OUT_OF_SCOPE` → 编排器返回礼貌拒绝文案

---

## 九、MCP Elicitation 联动链路

退货提交场景的完整链路：

```
售后 Skill 调用 submit_return (MCP Tool)
  │
  ▼
MCP Server ReturnOrderTool 检测 context.elicitEnabled()
  │
  ▼  context.elicit(builder → message, ReturnConfirmation.class)
  │
  ▼  MCP 协议 → AI Client
  │
  ▼  McpElicitationHandler (@McpElicitation(clients="order-mcp"))
  │
  ▼  ElicitationStore.awaitConfirmation(sessionId, message, 120s)
  │
  ├── SSE 推送 elicitation 事件到前端
  │   前端弹出确认窗口
  │
  ├── 用户确认 → POST /api/chat/confirm?confirmed=true
  │   ElicitationStore.complete() → CompletableFuture 完成
  │
  └── 返回 ElicitResult(ACCEPT, {confirmed:true, comment})
      │
      ▼
  MCP Server 校验 confirmed=true → 执行退货提交 → 返回 ReturnResult
```

超时（120s）自动返回 `DECLINE`，退货不提交。

---

## 十、扩展新 Skill 步骤

1. 创建 `XxxSkill extends AbstractSkill`，声明 Skill 名称、远程 Prompt 名、本地兜底 Prompt、MCP 工具名列表、本地 Tool
2. 在 `Intent` 枚举新增对应意图
3. 在 `IntentRouter.CLASSIFIER_SYSTEM_PROMPT` 中补充意图分类描述
4. 在 `AgentOrchestrator.routeSkill()` 的 switch 中映射意图 → 新 Skill
5. 无需修改 `SkillChatClientFactory`、`ChatController` 等基础设施
