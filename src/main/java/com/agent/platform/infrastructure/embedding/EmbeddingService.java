package com.agent.platform.infrastructure.embedding; // 定义包路径：基础设施层的嵌入服务

import lombok.RequiredArgsConstructor; // Lombok注解：自动生成构造器注入依赖
import lombok.extern.slf4j.Slf4j; // Lombok注解：自动生成日志对象
import org.springframework.ai.embedding.EmbeddingModel; // Spring AI的嵌入模型接口
import org.springframework.ai.embedding.EmbeddingRequest; // Spring AI的嵌入请求对象
import org.springframework.ai.openai.OpenAiEmbeddingOptions; // OpenAI兼容的嵌入选项配置
import org.springframework.stereotype.Service; // Spring注解：标记为服务层组件

import java.util.ArrayList; // 导入ArrayList集合类（新增）
import java.util.List; // 导入List集合类

/**
 * Embedding 服务
 * <p>
 * 职责：将文本转换为向量表示
 * 使用通义千问的 text-embedding-v2 模型生成1536维向量
 */
@Slf4j // 启用日志功能，生成 log 对象
@Service // 标记为Spring服务组件，自动注册到容器
@RequiredArgsConstructor // 自动生成包含final字段的构造器，实现依赖注入
public class EmbeddingService {

    private final EmbeddingModel embeddingModel; // 注入Spring AI的嵌入模型实例（由OpenAIConfig配置）

    /**
     * 为单个文本生成向量
     *
     * @param text 输入文本（如："人工智能是计算机科学的一个分支"）
     * @return 向量表示（1536维的浮点数列表）
     */
    public List<Float> embed(String text) { // 公开方法：接收文本，返回向量

        log.debug("生成文本向量: length={}", text.length()); // 记录调试日志：输出文本长度

        try { // 开始异常处理块

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder() // 创建嵌入选项构建器
                    .model("text-embedding-v2") // 指定使用通义千问的text-embedding-v2模型
                    .build(); // 构建选项对象

            EmbeddingRequest request = new EmbeddingRequest(List.of(text), options); // 创建嵌入请求：包含文本列表和选项

            var response = embeddingModel.call(request); // 调用嵌入模型，获取响应结果

            if (response == null || response.getResults().isEmpty()) { // 检查响应是否为空或结果为空
                throw new RuntimeException("Embedding 返回结果为空"); // 抛出运行时异常
            }

            float[] vectorArray = response.getResults().get(0).getOutput(); // 【修复】获取float[]数组

            List<Float> vector = new ArrayList<>(); // 创建List<Float>用于存储转换后的向量

            for (float value : vectorArray) { // 遍历float数组的每个元素
                vector.add(value); // 将每个float值添加到List中（自动装箱为Float）
            }

            log.debug("向量生成成功: dimension={}", vector.size()); // 记录调试日志：输出向量维度

            return vector; // 返回生成的向量

        } catch (Exception e) { // 捕获所有异常

            log.error("Embedding 生成失败: {}", e.getMessage(), e); // 记录错误日志：包含异常消息和堆栈

            throw new RuntimeException("向量生成失败: " + e.getMessage(), e); // 抛出包装后的异常
        }
    }

    /**
     * 批量生成向量（效率更高，减少API调用次数）
     *
     * @param texts 文本列表（如：["文本1", "文本2", "文本3"]）
     * @return 向量列表（每个文本对应一个向量）
     */
    public List<List<Float>> embedBatch(List<String> texts) { // 公开方法：接收文本列表，返回向量列表

        log.info("批量生成向量: count={}", texts.size()); // 记录信息日志：输出待处理的文本数量

        try { // 开始异常处理块

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder() // 创建嵌入选项构建器
                    .model("text-embedding-v2") // 指定使用通义千问的text-embedding-v2模型
                    .build(); // 构建选项对象

            EmbeddingRequest request = new EmbeddingRequest(texts, options); // 创建批量嵌入请求：包含所有文本和选项

            var response = embeddingModel.call(request); // 调用嵌入模型，获取批量响应结果

            if (response == null || response.getResults().isEmpty()) { // 检查响应是否为空或结果为空
                throw new RuntimeException("批量 Embedding 返回结果为空"); // 抛出运行时异常
            }

            List<List<Float>> vectors = new ArrayList<>(); // 创建外层List用于存储所有向量

            for (var result : response.getResults()) { // 遍历每个嵌入结果

                float[] vectorArray = result.getOutput(); // 【修复】获取float[]数组

                List<Float> vector = new ArrayList<>(); // 创建内层List用于存储单个向量

                for (float value : vectorArray) { // 遍历float数组的每个元素
                    vector.add(value); // 将每个float值添加到List中（自动装箱为Float）
                }

                vectors.add(vector); // 将转换后的向量添加到外层列表
            }

            log.info("批量向量生成成功: count={}, dimension={}",  // 记录信息日志
                    vectors.size(),  // 输出向量数量
                    vectors.isEmpty() ? 0 : vectors.get(0).size()); // 输出向量维度（如果为空则为0）

            return vectors; // 返回所有向量

        } catch (Exception e) { // 捕获所有异常

            log.error("批量 Embedding 生成失败: {}", e.getMessage(), e); // 记录错误日志：包含异常消息和堆栈

            throw new RuntimeException("批量向量生成失败: " + e.getMessage(), e); // 抛出包装后的异常
        }
    }
}
