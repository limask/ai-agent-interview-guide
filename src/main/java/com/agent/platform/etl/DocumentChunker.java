package com.agent.platform.etl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档切片器
 * <p>
 * 将长文本按照语义边界切分为小块（Chunk），
 * 支持固定大小切片和段落感知切片，带有重叠以保持上下文连续性。
 */
@Slf4j
@Component
public class DocumentChunker {

    @Value("${agent.rag.chunk-size:512}")
    private int chunkSize;

    @Value("${agent.rag.chunk-overlap:64}")
    private int chunkOverlap;

    /**
     * 将文本切分为 Chunk 列表
     *
     * @param text       原始文本
     * @param documentId 文档 ID
     * @return Chunk 列表
     */
    public List<Chunk> chunk(String text, String documentId) {
        if (text == null || text.isBlank()) {
            log.debug("文本为空，返回空切片列表");
            return List.of();
        }
        log.info("========== 开始文档切片 ==========");
        log.info("文档ID: {}", documentId);
        log.info("原始文本长度: {} 字符", text.length());
        log.info("切片配置: chunkSize={}, overlap={}", chunkSize, chunkOverlap);
        log.info("重叠率: {:.1f}%", chunkOverlap * 100.0 / chunkSize);

        long startTime = System.currentTimeMillis();
        List<Chunk> chunks = new ArrayList<>();

        String[] paragraphs = text.split("\n\n");
        log.info("段落分割完成: 共{}个段落", paragraphs.length);
        log.debug("段落长度分布: min={}, max={}, avg={}",
                java.util.Arrays.stream(paragraphs).mapToInt(String::length).min().orElse(0),
                java.util.Arrays.stream(paragraphs).mapToInt(String::length).max().orElse(0),
                java.util.Arrays.stream(paragraphs).mapToInt(String::length).average().orElse(0));

        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;
        int paragraphProcessed = 0;
        int longParagraphSplit = 0;

        for (String paragraph : paragraphs) {
            if (buffer.length() + paragraph.length() + 1 > chunkSize && buffer.length() > 0) {
                chunks.add(createChunk(buffer.toString(), documentId, chunkIndex++));

                int overlapStart = Math.max(0, buffer.length() - chunkOverlap);
                String overlap = buffer.substring(overlapStart);
                buffer = new StringBuilder(overlap);
                log.debug("创建切片[{}]: 缓冲区满, 保留重叠{}字符", chunkIndex, overlap.length());
            }

            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(paragraph);
            paragraphProcessed++;

            if (paragraph.length() > chunkSize) {
                log.debug("段落[{}]超长({}字符)，强制切分", paragraphProcessed, paragraph.length());

                List<Chunk> subChunks = splitLongParagraph(paragraph, documentId, chunkIndex);
                chunks.addAll(subChunks);
                chunkIndex += subChunks.size();
                buffer = new StringBuilder();
                longParagraphSplit++;

                log.debug("长段落切分: 生成{}个子切片", subChunks.size());
            }
        }

        if (buffer.length() > 0) {
            chunks.add(createChunk(buffer.toString(), documentId, chunkIndex));
            log.debug("创建最后一个切片: 缓冲区剩余{}字符", buffer.length());
        }

        long elapsed = System.currentTimeMillis() - startTime;

        int totalChars = chunks.stream().mapToInt(Chunk::getCharCount).sum();
        double avgChunkSize = chunks.stream().mapToInt(Chunk::getCharCount).average().orElse(0);
        int minChunkSize = chunks.stream().mapToInt(Chunk::getCharCount).min().orElse(0);
        int maxChunkSize = chunks.stream().mapToInt(Chunk::getCharCount).max().orElse(0);

        log.info("========== 文档切片完成 ==========");
        log.info("文档ID: {}", documentId);
        log.info("总切片数: {}", chunks.size());
        log.info("处理段落数: {}", paragraphProcessed);
        log.info("长段落切分数: {}", longParagraphSplit);
        log.info("总字符数: {} (覆盖率: {:.1f}%)", totalChars, totalChars * 100.0 / text.length());
        log.info("平均切片大小: {:.0f} 字符", avgChunkSize);
        log.info("最小切片: {} 字符", minChunkSize);
        log.info("最大切片: {} 字符", maxChunkSize);
        log.info("总耗时: {}ms", elapsed);
        log.info("处理速度: {:.0f} 字符/ms", text.length() / (double) elapsed);
        log.info("====================================");

        return chunks;
    }

    /**
     * 超长段落的强制切分
     */
    private List<Chunk> splitLongParagraph(String paragraph, String documentId, int startIndex) {
        log.debug("开始切分长段落: 长度={}字符", paragraph.length());

        List<Chunk> chunks = new ArrayList<>();
        int index = startIndex;

        for (int i = 0; i < paragraph.length(); i += chunkSize - chunkOverlap) {
            int end = Math.min(i + chunkSize, paragraph.length());
            String segment = paragraph.substring(i, end);
            chunks.add(createChunk(segment, documentId, index++));
        }
        log.debug("长段落切分完成: 生成{}个子切片", chunks.size());

        return chunks;
    }

    private Chunk createChunk(String content, String documentId, int index) {
        Chunk chunk = Chunk.builder()
                .id(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .documentId(documentId)
                .content(content.trim())
                .index(index)
                .charCount(content.trim().length())
                .build();

        log.trace("创建切片[{}]: ID={}, 长度={}字符", index, chunk.getId(), chunk.getCharCount());

        return chunk;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class Chunk {
        private String id;
        private String documentId;
        private String content;
        private int index;
        private int charCount;
    }
}
