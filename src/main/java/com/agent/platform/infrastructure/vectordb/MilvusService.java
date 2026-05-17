package com.agent.platform.infrastructure.vectordb;

import com.agent.platform.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Milvus 向量数据库服务
 * <p>
 * 适配版本: Milvus SDK 2.5.3 (稳定版)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {

    private final MilvusServiceClient milvusClient;
    private final MilvusConfig milvusConfig;

    @PostConstruct
    public void init() {
        ensureCollectionLoaded();
    }

    /**
     * 插入向量数据
     */
    public long insertVectors(List<String> ids, List<List<Float>> vectors, List<String> contents) {
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("doc_id", ids));
        fields.add(new InsertParam.Field("embedding", vectors));
        fields.add(new InsertParam.Field("content", contents));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withFields(fields)
                .build();

        R<MutationResult> response = milvusClient.insert(insertParam);

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus 向量插入失败: {}", response.getMessage());
            throw new RuntimeException("向量插入失败: " + response.getMessage());
        }

        // 新增：强制刷盘，确保数据对查询和可视化工具立即可见
        FlushParam flushParam = FlushParam.newBuilder()
                .withCollectionNames(List.of(milvusConfig.getCollectionName()))
                .build();
        milvusClient.flush(flushParam);
        log.info("已执行 Flush 操作，数据已落盘");

        long count = response.getData().getInsertCnt();
        log.info("成功插入 {} 条向量到集合 {}", count, milvusConfig.getCollectionName());
        return count;
    }

    /**
     * 向量相似度搜索
     */
    public SearchResults searchSimilar(List<Float> queryVector, int topK) {
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withMetricType(MetricType.COSINE)
                .withOutFields(List.of("doc_id", "content"))
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName("embedding")
                .withParams("{\"nprobe\": 16}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("向量搜索失败: " + response.getMessage());
        }
        return response.getData();
    }

    /**
     * 确保集合已加载，若不存在则创建
     */
    private void ensureCollectionLoaded() {
        try {
            String collectionName = milvusConfig.getCollectionName();
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(collectionName).build()
            );

            if (hasCollection.getData()) {
                milvusClient.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collectionName).build());
                log.info("Milvus 集合 {} 已加载到内存", collectionName);
            } else {
                log.info("Milvus 集合 {} 不存在，正在创建...", collectionName);
                createCollection(collectionName);
            }
        } catch (Exception e) {
            log.warn("Milvus 初始化检查失败: {}", e.getMessage());
        }
    }

    /**
     * 创建集合 (SDK 2.5.3 最终稳定写法)
     */
    private void createCollection(String collectionName) {
        List<FieldType> fieldTypes = new ArrayList<>();

        // 字段 1：文档 ID
        fieldTypes.add(FieldType.newBuilder()
                .withName("doc_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(100)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build());

        // 字段 2：向量数据
        fieldTypes.add(FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(milvusConfig.getDimension())
                .build());

        // 字段 3：文本内容
        fieldTypes.add(FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build());

        // 使用 withFieldTypes 直接传入 FieldType 列表
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldTypes(fieldTypes)
                .withShardsNum(2)
                .build();

        R<RpcStatus> response = milvusClient.createCollection(createParam);
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("Milvus 集合 {} 创建成功", collectionName);
            createIndex(collectionName);
            milvusClient.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collectionName).build());
        } else {
            log.error("Milvus 集合创建失败: {}", response.getMessage());
        }
    }

    /**
     * 创建索引
     */
    private void createIndex(String collectionName) {
        IndexType indexType = IndexType.valueOf(milvusConfig.getIndexType().toUpperCase());
        MetricType metricType = MetricType.valueOf(milvusConfig.getMetricType().toUpperCase());

        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding")
                .withIndexType(indexType)
                .withMetricType(metricType)
                .withExtraParam(String.format("{\"nlist\":%d}", milvusConfig.getNlist()))
                .build();

        R<RpcStatus> response = milvusClient.createIndex(indexParam);
        if (response.getStatus() == R.Status.Success.getCode()) {
            log.info("Milvus 索引创建成功");
        } else {
            log.error("Milvus 索引创建失败: {}", response.getMessage());
        }
    }
}
