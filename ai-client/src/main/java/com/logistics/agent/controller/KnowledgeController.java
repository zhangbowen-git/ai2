package com.logistics.agent.controller;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logistics.agent.common.api.R;
import com.logistics.agent.kb.entity.KbDocument;
import com.logistics.agent.kb.service.KnowledgeQueryService;
import com.logistics.agent.kb.service.KnowledgeService;
import com.logistics.agent.kb.service.KnowledgeVectorReconciler;
import com.logistics.agent.kb.service.RetrievedSnippet;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理接口。
 *
 * <p>文档上传（批量）、分页查询、启用 / 禁用、删除；
 * 分类（category）用于不同业务文档隔离。</p>
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

	private final KnowledgeService knowledgeService;

	private final KnowledgeQueryService knowledgeQueryService;

	private final KnowledgeVectorReconciler vectorReconciler;

	public KnowledgeController(KnowledgeService knowledgeService, KnowledgeQueryService knowledgeQueryService,
			KnowledgeVectorReconciler vectorReconciler) {
		this.knowledgeService = knowledgeService;
		this.knowledgeQueryService = knowledgeQueryService;
		this.vectorReconciler = vectorReconciler;
	}

	/**
	 * 上传文档（解析 → 切片 → 向量化 → 入 Milvus，元数据入 MySQL）。
	 *
	 * @param file         文档文件
	 * @param category     业务分类，默认 after_sales
	 * @param tags         标签（逗号分隔）
	 * @param chunkSize    分片大小
	 * @param chunkOverlap 重叠窗口
	 * @return 文档元数据
	 */
	@PostMapping("/upload")
	public R<KbDocument> upload(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "category", required = false) String category,
			@RequestParam(value = "tags", required = false) String tags,
			@RequestParam(value = "chunkSize", required = false) Integer chunkSize,
			@RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap) {
		KbDocument document = knowledgeService.upload(file, category, tags, chunkSize, chunkOverlap, null);
		return R.ok(document);
	}

	/**
	 * 批量上传文档（同分类 / 同切片策略）。
	 *
	 * @return 全部文档元数据
	 */
	@PostMapping("/upload-batch")
	public R<java.util.List<KbDocument>> uploadBatch(@RequestParam("files") MultipartFile[] files,
			@RequestParam(value = "category", required = false) String category,
			@RequestParam(value = "tags", required = false) String tags,
			@RequestParam(value = "chunkSize", required = false) Integer chunkSize,
			@RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap) {

		java.util.List<KbDocument> documents = new java.util.ArrayList<>();
		if (files != null) {
			for (MultipartFile file : files) {
				documents.add(knowledgeService.upload(file, category, tags, chunkSize, chunkOverlap, null));
			}
		}
		return R.ok(documents);
	}

	/**
	 * 分页查询文档。
	 */
	@GetMapping("/page")
	public R<Page<KbDocument>> page(@RequestParam(value = "page", defaultValue = "1") long page,
			@RequestParam(value = "size", defaultValue = "10") long size,
			@RequestParam(value = "category", required = false) String category,
			@RequestParam(value = "status", required = false) Integer status) {
		return R.ok(knowledgeService.page(page, size, category, status));
	}

	/**
	 * 文档详情。
	 */
	@GetMapping("/{id}")
	public R<KbDocument> getById(@PathVariable("id") Long id) {
		return R.ok(knowledgeService.getById(id));
	}

	/**
	 * 启用文档（重新向量化入库）。
	 */
	@PostMapping("/{id}/enable")
	public R<KbDocument> enable(@PathVariable("id") Long id) {
		return R.ok(knowledgeService.enable(id));
	}

	/**
	 * 禁用文档（删除向量，保留元数据）。
	 */
	@PostMapping("/{id}/disable")
	public R<KbDocument> disable(@PathVariable("id") Long id) {
		return R.ok(knowledgeService.disable(id));
	}

	/**
	 * 删除文档（同步清理向量）。
	 */
	@DeleteMapping("/{id}")
	public R<Void> delete(@PathVariable("id") Long id) {
		knowledgeService.delete(id);
		return R.ok();
	}

	/**
	 * 向量相似度检索（直接查询 Milvus）。
	 *
	 * @param query    检索问题
	 * @param category 分类过滤（可空）
	 * @param topK     召回数量（可空，默认 4）
	 * @return 召回片段列表
	 */
	@GetMapping("/search")
	public R<List<RetrievedSnippet>> search(@RequestParam("query") String query,
			@RequestParam(value = "category", required = false) String category,
			@RequestParam(value = "topK", required = false) Integer topK) {
		return R.ok(knowledgeQueryService.search(query, category, topK));
	}

	/**
	 * 修复 MySQL 与 Milvus 之间的知识库索引不一致。
	 *
	 * <p>删除孤立向量、补回缺失向量并校正文档切片数量。</p>
	 */
	@PostMapping("/vectors/reconcile")
	public R<KnowledgeVectorReconciler.ReconciliationResult> reconcileVectors() {
		return R.ok(vectorReconciler.reconcile());
	}

}
