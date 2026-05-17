package com.agent.platform.etl; // 定义包路径：ETL数据处理管道

import com.agent.platform.infrastructure.vectordb.MilvusService; // 导入Milvus向量数据库服务
import com.agent.platform.infrastructure.embedding.EmbeddingService; // 导入新创建的Embedding服务
import com.agent.platform.model.dto.DocumentUploadResponse; // 导入文档上传响应DTO
import com.agent.platform.model.entity.Document;
import com.agent.platform.service.DocumentService;
import lombok.RequiredArgsConstructor; // Lombok注解：自动生成构造器注入依赖
import lombok.extern.slf4j.Slf4j; // Lombok注解：自动生成日志对象
import org.springframework.stereotype.Service; // Spring注解：标记为服务层组件
import org.springframework.web.multipart.MultipartFile; // Spring MVC的文件上传对象

import java.util.ArrayList; // 导入ArrayList集合类
import java.util.List; // 导入List接口
import java.util.UUID; // 导入UUID工具类，用于生成唯一ID

/**
 * ETL 数据管道
 * <p>
 * 文档处理完整流程：
 * <ol>
 *   <li>Extract（提取）：使用 DocumentParser 从文件中提取文本</li>
 *   <li>Transform（转换）：使用 DocumentChunker 切分文本为 Chunk</li>
 *   <li>Load（加载）：向量化后存入 Milvus</li>
 * </ol>
 */
@Slf4j // 启用日志功能，生成 log 对象
@Service // 标记为Spring服务组件，自动注册到容器
@RequiredArgsConstructor // 自动生成包含final字段的构造器，实现依赖注入
public class ETLPipeline {

    private final DocumentParser documentParser; // 注入文档解析器：负责从PDF/DOCX等格式提取文本
    private final DocumentChunker documentChunker; // 注入文档切片器：负责将长文本分割成小块
    private final MilvusService milvusService; // 注入Milvus服务：负责向量存储和检索
    private final EmbeddingService embeddingService; // 注入Embedding服务：负责生成真实向量（新增）
    private final DocumentService documentService; // 新增注入

    /**
     * 执行完整的 ETL 流程
     *
     * @param file 上传的文件（如：用户上传的PDF文档）
     * @return 处理结果（包含文档ID、切片数量等信息）
     */
    public DocumentUploadResponse process(MultipartFile file) { // 公开方法：处理上传的文件

        String documentId = UUID.randomUUID().toString().replace("-", ""); // 生成唯一的文档ID（去掉横杠）
        String fileName = file.getOriginalFilename(); // 获取原始文件名
        String fileType = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1) : "unknown";
        long fileSize = file.getSize();

        log.info("========== ETL 管道启动 ==========");
        log.info("文档ID: {}", documentId);
        log.info("文件名: {}", fileName);
        log.info("文件大小: {} bytes", fileSize);
        log.info("====================================");

        long pipelineStartTime = System.currentTimeMillis();
        // E: 提取文本
        log.info("━━━ 阶段1: Extract（文本提取） ━━━");
        long parseStartTime = System.currentTimeMillis();
        String text = documentParser.parse(file);
        long parseElapsed = System.currentTimeMillis() - parseStartTime;

        if (text.isBlank()) { // 检查提取的文本是否为空
            log.error("文档内容为空，无法处理");
            throw new RuntimeException("文档内容为空，无法处理"); // 抛出异常：终止处理
        }
        log.info("文本提取完成:");
        log.info("  文本长度: {} 字符", text.length());
        log.info("  预估字数: {} 字", text.replaceAll("\\s+", "").length());
        log.info("  行数: {}", text.split("\n").length);
        log.info("  段落数: {}", text.split("\n\n").length);
        log.info("  耗时: {}ms", parseElapsed);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // T: 切分为 Chunk
        log.info("━━━ 阶段2: Transform（文本切片） ━━━");
        long chunkStartTime = System.currentTimeMillis();
        List<DocumentChunker.Chunk> chunks = documentChunker.chunk(text, documentId);
        long chunkElapsed = System.currentTimeMillis() - chunkStartTime;

        if (chunks.isEmpty()) { // 检查切片结果是否为空
            log.error("文档切片结果为空");
            throw new RuntimeException("文档切片结果为空"); // 抛出异常：终止处理
        }
        int totalChars = chunks.stream().mapToInt(DocumentChunker.Chunk::getCharCount).sum();
        double avgChunkSize = chunks.stream().mapToInt(DocumentChunker.Chunk::getCharCount).average().orElse(0);
        int minChunkSize = chunks.stream().mapToInt(DocumentChunker.Chunk::getCharCount).min().orElse(0);
        int maxChunkSize = chunks.stream().mapToInt(DocumentChunker.Chunk::getCharCount).max().orElse(0);

        log.info("文本切片完成:");
        log.info("  切片数量: {}", chunks.size());
        log.info("  总字符数: {}", totalChars);
        log.info("  平均切片大小: {:.0f} 字符", avgChunkSize);
        log.info("  最小切片: {} 字符", minChunkSize);
        log.info("  最大切片: {} 字符", maxChunkSize);
        log.info("  覆盖率: {:.1f}%", (totalChars * 100.0 / text.length()));
        log.info("  耗时: {}ms", chunkElapsed);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        log.debug("切片详情预览（前3个）:");
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            DocumentChunker.Chunk chunk = chunks.get(i);
            log.debug("  [{}] ID={}, 索引={}, 长度={}字符, 内容预览: {}",
                    i + 1, chunk.getId(), chunk.getIndex(), chunk.getCharCount(),
                    truncate(chunk.getContent(), 100));
        }

        // L: 向量化并存入 Milvus
        log.info("━━━ 阶段3: Load（向量化与存储） ━━━");
        long loadStartTime = System.currentTimeMillis();
        loadToVectorDB(chunks); // 调用加载方法：生成向量并存储到Milvus
        long loadElapsed = System.currentTimeMillis() - loadStartTime;
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        long pipelineElapsed = System.currentTimeMillis() - pipelineStartTime;

        // ========== 新增：将文档元数据持久化到 MySQL ==========
        try {
            Document docMeta = Document.builder()
                    .id(documentId) // 使用 ETL 生成的 ID
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileSize(fileSize)
                    .chunkCount(chunks.size())
                    .status("completed")
                    .build();
            documentService.saveDocumentMeta(docMeta);
            log.info("文档元数据已持久化到 MySQL: {}", documentId);
        } catch (Exception e) {
            log.error("文档元数据入库失败（不影响向量存储）: {}", e.getMessage());
        }
        // ========== 新增结束 ==========

        log.info("========== ETL 管道完成 ==========");
        log.info("文档ID: {}", documentId);
        log.info("总耗时: {}ms", pipelineElapsed);
        log.info("  - 文本提取: {}ms ({:.1f}%)", parseElapsed, parseElapsed * 100.0 / pipelineElapsed);
        log.info("  - 文本切片: {}ms ({:.1f}%)", chunkElapsed, chunkElapsed * 100.0 / pipelineElapsed);
        log.info("  - 向量化存储: {}ms ({:.1f}%)", loadElapsed, loadElapsed * 100.0 / pipelineElapsed);
        log.info("最终切片数: {}", chunks.size());
        log.info("====================================");

        return DocumentUploadResponse.builder() // 构建响应对象
                .documentId(documentId) // 设置文档ID
                .fileName(fileName) // 设置文件名
                .chunkCount(chunks.size()) // 设置切片数量
                .status("completed") // 设置状态为完成
                .build(); // 构建最终对象
    }

    /**
     * 将 Chunk 向量化并存入 Milvus
     * <p>
     * 生产环境应通过 Embedding 模型生成真实向量，
     * 此处使用占位向量演示流程。
     */
    private void loadToVectorDB(List<DocumentChunker.Chunk> chunks) {  // 私有方法：加载向量到数据库
        List<String> ids = new ArrayList<>();  // 创建ID列表：存储每个chunk的唯一标识
        // List<List<Float>> vectors = new ArrayList<>();   // 创建内容列表：存储每个chunk的文本内容
        List<String> contents = new ArrayList<>();

//        for (DocumentChunker.Chunk chunk : chunks) {
//            ids.add(chunk.getId());
//            vectors.add(generatePlaceholderEmbedding());
//            contents.add(chunk.getContent());
//        }

        for (DocumentChunker.Chunk chunk : chunks) { // 遍历所有文本块
            ids.add(chunk.getId());   // 添加chunk的ID到列表
            contents.add(chunk.getContent());  // 添加chunk的文本内容到列表
        }
        log.info("准备向量化: 共{}个文本块", contents.size());
        log.info("文本块ID列表: {}", ids.subList(0, Math.min(5, ids.size())));

//        try {
//            milvusService.insertVectors(ids, vectors, contents);
//            log.info("成功写入 {} 条向量到 Milvus", chunks.size());
//        } catch (Exception e) {
//            log.error("向量写入 Milvus 失败", e);
//            throw new RuntimeException("向量存储失败: " + e.getMessage(), e);
//        }

        try { // 开始异常处理块
            log.info("开始批量生成Embedding向量...");
            long embedStartTime = System.currentTimeMillis();

            List<List<Float>> vectors = embeddingService.embedBatch(contents); // 【关键】调用Embedding服务，批量生成真实向量

            long embedElapsed = System.currentTimeMillis() - embedStartTime;

            log.info("Embedding生成完成:");
            log.info("  向量数量: {}", vectors.size());
            log.info("  向量维度: {}", vectors.isEmpty() ? 0 : vectors.get(0).size());
            log.info("  总耗时: {}ms", embedElapsed);
            log.info("  平均每个向量: {:.0f}ms", embedElapsed / (double) vectors.size());

            if (!vectors.isEmpty()) {
                List<Float> firstVector = vectors.get(0);
                double min = firstVector.stream().mapToDouble(Float::doubleValue).min().orElse(0.0);
                double max = firstVector.stream().mapToDouble(Float::doubleValue).max().orElse(0.0);
                double mean = firstVector.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
                log.debug("首个向量统计: min={:.4f}, max={:.4f}, mean={:.4f}", min, max, mean);
            }

            log.info("开始写入Milvus向量数据库...");
            long milvusStartTime = System.currentTimeMillis();

            milvusService.insertVectors(ids, vectors, contents); // 将ID、向量、内容插入Milvus数据库

            long milvusElapsed = System.currentTimeMillis() - milvusStartTime;

            log.info("Milvus写入完成:");
            log.info("  写入记录数: {}", chunks.size());
            log.info("  耗时: {}ms", milvusElapsed);
            log.info("  写入速度: {:.0f} 条/秒", chunks.size() / (milvusElapsed / 1000.0));

        } catch (Exception e) { // 捕获所有异常

            log.error("========== 向量写入Milvus失败 ==========");
            log.error("异常类型: {}", e.getClass().getSimpleName());
            log.error("异常消息: {}", e.getMessage());
            log.error("待写入记录数: {}", chunks.size());
            log.error("堆栈跟踪:", e);
            log.error("=========================================");

            throw new RuntimeException("向量存储失败: " + e.getMessage(), e); // 抛出包装后的异常
        }
    }

    /**
     * 占位向量生成（1536维，模拟 OpenAI text-embedding-ada-002）
     * 生产环境应替换为真实 Embedding 模型调用
     */
    private List<Float> generatePlaceholderEmbedding() {
        List<Float> embedding = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            embedding.add((float) Math.random() * 0.1f);
        }
        return embedding;
    }
    /**
     * 截断文本到指定长度
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "[null]";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
