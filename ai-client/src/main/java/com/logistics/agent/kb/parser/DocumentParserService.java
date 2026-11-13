package com.logistics.agent.kb.parser;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import com.logistics.agent.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析服务。
 *
 * <p>支持多格式：PDF、Word（doc/docx）、Markdown、TXT、Excel（xls/xlsx）、PPT 等。
 * Markdown / TXT 直接按 UTF-8 读取；其余格式通过 Apache Tika 解析。</p>
 */
@Service
public class DocumentParserService {

	private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

	/**
	 * 纯文本类扩展名（直接读取，避免 Tika 编码探测偏差）。
	 */
	private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "csv", "json", "log");

	/**
	 * 解析上传文件，输出纯文本内容。
	 *
	 * @param file 上传的文件
	 * @return 解析出的文本（未清洗）
	 * @throws BizException 文件为空 / 格式不支持 / 解析失败
	 */
	public String parse(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BizException("上传文件为空，请选择有效的文档文件。");
		}
		String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
		String extension = getExtension(originalFilename);
		if (!StringUtils.hasText(extension)) {
			throw new BizException("无法识别文件类型，请上传 PDF、Word、Markdown、TXT 或 Excel 文件。");
		}
		try {
			String rawText;
			if (PLAIN_TEXT_EXTENSIONS.contains(extension)) {
				rawText = new String(file.getBytes(), StandardCharsets.UTF_8);
			}
			else {
				rawText = parseByTika(file, originalFilename);
			}

			if (!StringUtils.hasText(rawText)) {
				throw new BizException("文档解析结果为空，请检查文件内容（如为扫描件 PDF，请先做 OCR 识别）。");
			}
			log.info("文档解析完成 fileName={}, contentLength={}", originalFilename, rawText.length());
			return rawText;
		}
		catch (BizException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.error("文档解析失败 fileName={}", originalFilename, ex);
			throw new BizException("文档解析失败，请确认文件未损坏且格式受支持（PDF/Word/Markdown/TXT/Excel）。", ex);
		}
	}

	/**
	 * 基于 Tika 解析二进制文档。
	 */
	private String parseByTika(MultipartFile file, String filename) throws Exception {
		AbstractResource resource = new InputStreamResource(file.getInputStream()) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
		TikaDocumentReader reader = new TikaDocumentReader(resource);
		List<Document> documents = reader.get();
		return documents.stream()
				.map(Document::getText)
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}

	/**
	 * 获取文件扩展名（小写）。
	 */
	private String getExtension(String filename) {
		int dotIndex = filename.lastIndexOf('.');
		return dotIndex >= 0 && dotIndex < filename.length() - 1
				? filename.substring(dotIndex + 1).toLowerCase() : "";
	}

}
