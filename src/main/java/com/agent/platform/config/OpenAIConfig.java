package com.agent.platform.config; // 定义包路径：配置类

import org.springframework.ai.embedding.EmbeddingModel; // 导入Spring AI的嵌入模型接口
import org.springframework.ai.openai.OpenAiChatModel; // 导入OpenAI聊天模型
import org.springframework.ai.openai.OpenAiChatOptions; // 导入OpenAI聊天选项
import org.springframework.ai.openai.OpenAiEmbeddingModel; // 导入OpenAI嵌入模型（新增）
import org.springframework.ai.openai.api.OpenAiApi; // 导入OpenAI API客户端
import org.springframework.beans.factory.annotation.Value; // Spring注解：注入配置文件中的值
import org.springframework.context.annotation.Bean; // Spring注解：标记为Bean定义方法
import org.springframework.context.annotation.Configuration; // Spring注解：标记为配置类

/**
 * OpenAI 模型配置类
 * <p>
 * 提供多模型实例，供 ModelRouter 进行路由选择
 * 包括：聊天模型（主/备）和嵌入模型
 */

@Configuration // 标记为Spring配置类：会被Spring扫描并加载
public class OpenAIConfig {

    @Value("${spring.ai.openai.api-key}") // 从配置文件注入API密钥
    private String apiKey; // 存储阿里云通义千问的API Key

    @Value("${spring.ai.openai.base-url}") // 从配置文件注入基础URL
    private String baseUrl; // 存储阿里云通义千问的API端点

    /**
     * 主力模型 - 通义千问 qwen-turbo，用于复杂推理任务
     */
    @Bean(name = "primaryChatModel") // 定义Bean：名称为primaryChatModel
    public OpenAiChatModel primaryChatModel() { // 创建主聊天模型

        OpenAiApi api = OpenAiApi.builder() // 创建OpenAI API客户端构建器
                .apiKey(apiKey) // 设置API密钥
                .baseUrl(baseUrl) // 设置基础URL
                .build(); // 构建API客户端对象

        OpenAiChatOptions options = OpenAiChatOptions.builder() // 创建聊天选项构建器
                .model("qwen-turbo") // 指定使用qwen-plus模型（能力较强）
                .temperature(0.7) // 设置温度参数：0.7表示平衡创造性和准确性
                .maxTokens(4096) // 设置最大token数：控制输出长度
                .build(); // 构建选项对象

        return OpenAiChatModel.builder() // 创建聊天模型构建器
                .openAiApi(api) // 设置API客户端
                .defaultOptions(options) // 设置默认选项
                .build(); // 构建并返回聊天模型
    }

    /**
     * 快速模型 - 通义千问 qwen-turbo，用于简单任务和降级场景
     */
    @Bean(name = "fallbackChatModel") // 定义Bean：名称为fallbackChatModel
    public OpenAiChatModel fallbackChatModel() { // 创建备用聊天模型

        OpenAiApi api = OpenAiApi.builder() // 创建OpenAI API客户端构建器
                .apiKey(apiKey) // 设置API密钥
                .baseUrl(baseUrl) // 设置基础URL
                .build(); // 构建API客户端对象

        OpenAiChatOptions options = OpenAiChatOptions.builder() // 创建聊天选项构建器
                .model("qwen-turbo") // 指定使用qwen-turbo模型（速度较快）
                .temperature(0.7) // 设置温度参数：0.7
                .maxTokens(2048) // 设置最大token数：比主模型少
                .build(); // 构建选项对象

        return OpenAiChatModel.builder() // 创建聊天模型构建器
                .openAiApi(api) // 设置API客户端
                .defaultOptions(options) // 设置默认选项
                .build(); // 构建并返回聊天模型
    }

    /**
     * Embedding 模型 - 用于生成文本向量
     * <p>
     * 这个模型不参与对话，只负责将文本转换为向量表示
     * 用于RAG检索时的语义匹配
     */
    @Bean // 定义Bean：默认名称为方法名 embeddingModel
    public EmbeddingModel embeddingModel() { // 创建嵌入模型

        OpenAiApi api = OpenAiApi.builder() // 创建OpenAI API客户端构建器
                .apiKey(apiKey) // 设置API密钥（与聊天模型共用）
                .baseUrl(baseUrl) // 设置基础URL（与聊天模型共用）
                .build(); // 构建API客户端对象

        return new OpenAiEmbeddingModel(api); // 创建并返回嵌入模型实例
    }


//    /**
//     * 主力模型 - GPT-4o，用于复杂推理任务
//     */
//    @Bean(name = "primaryChatModel")
//    public OpenAiChatModel primaryChatModel() {
//        OpenAiApi api = OpenAiApi.builder()
//                .apiKey(apiKey)
//                .baseUrl(baseUrl)
//                .build();
//        OpenAiChatOptions options = OpenAiChatOptions.builder()
//                .model("gpt-4o")
//                .temperature(0.7)
//                .maxTokens(4096)
//                .build();
//        return OpenAiChatModel.builder()
//                .openAiApi(api)
//                .defaultOptions(options)
//                .build();
//    }
//
//    /**
//     * 快速模型 - GPT-4o-mini，用于简单任务和降级场景
//     */
//    @Bean(name = "fallbackChatModel")
//    public OpenAiChatModel fallbackChatModel() {
//        OpenAiApi api = OpenAiApi.builder()
//                .apiKey(apiKey)
//                .baseUrl(baseUrl)
//                .build();
//        OpenAiChatOptions options = OpenAiChatOptions.builder()
//                .model("gpt-4o-mini")
//                .temperature(0.7)
//                .maxTokens(2048)
//                .build();
//        return OpenAiChatModel.builder()
//                .openAiApi(api)
//                .defaultOptions(options)
//                .build();
//    }
}
