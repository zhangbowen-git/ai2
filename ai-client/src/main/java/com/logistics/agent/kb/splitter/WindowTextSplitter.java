package com.logistics.agent.kb.splitter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 滑动窗口文本切片器。
 *
 * <p>支持自定义分片大小与重叠窗口：</p>
 * <ul>
 *     <li>chunkSize：单个分片最大字符数；</li>
 *     <li>overlap：相邻分片重叠字符数，保证跨片段语义连续；</li>
 *     <li>滑动步长 = chunkSize - overlap。</li>
 * </ul>
 *
 * <p>按字符切分，对中英文混合文本均适用；不依赖具体 Tokenizer，
 * 行为确定、跨 Spring AI 版本稳定。</p>
 */
@Component
public class WindowTextSplitter {

	/**
	 * 对文本进行滑动窗口切片。
	 *
	 * @param text      原始文本（已完成清洗）
	 * @param chunkSize 分片大小（字符数，必须大于 0）
	 * @param overlap   重叠窗口（字符数，必须小于 chunkSize）
	 * @return 分片文本列表
	 */
	public List<String> split(String text, int chunkSize, int overlap) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("分片大小 chunkSize 必须大于 0");
		}
		if (overlap < 0 || overlap >= chunkSize) {
			throw new IllegalArgumentException("重叠窗口 overlap 必须满足 0 <= overlap < chunkSize");
		}

		int step = chunkSize - overlap;
		int length = text.length();
		List<String> chunks = new ArrayList<>();

		int start = 0;
		while (start < length) {
			int end = Math.min(start + chunkSize, length);
			String chunk = text.substring(start, end).trim();
			if (!chunk.isBlank()) {
				chunks.add(chunk);
			}
			if (end >= length) {
				break;
			}
			start += step;
		}
		return chunks;
	}

}
