package com.logistics.agent.kb.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 知识库文档切片实体。
 *
 * <p>保存切片原文与向量ID映射：</p>
 * <ul>
 *     <li>删除文档时，按 vectorId 清理 Milvus 中对应向量；</li>
 *     <li>禁用后重新启用时，直接基于保留的 content 重新向量化，无需重新上传文件。</li>
 * </ul>
 */
@TableName("kb_chunk")
public class KbChunk {


	@TableId(type = IdType.ASSIGN_ID)
	@JsonSerialize(using = ToStringSerializer.class)
	private Long id;

	/** 所属文档ID。 */
	private Long docId;

	/** 分片序号（从 0 开始）。 */
	private Integer chunkIndex;

	/** Milvus 中对应文档的ID（UUID）。 */
	private String vectorId;

	/** 分片文本原文。 */
	private String content;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getDocId() {
		return docId;
	}

	public void setDocId(Long docId) {
		this.docId = docId;
	}

	public Integer getChunkIndex() {
		return chunkIndex;
	}

	public void setChunkIndex(Integer chunkIndex) {
		this.chunkIndex = chunkIndex;
	}

	public String getVectorId() {
		return vectorId;
	}

	public void setVectorId(String vectorId) {
		this.vectorId = vectorId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
