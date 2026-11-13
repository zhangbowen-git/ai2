package com.logistics.mcp.model;

/**
 * MCP Elicitation 人机交互确认结果结构。
 *
 * <p>MCP Server 在执行退货提交前，通过 Elicitation 弹窗向用户二次确认；
 * 用户确认后客户端需回填本结构：</p>
 *
 * @param confirmed 用户是否确认提交退货申请
 * @param comment   用户补充说明（可空）
 */
public record ReturnConfirmation(Boolean confirmed, String comment) {
}
