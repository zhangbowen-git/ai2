package com.logistics.agent.kb.service;

/**
 * 知识库向量检索召回片段。
 *
 * @param docId    来源文档ID
 * @param docName  来源文档名称
 * @param category 文档分类
 * @param vectorId 切片向量ID
 * @param content  片段原文
 */
public record RetrievedSnippet(String docId, String docName, String category, String vectorId, String content) {
}
