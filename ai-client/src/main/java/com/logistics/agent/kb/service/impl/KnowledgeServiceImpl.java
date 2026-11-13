package com.logistics.agent.kb.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logistics.agent.common.exception.BizException;
import com.logistics.agent.common.log.AgentTraceLogger;
import com.logistics.agent.kb.entity.KbChunk;
import com.logistics.agent.kb.entity.KbDocument;
import com.logistics.agent.kb.mapper.KbChunkMapper;
import com.logistics.agent.kb.mapper.KbDocumentMapper;
import com.logistics.agent.kb.parser.DocumentParserService;
import com.logistics.agent.kb.parser.TextCleaner;
import com.logistics.agent.kb.service.KnowledgeService;
import com.logistics.agent.kb.service.KnowledgeVectorIndexService;
import com.logistics.agent.kb.splitter.WindowTextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理服务实现。
 *
 * <p>导入链路：文件解析 → 文本清洗 → 滑动窗口切片 → 调用 Embedding 向量化
 * → 写入 Milvus；文档元数据与分片映射持久化 MySQL。</p>
 *
 * <p>一致性策略（最小可用）：</p>
 * <ul>
 *     <li>向量ID由应用生成（UUID），与 kb_chunk 一一映射；</li>
 *     <li>禁用 = 删除向量但保留分片原文，启用时按原文重新向量化；</li>
 *     <li>删除 = 先清理向量再清理元数据；</li>
 *     <li>导入任一步骤失败做 best-effort 回滚并标记文档失败。</li>
 * </ul>
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

	private final KbDocumentMapper documentMapper;

	private final KbChunkMapper chunkMapper;

	private final VectorStore vectorStore;

	private final KnowledgeVectorIndexService vectorIndexService;

	private final DocumentParserService documentParser;

	private final WindowTextSplitter textSplitter;

	private final AgentTraceLogger traceLogger;

	private final int defaultChunkSize;

	private final int defaultChunkOverlap;

	public KnowledgeServiceImpl(KbDocumentMapper documentMapper, KbChunkMapper chunkMapper, VectorStore vectorStore,
			KnowledgeVectorIndexService vectorIndexService, DocumentParserService documentParser,
			WindowTextSplitter textSplitter, AgentTraceLogger traceLogger,
			@Value("${agent.kb.default-chunk-size:500}") int defaultChunkSize,
			@Value("${agent.kb.default-chunk-overlap:100}") int defaultChunkOverlap) {
		this.documentMapper = documentMapper;
		this.chunkMapper = chunkMapper;
		this.vectorStore = vectorStore;
		this.vectorIndexService = vectorIndexService;
		this.documentParser = documentParser;
		this.textSplitter = textSplitter;
		this.traceLogger = traceLogger;
		this.defaultChunkSize = defaultChunkSize;
		this.defaultChunkOverlap = defaultChunkOverlap;
	}

	@Override
	public KbDocument upload(MultipartFile file, String category, String tags, Integer chunkSize,
			Integer chunkOverlap, Long userId) {
		String docName = file != null && file.getOriginalFilename() != null
				? StringUtils.cleanPath(file.getOriginalFilename()) : "unknown";

		KbDocument document = new KbDocument();
		document.setDocName(docName);
		document.setCategory(StringUtils.hasText(category) ? category.trim() : "after_sales");
		document.setTags(tags);
		document.setFileType(getExtension(docName));
		document.setFileSize(file != null ? file.getSize() : 0L);
		document.setChunkSize((chunkSize != null && chunkSize > 0) ? chunkSize : defaultChunkSize);
		document.setChunkOverlap((chunkOverlap != null && chunkOverlap >= 0) ? chunkOverlap : defaultChunkOverlap);
		// 版本管理：同名称 + 同分类文档重新导入时，版本号在历史最大版本上递增
		document.setVersion(nextVersion(docName, document.getCategory()));
		document.setStatus(KbDocument.STATUS_PROCESSING);
		document.setChunkCount(0);
		document.setCreateBy(userId);
		document.setCreateTime(LocalDateTime.now());
		document.setUpdateTime(LocalDateTime.now());
		documentMapper.insert(document);
		traceLogger.kbImport(document.getId(), docName, "UPLOAD_START",
				"category=" + document.getCategory() + ", chunkSize=" + document.getChunkSize());

		try {
			// 1. 解析
			String rawText = documentParser.parse(file);
			// 2. 清洗
			String cleanedText = TextCleaner.clean(rawText);
			// 3. 切片
			List<String> chunks = textSplitter.split(cleanedText, document.getChunkSize(),
					document.getChunkOverlap());
			if (chunks.isEmpty()) {
				throw new BizException("文档切片结果为空，请检查文件内容。");
			}

			// 4. 构造分片记录与向量文档
			List<KbChunk> chunkEntities = new ArrayList<>(chunks.size());
			List<Document> vectorDocuments = new ArrayList<>(chunks.size());
			for (int i = 0; i < chunks.size(); i++) {
				String vectorId = UUID.randomUUID().toString();

				KbChunk chunkEntity = new KbChunk();
				chunkEntity.setDocId(document.getId());
				chunkEntity.setChunkIndex(i);
				chunkEntity.setVectorId(vectorId);
				chunkEntity.setContent(chunks.get(i));
				chunkEntity.setCreateTime(LocalDateTime.now());
				chunkEntities.add(chunkEntity);

				Map<String, Object> metadata = Map.of(
						"docId", String.valueOf(document.getId()),
						"docName", document.getDocName(),
						"category", document.getCategory(),
						"chunkIndex", i);
				vectorDocuments.add(new Document(vectorId, chunks.get(i), metadata));
			}

			// 5. 元数据分片落库
			chunkEntities.forEach(chunkMapper::insert);

			// 6. 向量化并写入 Milvus（Embedding 由 VectorStore 内部调用）
			addInBatches(vectorDocuments);
			traceLogger.vectorPersist(document.getId(), vectorDocuments.size());

			// 7. 标记启用
			document.setStatus(KbDocument.STATUS_ACTIVE);
			document.setChunkCount(chunks.size());
			document.setErrorMsg(null);
			document.setUpdateTime(LocalDateTime.now());
			documentMapper.updateById(document);
			traceLogger.kbImport(document.getId(), docName, "UPLOAD_SUCCESS",
					"chunks=" + chunks.size());
			log.info("知识库文档导入成功 docId={}, docName={}, chunks={}", document.getId(), docName, chunks.size());
			return document;
		}
		catch (Exception ex) {
			log.error("知识库文档导入失败 docId={}, docName={}", document.getId(), docName, ex);
			// best-effort 清理半成品
			bestEffortCleanup(document.getId());

			String friendlyMessage = ex instanceof BizException bizEx ? bizEx.getMessage()
					: "知识库文档导入失败，请稍后重试。";
			document.setStatus(KbDocument.STATUS_FAILED);
			document.setErrorMsg(StringUtils.truncate(friendlyMessage, 480));
			document.setUpdateTime(LocalDateTime.now());
			documentMapper.updateById(document);
			traceLogger.kbImport(document.getId(), docName, "UPLOAD_FAILED", friendlyMessage);

			if (ex instanceof BizException bizEx) {
				throw bizEx;
			}
			throw new BizException(friendlyMessage, ex);
		}
	}

	@Override
	public Page<KbDocument> page(long page, long size, String category, Integer status) {
		Page<KbDocument> pageRequest = new Page<>(page, size);
		LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
				.eq(StringUtils.hasText(category), KbDocument::getCategory, category)
				.eq(status != null, KbDocument::getStatus, status)
				.orderByDesc(KbDocument::getCreateTime);
		return documentMapper.selectPage(pageRequest, wrapper);
	}

	@Override
	public KbDocument getById(Long id) {
		KbDocument document = documentMapper.selectById(id);
		if (document == null) {
			throw new BizException("文档不存在或已被删除。");
		}
		return document;
	}

	@Override
	public KbDocument enable(Long id) {
		KbDocument document = getById(id);
		if (document.getStatus() == KbDocument.STATUS_ACTIVE) {
			return document;
		}
		List<KbChunk> chunks = listChunks(id);
		if (chunks.isEmpty()) {
			throw new BizException("文档分片数据缺失，无法重新启用，请删除后重新上传文档。");
		}

		List<Document> vectorDocuments = chunks.stream()
				.map(chunk -> new Document(chunk.getVectorId(), chunk.getContent(), Map.of(
						"docId", String.valueOf(document.getId()),
						"docName", document.getDocName(),
						"category", document.getCategory(),
						"chunkIndex", chunk.getChunkIndex())))
				.toList();
		try {
			// 启用前先清理同 ID 的历史残留，确保向量内容与当前 MySQL 切片完全一致
			deleteVectorsStrict(chunks);
			addInBatches(vectorDocuments);
		}
		catch (Exception ex) {
			log.error("重新启用文档失败 docId={}", id, ex);
			throw new BizException("文档启用失败，向量服务暂时不可用，请稍后再试。", ex);
		}

		document.setStatus(KbDocument.STATUS_ACTIVE);
		document.setChunkCount(chunks.size());
		document.setUpdateTime(LocalDateTime.now());
		documentMapper.updateById(document);
		traceLogger.kbImport(id, document.getDocName(), "ENABLE", "chunks=" + chunks.size());
		return document;
	}

	@Override
	public KbDocument disable(Long id) {
		KbDocument document = getById(id);
		if (document.getStatus() != KbDocument.STATUS_ACTIVE) {
			return document;
		}
		List<KbChunk> chunks = listChunks(id);
		// 向量删除成功后才更新文档状态，避免页面显示已禁用但 Milvus 仍有残留向量
		deleteVectorsStrict(chunks);

		document.setStatus(KbDocument.STATUS_DISABLED);
		document.setUpdateTime(LocalDateTime.now());
		documentMapper.updateById(document);
		traceLogger.kbImport(id, document.getDocName(), "DISABLE", "vectors removed=" + chunks.size());
		return document;
	}

	@Override
	public void delete(Long id) {
		KbDocument document = getById(id);
		List<KbChunk> chunks = listChunks(id);

		// 无论文档状态如何都按 vectorId 清理，避免历史异常留下孤立向量
		deleteVectorsStrict(chunks);
		chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, id));
		documentMapper.deleteById(id);
		traceLogger.kbImport(id, document.getDocName(), "DELETE", "document and vectors removed");
	}

	/**
	 * 查询文档分片（按序号升序）。
	 */
	private List<KbChunk> listChunks(Long docId) {
		return chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
				.eq(KbChunk::getDocId, docId)
				.orderByAsc(KbChunk::getChunkIndex));
	}

	/**
	 * 按分片 vectorId 严格删除 Milvus 向量。
	 *
	 * <p>失败时抛出业务异常，调用方不得继续修改 MySQL 状态，避免产生新的
	 * “页面状态已变化但向量仍存在”的数据不一致。</p>
	 */
	private void deleteVectorsStrict(List<KbChunk> chunks) {
		List<String> vectorIds = chunks.stream().map(KbChunk::getVectorId).toList();
		if (vectorIds.isEmpty()) {
			return;
		}
		try {
			vectorIndexService.deleteByIds(vectorIds);
		}
		catch (Exception ex) {
			log.error("删除向量失败 vectorIds={}", vectorIds, ex);
			throw new BizException("向量数据清理失败，文档状态未变更，请稍后重试。", ex);
		}
	}

	/**
	 * 导入失败时清理半成品向量与分片。
	 */
	private void bestEffortCleanup(Long docId) {
		try {
			List<KbChunk> chunks = listChunks(docId);
			deleteVectorsBestEffort(chunks);
			chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId));
		}
		catch (Exception ex) {
			log.warn("导入失败回滚清理异常 docId={}", docId, ex);
		}
	}

	/**
	 * 导入失败时清理半成品向量，不能覆盖原始导入异常。
	 */
	private void deleteVectorsBestEffort(List<KbChunk> chunks) {
		List<String> vectorIds = chunks.stream().map(KbChunk::getVectorId).toList();
		if (vectorIds.isEmpty()) {
			return;
		}
		try {
			vectorIndexService.deleteByIds(vectorIds);
		}
		catch (Exception ex) {
			log.warn("导入失败回滚时清理向量失败 vectorIds={}", vectorIds, ex);
		}
	}

	/**
	 * 获取扩展名（小写）。
	 */
	private String getExtension(String filename) {
		int dotIndex = filename.lastIndexOf('.');
		return dotIndex >= 0 && dotIndex < filename.length() - 1
				? filename.substring(dotIndex + 1).toLowerCase() : "";
	}

	/**
	 * 计算下一版本号：同文档名 + 同分类历史最大版本 + 1；无历史则为 1。
	 */
	private int nextVersion(String docName, String category) {
		List<KbDocument> latest = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
				.select(KbDocument::getVersion)
				.eq(KbDocument::getDocName, docName)
				.eq(KbDocument::getCategory, category)
				.orderByDesc(KbDocument::getVersion)
				.last("LIMIT 1"));
		if (latest.isEmpty() || latest.get(0).getVersion() == null) {
			return 1;
		}
		return latest.get(0).getVersion() + 1;
	}

	/**
	 * 分批写入向量库。DashScope Embedding 接口限制单次批量不超过 10 条，
	 * 大文档切片较多时必须分批调用，避免触发 batch size 校验错误。
	 */
	private void addInBatches(List<Document> documents) {
		int batchSize = 10;
		for (int from = 0; from < documents.size(); from += batchSize) {
			int to = Math.min(from + batchSize, documents.size());
			vectorStore.add(documents.subList(from, to));
		}
	}

}
