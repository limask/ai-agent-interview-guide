package com.agent.platform.controller;

import com.agent.platform.common.Result;
import com.agent.platform.model.dto.DocumentUploadResponse;
import com.agent.platform.etl.ETLPipeline;
import com.agent.platform.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理控制器
 * <p>
 * 提供文档上传、解析、向量化的入口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    // 注入ETL管道服务，用于处理文档的完整流程：解析 → 切片 → 向量化 → 存储
    private final ETLPipeline etlPipeline;
    private final DocumentService documentService; // 新增注入

    /**
     * 上传并处理文档
     * <p>
     * 支持 PDF、DOCX、TXT、MD 等格式。
     * 上传后自动执行：解析 → 切片 → 向量化 → 存入 Milvus
     *
     * @param file 用户上传的文件对象
     * @return 包含处理结果的响应对象，包括文档ID、切片数量、处理时间等信息
     */
    @PostMapping("/upload")
    public Result<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        long totalStartTime = System.currentTimeMillis();

        log.info("========== 收到文档上传请求 ==========");


        // 第一步：验证文件是否为空
        if (file.isEmpty()) {
            log.warn("上传文件为空");
            return Result.fail(400, "上传文件不能为空");
        }

        // 获取原始文件名，用于日志记录和返回给前端
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        String contentType = file.getContentType();

        log.info("文件名: {}", fileName);
        log.info("文件大小: {} bytes ({} KB, {} MB)", fileSize, fileSize / 1024, fileSize / 1024 / 1024);
        log.info("Content-Type: {}", contentType != null ? contentType : "[未知]");
        log.info("文件扩展名: {}", fileName != null && fileName.contains(".") ?
                fileName.substring(fileName.lastIndexOf('.') + 1) : "[无]");

        if (fileSize > 50 * 1024 * 1024) {
            log.warn("文件大小超过50MB限制: {} MB", fileSize / 1024 / 1024);
            return Result.fail(400, "文件大小不能超过50MB");
        }

        log.info("=======================================");

        // 记录开始处理的时间戳，用于计算总耗时
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始调用ETL管道处理文档...");
            // 第二步：调用ETL管道处理文档
            // 这个过程包括：
            // 1. 文档解析（DocumentParser）- 提取文本内容
            // 2. 文档切片（DocumentChunker）- 将长文本分割成小块
            // 3. 向量化 - 将每个文本块转换为向量
            // 4. 存储到Milvus向量数据库
            long etlStartTime = System.currentTimeMillis();
            DocumentUploadResponse response = etlPipeline.process(file);
            long etlElapsed = System.currentTimeMillis() - etlStartTime;

            // 计算整个处理流程的耗时（毫秒）
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            long totalElapsed = System.currentTimeMillis() - totalStartTime;


            log.info("========== 文档处理完成 ==========");
            log.info("文档ID: {}", response.getDocumentId());
            log.info("文件名: {}", response.getFileName());
            log.info("切片数量: {}", response.getChunkCount());
            log.info("处理状态: {}", response.getStatus());
            log.info("ETL管道耗时: {}ms", etlElapsed);
            log.info("总耗时（含网络传输）: {}ms", totalElapsed);

            double avgSpeed = fileSize > 0 ? (fileSize / 1024.0) / (etlElapsed / 1000.0) : 0;
            log.info("处理速度: {:.2f} KB/s", avgSpeed);
            log.info("====================================");

            // 返回成功响应，包含处理结果
            return Result.success(response);
        } catch (Exception e) {
            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.error("========== 文档处理失败 ==========");
            log.error("文件名: {}", fileName);
            log.error("已耗时: {}ms", totalElapsed);
            log.error("异常类型: {}", e.getClass().getSimpleName());
            log.error("异常消息: {}", e.getMessage());
            log.error("堆栈跟踪:", e);
            log.error("====================================");
            return Result.fail("文档处理失败: " + e.getMessage());
        }
    }
    /**
     * 查询已上传的文档历史列表
     */
    @GetMapping("/history")
    public Result<List<DocumentUploadResponse>> getHistory() {
        log.info("接收到查询文档历史请求");
        try {
            List<DocumentUploadResponse> history = documentService
                    .convertToResponseList(documentService.findAllDocuments());
            return Result.success(history);
        } catch (Exception e) {
            log.error("查询文档历史失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
}
