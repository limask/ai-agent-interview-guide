package com.agent.platform.service;

import com.agent.platform.mapper.DocumentMapper;
import com.agent.platform.model.dto.DocumentUploadResponse;
import com.agent.platform.model.entity.Document;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档服务
 * 负责文档元数据的持久化与查询
 */
@Slf4j
@Service
public class DocumentService extends ServiceImpl<DocumentMapper, Document> { // 继承 MyBatis-Plus 通用 CRUD

    /**
     * 保存文档元数据到 MySQL
     */
    public void saveDocumentMeta(Document doc) {
        this.save(doc); // MyBatis-Plus 自动执行 INSERT
        log.info("文档元数据已入库: id={}, fileName={}", doc.getId(), doc.getFileName());
    }

    /**
     * 查询所有已上传的文档列表（按时间倒序）
     */
    public List<Document> findAllDocuments() {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Document::getCreateTime); // 按创建时间倒序排列
        return this.list(wrapper); // MyBatis-Plus 自动执行 SELECT
    }

    /**
     * 将 Entity 转换为 DTO（返回给前端）
     */
    public List<DocumentUploadResponse> convertToResponseList(List<Document> documents) {
        return documents.stream()
                .map(doc -> DocumentUploadResponse.builder()
                        .documentId(doc.getId())
                        .fileName(doc.getFileName())
                        .chunkCount(doc.getChunkCount())
                        .status(doc.getStatus())
                        .processingTimeMs(0L) // 数据库中不存耗时，前端展示时可忽略
                        .build())
                .collect(Collectors.toList());
    }
}
