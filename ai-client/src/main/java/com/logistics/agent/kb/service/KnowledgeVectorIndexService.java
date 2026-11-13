package com.logistics.agent.kb.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Milvus 主键查询与清理。
 *
 * <p>删除向量直接使用 Milvus 原生表达式，避免 VectorStore.delete 再调用
 * EmbeddingModel 做维度探测，从而让索引清理不依赖外部模型网络。</p>
 */
@Service
public class KnowledgeVectorIndexService {

	private static final long QUERY_PAGE_SIZE = 1000L;

	private static final int DELETE_BATCH_SIZE = 500;

	private final MilvusServiceClient milvusClient;

	private final String databaseName;

	private final String collectionName;

	public KnowledgeVectorIndexService(MilvusServiceClient milvusClient,
			@Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
			@Value("${spring.ai.vectorstore.milvus.collection-name:knowledge_vectors}") String collectionName) {
		this.milvusClient = milvusClient;
		this.databaseName = databaseName;
		this.collectionName = collectionName;
	}

	/**
	 * 查询 collection 中的全部向量主键。
	 */
	public Set<String> findAllIds() {
		Set<String> vectorIds = new HashSet<>();
		long offset = 0L;
		while (true) {
			QueryParam queryParam = QueryParam.newBuilder()
					.withDatabaseName(databaseName)
					.withCollectionName(collectionName)
					.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
					.withOutFields(List.of(MilvusVectorStore.DOC_ID_FIELD_NAME))
					.withExpr(MilvusVectorStore.DOC_ID_FIELD_NAME + " != ''")
					.withOffset(offset)
					.withLimit(QUERY_PAGE_SIZE)
					.build();
			R<QueryResults> response = milvusClient.query(queryParam);
			if (response.getStatus() == null || response.getStatus() != 0 || response.getData() == null) {
				throw new IllegalStateException("查询 Milvus 向量主键失败: " + response.getMessage());
			}

			List<QueryResultsWrapper.RowRecord> rows =
					new QueryResultsWrapper(response.getData()).getRowRecords();
			for (QueryResultsWrapper.RowRecord row : rows) {
				Object vectorId = row.get(MilvusVectorStore.DOC_ID_FIELD_NAME);
				if (vectorId != null) {
					vectorIds.add(vectorId.toString());
				}
			}
			if (rows.size() < QUERY_PAGE_SIZE) {
				break;
			}
			offset += rows.size();
		}
		return vectorIds;
	}

	/**
	 * 按主键删除向量，失败时抛出异常。
	 */
	public void deleteByIds(List<String> vectorIds) {
		List<String> uniqueIds = vectorIds.stream()
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();
		for (int from = 0; from < uniqueIds.size(); from += DELETE_BATCH_SIZE) {
			int to = Math.min(from + DELETE_BATCH_SIZE, uniqueIds.size());
			deleteBatch(uniqueIds.subList(from, to));
		}
	}

	private void deleteBatch(List<String> vectorIds) {
		String expression = MilvusVectorStore.DOC_ID_FIELD_NAME + " in ["
				+ vectorIds.stream()
						.map(this::quote)
						.collect(Collectors.joining(","))
				+ "]";
		DeleteParam deleteParam = DeleteParam.newBuilder()
				.withDatabaseName(databaseName)
				.withCollectionName(collectionName)
				.withExpr(expression)
				.build();
		R<MutationResult> response = milvusClient.delete(deleteParam);
		if (response.getStatus() == null || response.getStatus() != 0) {
			throw new IllegalStateException("删除 Milvus 向量失败: " + response.getMessage());
		}
	}

	private String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

}
