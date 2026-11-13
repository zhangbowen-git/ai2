package com.logistics.agent.kb.parser;

/**
 * 文本清洗工具。
 *
 * <p>统一换行符、去除控制字符与分页符、折叠无效空白与连续空行。</p>
 */
public final class TextCleaner {

	private TextCleaner() {
	}

	/**
	 * 清洗文本。
	 *
	 * @param text 原始文本
	 * @return 清洗后的文本
	 */
	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		String result = text;
		// 统一换行符
		result = result.replace("\r\n", "\n").replace("\r", "\n");
		// 去除 ASCII 控制字符（保留换行 \n 与制表 \t），含 Tika 分页符 \f
		result = result.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
		// 折叠除换行外的连续空白（含 NBSP）为单个空格
		result = result.replaceAll("[^\\S\\n\u00A0]+|[\u00A0]", " ");
		// 去除每行首尾空白
		result = result.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n[ \\t]+", "\n");
		// 连续空行折叠为一个空行
		result = result.replaceAll("\\n{3,}", "\n\n");
		return result.trim();
	}

}
