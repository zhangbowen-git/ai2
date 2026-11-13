package com.logistics.agent.kb.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logistics.agent.kb.entity.KbDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理服务。
 *
 * <p>提供文档导入（解析→切片→向量化→入 Milvus，元数据入 MySQL）、
 * 启用 / 禁用 / 删除、分页查询能力。</p>
 */
public interface KnowledgeService {

	/**
	 * 导入文档并完成向量化入库。
	 *
	 * @param file         文档文件（PDF/Word/Markdown/TXT/Excel）
	 * @param category     业务分类（文档隔离），默认 after_sales
	 * @param tags         标签（逗号分隔，可空）
	 * @param chunkSize    分片大小（空使用默认值）
	 * @param chunkOverlap 重叠窗口（空使用默认值）
	 * @param userId       上传人ID（可空）
	 * @return 文档元数据
	 */
	KbDocument upload(MultipartFile file, String category, String tags, Integer chunkSize, Integer chunkOverlap,
			Long userId);

	/**
	 * 分页查询文档。
	 *
	 * @param page     页码（从 1 开始）
	 * @param size     每页数量
	 * @param category 分类过滤（可空）
	 * @param status   状态过滤（可空）
	 * @return 分页结果
	 */
	Page<KbDocument> page(long page, long size, String category, Integer status);

	/**
	 * 查询文档详情。
	 */
	KbDocument getById(Long id);

	/**
	 * 启用文档：基于保留的分片文本重新向量化入库。
	 */
	KbDocument enable(Long id);

	/**
	 * 禁用文档：删除对应向量，元数据与分片保留。
	 */
	KbDocument disable(Long id);

	/**
	 * 删除文档：同步清理向量、分片与元数据。
	 */
	void delete(Long id);

}
