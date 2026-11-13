package com.logistics.agent.kb.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.logistics.agent.common.exception.BizException;
import com.logistics.agent.kb.entity.KbChunk;
import com.logistics.agent.kb.entity.KbDocument;
import com.logistics.agent.kb.mapper.KbChunkMapper;
import com.logistics.agent.kb.mapper.KbDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识库向量检索服务。
 *
 * <p>根据用户问题进行相似度检索召回相关片段，并以 MySQL 中的切片和文档状态
 * 作为权威数据源回查。Milvus 中历史遗留、文档已删除或文档未启用的向量不会
 * 返回给调用方，并会在查询时做 best-effort 清理。</p>
 */
@Service
public class KnowledgeQueryService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeQueryService.class);

	private static final int MAX_OVER_FETCH = 100;

	private final VectorStore vectorStore;

	private final KnowledgeVectorIndexService vectorIndexService;

	private final KbChunkMapper chunkMapper;

	private final KbDocumentMapper documentMapper;

	private final int defaultTopK;

	private final double similarityThreshold;

	public KnowledgeQueryService(VectorStore vectorStore, KnowledgeVectorIndexService vectorIndexService,
			KbChunkMapper chunkMapper,
			KbDocumentMapper documentMapper,
			@Value("${agent.kb.default-top-k:4}") int defaultTopK,
			@Value("${agent.kb.similarity-threshold:0.5}") double similarityThreshold) {
		this.vectorStore = vectorStore;
		this.vectorIndexService = vectorIndexService;
		this.chunkMapper = chunkMapper;
		this.documentMapper = documentMapper;
		this.defaultTopK = defaultTopK;
		this.similarityThreshold = similarityThreshold;
	}

	/**
	 * 相似度检索。
	 *
	 * @param question 用户问题
	 * @param category 限定分类（空则不限制）
	 * @param topK     召回数量，空使用默认值
	 * @return 召回片段列表
	 * @throws BizException 向量检索异常（友好文案）
	 */
	public List<RetrievedSnippet> search(String question, String category, Integer topK) {
		if (!StringUtils.hasText(question)) {
			throw new BizException("检索问题不能为空。");
		}
		int limit = (topK != null && topK > 0) ? topK : defaultTopK;
		int searchLimit = Math.min(Math.max(limit * 4, limit), MAX_OVER_FETCH);

		SearchRequest searchRequest = SearchRequest.builder()
				.query(question)
				.topK(searchLimit)
				.similarityThreshold(similarityThreshold)
				.build();

		List<Document> documents;
		try {
			documents = vectorStore.similaritySearch(searchRequest);
		}
		catch (Exception ex) {
			log.error("向量检索异常 question={}", question, ex);
			throw new BizException("知识库检索服务暂时不可用，请稍后再试。", ex);
		}

		if (documents == null || documents.isEmpty()) {
			log.info("向量检索无召回 question={}", question);
			return List.of();
		}

		List<String> vectorIds = documents.stream()
				.map(Document::getId)
				.toList();
		List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
				.in(KbChunk::getVectorId, vectorIds));
		Map<String, KbChunk> chunkByVectorId = new HashMap<>();
		for (KbChunk chunk : chunks) {
			chunkByVectorId.put(chunk.getVectorId(), chunk);
		}

		Set<Long> documentIds = chunks.stream()
				.map(KbChunk::getDocId)
				.collect(java.util.stream.Collectors.toSet());
		Map<Long, KbDocument> activeDocumentById = new HashMap<>();
		if (!documentIds.isEmpty()) {
			documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
					.in(KbDocument::getId, documentIds)
					.eq(KbDocument::getStatus, KbDocument.STATUS_ACTIVE))
					.forEach(document -> activeDocumentById.put(document.getId(), document));
		}

		List<RetrievedSnippet> snippets = new ArrayList<>();
		List<String> staleVectorIds = new ArrayList<>();
		for (Document document : documents) {
			KbChunk chunk = chunkByVectorId.get(document.getId());
			if (chunk == null) {
				staleVectorIds.add(document.getId());
				continue;
			}
			KbDocument currentDocument = activeDocumentById.get(chunk.getDocId());
			if (currentDocument == null) {
				staleVectorIds.add(document.getId());
				continue;
			}
			if (!categoryMatched(currentDocument, category)) {
				continue;
			}
			snippets.add(toSnippet(currentDocument, chunk));
			if (snippets.size() >= limit) {
				break;
			}
		}

		if (!staleVectorIds.isEmpty()) {
			log.warn("向量检索发现孤立向量，已从结果排除 vectorIds={}", staleVectorIds);
			deleteStaleVectors(staleVectorIds);
		}
		return snippets;
	}

	/**
	 * 分类过滤使用 MySQL 中当前文档元数据，避免使用 Milvus 中的历史 metadata。
	 */
	private boolean categoryMatched(KbDocument document, String category) {
		if (!StringUtils.hasText(category)) {
			return true;
		}
		return Objects.equals(category, document.getCategory());
	}

	private RetrievedSnippet toSnippet(KbDocument document, KbChunk chunk) {
		return new RetrievedSnippet(
				String.valueOf(document.getId()),
				document.getDocName(),
				document.getCategory(),
				chunk.getVectorId(),
				chunk.getContent());
	}

	/**
	 * 对本次检索已确认失效的向量做 best-effort 清理，不影响正常查询返回。
	 */
	private void deleteStaleVectors(List<String> vectorIds) {
		try {
			vectorIndexService.deleteByIds(vectorIds);
		}
		catch (Exception ex) {
			log.warn("清理孤立向量失败，等待后续一致性修复 vectorIds={}", vectorIds, ex);
		}
	}

}
