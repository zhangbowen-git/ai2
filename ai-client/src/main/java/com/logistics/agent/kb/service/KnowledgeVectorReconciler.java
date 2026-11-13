package com.logistics.agent.kb.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.logistics.agent.kb.entity.KbChunk;
import com.logistics.agent.kb.entity.KbDocument;
import com.logistics.agent.kb.mapper.KbChunkMapper;
import com.logistics.agent.kb.mapper.KbDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 知识库与 Milvus 向量索引一致性修复。
 *
 * <p>MySQL 是知识库文档和启用状态的权威源：</p>
 * <ul>
 *     <li>删除 Milvus 中不存在对应切片或文档未启用的孤立向量；</li>
 *     <li>为已启用但 Milvus 中缺失的切片补写向量；</li>
 *     <li>修正文档切片数量，避免管理页面展示值与实际切片不一致。</li>
 * </ul>
 */
@Service
public class KnowledgeVectorReconciler {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorReconciler.class);

	private static final int VECTOR_ADD_BATCH_SIZE = 10;

	private final VectorStore vectorStore;

	private final KnowledgeVectorIndexService vectorIndexService;

	private final KbChunkMapper chunkMapper;

	private final KbDocumentMapper documentMapper;

	public KnowledgeVectorReconciler(VectorStore vectorStore, KnowledgeVectorIndexService vectorIndexService,
			KbChunkMapper chunkMapper, KbDocumentMapper documentMapper) {
		this.vectorStore = vectorStore;
		this.vectorIndexService = vectorIndexService;
		this.chunkMapper = chunkMapper;
		this.documentMapper = documentMapper;
	}

	/**
	 * 应用启动完成后自动校验一次，修复历史版本留下的孤立向量。
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void reconcileOnStartup() {
		try {
			ReconciliationResult result = reconcile();
			log.info("知识库向量一致性校验完成 result={}", result);
		}
		catch (Exception ex) {
			log.error("知识库向量一致性校验失败，应用继续运行", ex);
		}
	}

	/**
	 * 执行一次全量一致性修复。
	 */
	public synchronized ReconciliationResult reconcile() {
		List<KbDocument> documents = documentMapper.selectList(null);
		List<KbChunk> chunks = chunkMapper.selectList(null);

		Map<Long, KbDocument> documentById = new HashMap<>();
		documents.forEach(document -> documentById.put(document.getId(), document));

		List<KbChunk> orphanChunks = chunks.stream()
				.filter(chunk -> !documentById.containsKey(chunk.getDocId()))
				.toList();
		if (!orphanChunks.isEmpty()) {
			chunkMapper.deleteBatchIds(orphanChunks.stream().map(KbChunk::getId).toList());
			log.warn("已清理缺少父文档的孤立切片 count={}", orphanChunks.size());
			chunks = chunks.stream()
					.filter(chunk -> documentById.containsKey(chunk.getDocId()))
					.toList();
		}

		Map<Long, List<KbChunk>> chunksByDocument = new HashMap<>();
		for (KbChunk chunk : chunks) {
			chunksByDocument.computeIfAbsent(chunk.getDocId(), ignored -> new ArrayList<>()).add(chunk);
		}

		Map<String, KbChunk> activeChunkByVectorId = new HashMap<>();
		for (KbChunk chunk : chunks) {
			KbDocument document = documentById.get(chunk.getDocId());
			if (document != null && Objects.equals(document.getStatus(), KbDocument.STATUS_ACTIVE)) {
				activeChunkByVectorId.put(chunk.getVectorId(), chunk);
			}
		}

		Set<String> milvusVectorIds = vectorIndexService.findAllIds();
		List<String> staleVectorIds = milvusVectorIds.stream()
				.filter(vectorId -> !activeChunkByVectorId.containsKey(vectorId))
				.toList();
		List<String> missingVectorIds = activeChunkByVectorId.keySet().stream()
				.filter(vectorId -> !milvusVectorIds.contains(vectorId))
				.toList();

		vectorIndexService.deleteByIds(staleVectorIds);
		addVectorsInBatches(missingVectorIds, activeChunkByVectorId, documentById);
		int correctedDocuments = correctDocumentChunkCounts(documents, chunksByDocument);

		return new ReconciliationResult(
				milvusVectorIds.size(),
				activeChunkByVectorId.size(),
				staleVectorIds.size(),
				missingVectorIds.size(),
				correctedDocuments,
				orphanChunks.size());
	}

	private void addVectorsInBatches(List<String> vectorIds, Map<String, KbChunk> chunkByVectorId,
			Map<Long, KbDocument> documentById) {
		for (int from = 0; from < vectorIds.size(); from += VECTOR_ADD_BATCH_SIZE) {
			int to = Math.min(from + VECTOR_ADD_BATCH_SIZE, vectorIds.size());
			List<Document> documents = new ArrayList<>(to - from);
			for (String vectorId : vectorIds.subList(from, to)) {
				KbChunk chunk = chunkByVectorId.get(vectorId);
				KbDocument document = documentById.get(chunk.getDocId());
				documents.add(new Document(chunk.getVectorId(), chunk.getContent(), Map.of(
						"docId", String.valueOf(document.getId()),
						"docName", document.getDocName(),
						"category", document.getCategory(),
						"chunkIndex", chunk.getChunkIndex())));
			}
			vectorStore.add(documents);
		}
	}

	private int correctDocumentChunkCounts(List<KbDocument> documents,
			Map<Long, List<KbChunk>> chunksByDocument) {
		int corrected = 0;
		for (KbDocument document : documents) {
			int actualCount = chunksByDocument.getOrDefault(document.getId(), List.of()).size();
			boolean changed = !Objects.equals(document.getChunkCount(), actualCount);
			if (changed) {
				document.setChunkCount(actualCount);
			}
			if (Objects.equals(document.getStatus(), KbDocument.STATUS_ACTIVE) && actualCount == 0) {
				document.setStatus(KbDocument.STATUS_FAILED);
				document.setErrorMsg("切片数据缺失，无法关联向量；请重新上传文档。");
				changed = true;
			}
			if (changed) {
				documentMapper.updateById(document);
				corrected++;
			}
		}
		return corrected;
	}

	/**
	 * 一致性修复结果。
	 */
	public record ReconciliationResult(long milvusVectors, long activeDatabaseVectors,
			int removedOrphanVectors, int restoredMissingVectors, int correctedDocuments,
			int removedOrphanChunks) {
	}

}
