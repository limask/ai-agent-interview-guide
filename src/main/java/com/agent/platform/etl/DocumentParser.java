package com.agent.platform.etl;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文档解析器
 * <p>
 * 使用 Apache Tika 解析多种文档格式，提取纯文本内容。
 * 支持 PDF、DOCX、TXT、HTML、Markdown 等。
 */
@Slf4j
@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    /**
     * 解析上传文件，提取文本内容
     *
     * @param file 上传的文件
     * @return 提取的文本内容
     */
    public String parse(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();

        log.info("========== 开始文档解析 ==========");
        log.info("文件名: {}", fileName);
        log.info("Content-Type: {}", contentType != null ? contentType : "[未知]");
        log.info("文件大小: {} bytes", fileSize);

        long startTime = System.currentTimeMillis();

        try (InputStream inputStream = file.getInputStream()) {
            tika.setMaxStringLength(10 * 1024 * 1024);

            log.debug("Apache Tika配置: maxStringLength=10MB");
            log.debug("开始调用Tika解析引擎...");

            long tikaStartTime = System.currentTimeMillis();
            String content = tika.parseToString(inputStream);
            long tikaElapsed = System.currentTimeMillis() - tikaStartTime;

            log.info("Tika解析完成: 耗时={}ms", tikaElapsed);
            log.info("原始文本长度: {} 字符", content.length());

            log.debug("开始文本清洗...");
            long cleanStartTime = System.currentTimeMillis();
            content = cleanText(content);

            long cleanElapsed = System.currentTimeMillis() - cleanStartTime;

            int removedChars = content.length() - content.replaceAll("\\s+", "").length();

            log.info("文本清洗完成:");
            log.info("  清洗后长度: {} 字符", content.length());
            log.info("  空白字符占比: {:.1f}%", removedChars * 100.0 / content.length());
            log.info("  清洗耗时: {}ms", cleanElapsed);

            long totalElapsed = System.currentTimeMillis() - startTime;

            log.info("========== 文档解析完成 ==========");
            log.info("总耗时: {}ms", totalElapsed);
            log.info("  - Tika解析: {}ms ({:.1f}%)", tikaElapsed, tikaElapsed * 100.0 / totalElapsed);
            log.info("  - 文本清洗: {}ms ({:.1f}%)", cleanElapsed, cleanElapsed * 100.0 / totalElapsed);
            log.info("最终文本长度: {} 字符", content.length());
            log.info("====================================");

            return content;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;

            log.error("========== 文档解析失败 ==========");
            log.error("文件名: {}", fileName);
            log.error("已耗时: {}ms", elapsed);
            log.error("异常类型: {}", e.getClass().getSimpleName());
            log.error("异常消息: {}", e.getMessage());
            log.error("堆栈跟踪:", e);
            log.error("====================================");
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清洗文本：去除多余空白、特殊字符
     */
    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            log.debug("文本为空，跳过清洗");
            return "";
        }
        int originalLength = text.length();

        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("\\r", "\n");
        text = text.replaceAll("\n{3,}", "\n\n");
        text = text.replaceAll("[ \\t]{2,}", " ");
        text = text.trim();

        int removedLength = originalLength - text.length();

        if (removedLength > 0) {
            log.debug("文本清洗统计: 原始={}字符, 清洗后={}字符, 移除={}字符",
                    originalLength, text.length(), removedLength);
        }

        return text;
    }
}
