package com.logistics.agent.kb.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 知识库文档元数据实体（MySQL 持久化）。
 *
 * <p>记录文档名称、上传时间、标签、分类、状态以及与 chunk 的索引映射关系。</p>
 */
@TableName("kb_document")
public class KbDocument {

	/** 状态：处理中。 */
	public static final int STATUS_PROCESSING = 0;

	/** 状态：已启用（向量在库，可检索）。 */
	public static final int STATUS_ACTIVE = 1;

	/** 状态：已禁用（向量已删除，元数据与 chunk 文本保留，可重新启用）。 */
	public static final int STATUS_DISABLED = 2;

	/** 状态：处理失败。 */
	public static final int STATUS_FAILED = 3;

	@TableId(type = IdType.ASSIGN_ID)
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

	/** 文档名称（原始文件名）。 */
	private String docName;

	/** 业务分类（不同业务文档隔离），如 after_sales / order_policy。 */
	private String category;

	/** 标签，逗号分隔。 */
	private String tags;

	/** 文件类型（扩展名）：pdf/docx/md/txt/xlsx 等。 */
	private String fileType;

	/** 文件大小（字节）。 */
	private Long fileSize;

	/** 切片大小（字符数）。 */
	private Integer chunkSize;

	/** 切片重叠窗口（字符数）。 */
	private Integer chunkOverlap;

	/** 版本号（重新导入同文档时递增）。 */
	private Integer version;

	/** 状态：0 处理中 / 1 已启用 / 2 已禁用 / 3 处理失败。 */
	private Integer status;

	/** 切片数量。 */
	private Integer chunkCount;

	/** 处理失败原因（友好文案）。 */
	private String errorMsg;

	/** 上传人ID。 */
	private Long createBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getDocName() {
		return docName;
	}

	public void setDocName(String docName) {
		this.docName = docName;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags;
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public Integer getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(Integer chunkSize) {
		this.chunkSize = chunkSize;
	}

	public Integer getChunkOverlap() {
		return chunkOverlap;
	}

	public void setChunkOverlap(Integer chunkOverlap) {
		this.chunkOverlap = chunkOverlap;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public Integer getChunkCount() {
		return chunkCount;
	}

	public void setChunkCount(Integer chunkCount) {
		this.chunkCount = chunkCount;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public Long getCreateBy() {
		return createBy;
	}

	public void setCreateBy(Long createBy) {
		this.createBy = createBy;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

}
