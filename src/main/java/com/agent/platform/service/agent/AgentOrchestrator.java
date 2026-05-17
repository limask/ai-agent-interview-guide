package com.agent.platform.service.agent;

import com.agent.platform.infrastructure.embedding.EmbeddingService;
import com.agent.platform.infrastructure.llm.ModelRouter;
import com.agent.platform.infrastructure.trace.TraceService;
import com.agent.platform.model.dto.ChatRequest;
import com.agent.platform.model.dto.ChatResponse;
import com.agent.platform.model.enums.AgentMode;
import com.agent.platform.service.intent.IntentRecognizer;
import com.agent.platform.service.memory.MemoryManager;
import com.agent.platform.service.rag.MultiRetriever;
import com.agent.platform.service.rag.RAGGenerator;
import com.agent.platform.service.rag.Reranker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 编排器
 * <p>
 * 系统的核心调度中枢，负责：
 * <ul>
 *   <li>意图识别 → 选择 Agent 模式</li>
 *   <li>RAG 检索 → 提供上下文</li>
 *   <li>Agent 执行 → ReAct / Planner / Reflection</li>
 *   <li>记忆管理 → 保存对话历史</li>
 *   <li>全链路追踪 → 记录执行过程</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final IntentRecognizer intentRecognizer; // 注入意图识别器：分析用户意图
    private final MemoryManager memoryManager; // 注入记忆管理器：管理短期/长期记忆
    private final MultiRetriever multiRetriever; // 注入多路检索器：从知识库检索相关内容
    private final Reranker reranker; // 注入重排序器：对检索结果重新排序
    private final RAGGenerator ragGenerator; // 注入RAG生成器：基于检索结果生成回答
    private final ReActAgent reActAgent; // 注入ReAct Agent：推理+行动的Agent
    private final PlannerAgent plannerAgent; // 注入Planner Agent：规划复杂任务
    private final ReflectionAgent reflectionAgent; // 注入Reflection Agent：自我反思优化
    private final ModelRouter modelRouter; // 注入模型路由器：路由到合适的LLM
    private final TraceService traceService; // 注入追踪服务：记录执行轨迹
    private final EmbeddingService embeddingService; // 注入Embedding服务：生成查询向量（新增）

    /**
     * 处理对话请求（同步）
     *
     * @param request 对话请求（包含用户消息、会话ID等）
     * @return 对话响应（包含AI回复、思考过程等）
     */

    public ChatResponse chat(ChatRequest request) {  // 公开方法：处理对话请求
        long totalStartTime = System.currentTimeMillis();
        String traceId = traceService.startTrace("chat");  // 开始追踪：生成唯一的traceId

        try {  // 开始异常处理块

            String conversationId = resolveConversationId(request.getConversationId()); // 解析会话ID：如果为空则生成新的
            log.info("会话ID解析: 原始={}, 最终={}",
                    request.getConversationId() != null ? request.getConversationId() : "[null]",
                    conversationId);

            memoryManager.saveUserMessage(conversationId, request.getMessage()); // 保存用户消息到短期记忆

            traceService.addSpan(traceId, "intent_recognition", Map.of("message", request.getMessage())); // 添加追踪span：意图识别阶段

            long intentStartTime = System.currentTimeMillis();
            IntentRecognizer.IntentResult intent = intentRecognizer.recognize(request.getMessage()); // 识别用户意图
            long intentElapsed = System.currentTimeMillis() - intentStartTime;

            AgentMode mode = request.getMode() != null ? request.getMode() : intent.getAgentMode(); // 确定Agent模式：优先使用请求指定的，否则使用意图识别的结果

            log.info("========== 意图识别完成 ==========");
            log.info("识别方式: {}", request.getMode() != null ? "[手动指定]" : "[自动识别]");
            log.info("意图类型: {}", intent.getIntentType());
            log.info("置信度: {}", intent.getConfidence());
            log.info("需要RAG: {}", intent.isNeedsRag());
            log.info("建议工具: {}", intent.getSuggestedTools());
            log.info("选定Agent模式: {}", mode);
            log.info("耗时: {}ms", intentElapsed);
            log.info("====================================");


            String ragContext = ""; // 初始化RAG上下文为空字符串
            List<ChatResponse.SourceDocument> sources = new ArrayList<>(); // 创建来源文档列表：存储检索到的文档片段

//            if (request.isEnableRag() && intent.isNeedsRag()) { // 判断是否需要RAG：用户启用且意图需要
//                log.info("触发RAG检索: 用户启用={}, 意图需要={}", request.isEnableRag(), intent.isNeedsRag());
//
//                traceService.addSpan(traceId, "rag_retrieval", Map.of()); // 添加追踪span：RAG检索阶段
//
//                long ragStartTime = System.currentTimeMillis();
//                ragContext = performRAG(request.getMessage(), sources, traceId); // 执行RAG检索，获取相关上下文
//                long ragElapsed = System.currentTimeMillis() - ragStartTime;
//                log.info("RAG检索完成: 检索到{}个文档, 上下文长度={}字符, 耗时={}ms",
//                        sources.size(), ragContext.length(), ragElapsed);
//            } else {
//                log.info("跳过RAG检索: 用户启用={}, 意图需要={}", request.isEnableRag(), intent.isNeedsRag());
//            }

            // 【修改】企业级方案：只要前端开启 RAG 开关，就强制尝试检索
            // 检索结果将作为“置信度校验”：如果库里有相关内容，就优先用知识库；如果没有，则退化为闲聊
            if (request.isEnableRag()) {
                log.info("触发RAG检索: 用户启用开关，尝试从知识库检索...");

                traceService.addSpan(traceId, "rag_retrieval", Map.of()); // 添加追踪span：RAG检索阶段

                long ragStartTime = System.currentTimeMillis();
                ragContext = performRAG(request.getMessage(), sources, traceId); // 执行RAG检索
                long ragElapsed = System.currentTimeMillis() - ragStartTime;

                // 【核心逻辑】意图驱动 + 检索置信度校验
                // 即使意图识别认为是“闲聊”(intent.isNeedsRag() == false)
                // 但如果检索到了内容 (sources.size() > 0)，说明用户的问题在知识库中有答案
                // 此时应优先使用知识库内容回答
                if (!intent.isNeedsRag() && !sources.isEmpty()) {
                    log.info("意图识别判定为闲聊，但检索到 {} 条相关知识，强制启用 RAG 上下文", sources.size());
                }

                log.info("RAG检索完成: 检索到{}个文档, 上下文长度={}字符, 耗时={}ms",
                        sources.size(), ragContext.length(), ragElapsed);
            } else {
                log.info("跳过RAG检索: 用户未启用开关");
            }

            traceService.addSpan(traceId, "agent_execution", // 添加追踪span：Agent执行阶段
                    Map.of("mode", mode.name(), "intent", intent.getIntentType())); // 记录模式和意图

            log.info("开始Agent执行: 模式={}, 意图={}, RAG上下文长度={}",
                    mode, intent.getIntentType(), ragContext.length());

            long agentStartTime = System.currentTimeMillis();
            ChatResponse response = dispatchToAgent(mode, request, ragContext, traceId); // 分发到对应的Agent执行
            long agentElapsed = System.currentTimeMillis() - agentStartTime;

            log.info("Agent执行完成: 耗时={}ms", agentElapsed);

            response.setConversationId(conversationId); // 设置响应的会话ID
            response.setSources(sources.isEmpty() ? null : sources); // 设置来源文档：如果为空则设为null
            response.setTraceId(traceId); // 设置追踪ID

            memoryManager.saveAssistantMessage(conversationId, response.getReply()); // 保存助手回复到短期记忆

            memoryManager.archiveIfNeeded(conversationId); // 如果需要，归档长期记忆

            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.info("对话处理总耗时: {}ms (意图:{}ms + RAG:{}ms + Agent:{}ms)",
                    totalElapsed,
                    intentElapsed,
                    request.isEnableRag() && intent.isNeedsRag() ? "已执行" : "跳过",
                    agentElapsed);

            return response; // 返回对话响应

        } catch (Exception e) { // 捕获所有异常

            // 记录错误日志
            log.error("========== 对话处理异常 ==========");
            log.error("异常类型: {}", e.getClass().getSimpleName());
            log.error("异常消息: {}", e.getMessage());
            log.error("堆栈跟踪:", e);
            log.error("====================================");

            return ChatResponse.builder() // 构建错误响应
                    .reply("抱歉，处理您的请求时发生了错误: " + e.getMessage()) // 设置错误消息
                    .traceId(traceId) // 设置追踪ID
                    .build(); // 构建最终对象
        } finally { // 最终执行块（无论是否异常都会执行）

            traceService.endTrace(traceId); // 结束追踪：记录总耗时
        }
    }

    /**
     * 处理对话请求（流式 SSE）
     */
    public Flux<String> chatStream(ChatRequest request) {
        String traceId = traceService.startTrace("chat_stream");
        String conversationId = resolveConversationId(request.getConversationId());

        memoryManager.saveUserMessage(conversationId, request.getMessage());

        String ragContext = "";

//        if (request.isEnableRag()) {
//            IntentRecognizer.IntentResult intent = intentRecognizer.recognize(request.getMessage());
//            if (intent.isNeedsRag()) {
//                log.info("流式对话触发RAG检索");
//                ragContext = performRAG(request.getMessage(), new ArrayList<>(), traceId);
//            }
//        }
        // 【修改】流式接口同样采用“前端开关优先”策略
        if (request.isEnableRag()) {
            IntentRecognizer.IntentResult intent = intentRecognizer.recognize(request.getMessage());
            // 即使意图识别认为是闲聊，只要开启了开关，也尝试检索
            log.info("流式对话触发RAG检索 (无视意图识别结果)");
            ragContext = performRAG(request.getMessage(), new ArrayList<>(), traceId);
        }

        String promptText = buildDirectPrompt(request.getMessage(), ragContext);
        String forceModel = request.getModelOptions() != null ? request.getModelOptions().getModel() : null;

        log.info("流式对话准备完成: Prompt长度={}, 强制模型={}", promptText.length(), forceModel);

        return modelRouter.stream(new Prompt(promptText), forceModel)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null &&
                            chatResponse.getResult().getOutput() != null) {
                        String text = chatResponse.getResult().getOutput().getText();
                        return text != null ? text : "";
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty())
                .doOnComplete(() -> {
                    traceService.endTrace(traceId);
                    log.info("流式对话完成: traceId={}", traceId);
                })
                .doOnError(e -> {
                    log.error("流式对话异常: traceId={}", traceId, e);
                    traceService.endTrace(traceId);
                });
    }

    /**
     * 执行 RAG 检索，获取相关上下文
     * <p>
     * 这是RAG的核心步骤：
     * 1. 将用户查询转换为向量
     * 2. 在向量数据库中搜索相似内容
     * 3. 对结果重排序
     * 4. 返回相关文本作为上下文
     */
    private String performRAG(String query, List<ChatResponse.SourceDocument> sources, String traceId) { // 私有方法：执行RAG检索

        try { // 开始异常处理块

            log.info("========== 开始RAG检索 ==========");
            log.info("查询文本: {}", query);

            long embedStartTime = System.currentTimeMillis();
            List<Float> queryVector = embeddingService.embed(query); // 【关键】调用Embedding服务，将查询文本转换为向量
            long embedElapsed = System.currentTimeMillis() - embedStartTime;

            log.info("Embedding生成完成: 向量维度={}, 耗时={}ms", queryVector.size(), embedElapsed);

            long retrieveStartTime = System.currentTimeMillis();
            List<MultiRetriever.RetrievalResult> results = multiRetriever.retrieve(query, queryVector); // 执行多路检索：传入查询文本和向量
            long retrieveElapsed = System.currentTimeMillis() - retrieveStartTime;

            log.info("多路检索完成: 返回{}个结果, 耗时={}ms", results.size(), retrieveElapsed);

            if (!results.isEmpty()) { // 如果检索结果不为空
                log.info("开始重排序: 输入{}个结果", results.size());
                long rerankStartTime = System.currentTimeMillis();
                results = reranker.rerank(query, results); // 对结果进行重排序：提高相关性
                long rerankElapsed = System.currentTimeMillis() - rerankStartTime;
                log.info("重排序完成: 输出{}个结果, 耗时={}ms", results.size(), rerankElapsed);

                log.info("检索结果分数分布:");
                for (int i = 0; i < results.size(); i++) {
                    MultiRetriever.RetrievalResult r = results.get(i);
                    log.info("  [{}] 文档ID={}, 分数={}, 来源={}, 内容长度={}字符",
                            i + 1, r.getDocumentId(), r.getScore(), r.getSource(),
                            r.getContent() != null ? r.getContent().length() : 0);
                }
            }

            for (MultiRetriever.RetrievalResult r : results) { // 遍历所有检索结果
                sources.add(ChatResponse.SourceDocument.builder() // 构建来源文档对象
                        .documentId(r.getDocumentId()) // 设置文档ID
                        .content(r.getContent()) // 设置文档内容
                        .score(r.getScore()) // 设置相似度分数
                        .build()); // 构建最终对象
            }

            traceService.addSpan(traceId, "rag_results", // 添加追踪span：RAG结果阶段
                    Map.of("count", results.size())); // 记录检索结果数量

            String context = results.stream()
                    .map(MultiRetriever.RetrievalResult::getContent)
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("RAG上下文构建完成: 总长度={}字符", context.length());
            log.info("================================");

            return context;
//            return results.stream() // 将结果流式处理
//                    .map(MultiRetriever.RetrievalResult::getContent) // 提取每个结果的内容
//                    .collect(Collectors.joining("\n\n---\n\n")); // 用分隔符连接所有内容

        } catch (Exception e) { // 捕获所有异常

            log.warn("========== RAG检索失败 ==========");
            log.warn("异常类型: {}", e.getClass().getSimpleName());
            log.warn("异常消息: {}", e.getMessage());
            log.warn("降级策略: 使用无上下文模式");
            log.warn("====================================");  // 记录警告日志：降级处理

            return ""; // 返回空字符串：不使用RAG上下文
        }
    }

    /**
     * 根据模式分发到对应的 Agent
     */
    private ChatResponse dispatchToAgent(AgentMode mode, ChatRequest request,
                                          String ragContext, String traceId) {

        log.info("Agent分发决策: 模式={}, 消息长度={}, RAG上下文长度={}",
                mode, request.getMessage().length(), ragContext.length());

        return switch (mode) {
            case REACT -> {
                log.info("选择ReAct Agent: 适合需要推理和工具调用的场景");

                ReActAgent.ReActResult result = reActAgent.execute(
                        request.getMessage(), ragContext, request.getTools(), traceId);

                log.info("ReAct执行完成: 迭代次数={}, 使用工具数={}",
                        result.getIterations(), result.getUsedTools().size());
                yield ChatResponse.builder()
                        .reply(result.getFinalAnswer())
                        .thinkingSteps(result.getThinkingSteps())
                        .usedTools(result.getUsedTools())
                        .build();
            }
            case PLANNER -> {
                log.info("选择Planner Agent: 适合复杂多步任务规划");
                PlannerAgent.PlanResult result = plannerAgent.execute(
                        request.getMessage(), ragContext, request.getTools(), traceId);
                log.info("Planner执行完成: 计划步骤数={}", result.getThinkingSteps().size());
                yield ChatResponse.builder()
                        .reply(result.getFinalAnswer())
                        .thinkingSteps(result.getThinkingSteps())
                        .usedTools(result.getUsedTools())
                        .build();
            }
            case REFLECTION -> {
                log.info("选择Reflection Agent: 适合需要自我反思优化的场景");
                ReflectionAgent.ReflectionResult result = reflectionAgent.execute(
                        request.getMessage(), ragContext, traceId);
                log.info("Reflection执行完成: 反思次数={}", result.getThinkingSteps().size());
                yield ChatResponse.builder()
                        .reply(result.getFinalAnswer())
                        .thinkingSteps(result.getThinkingSteps())
                        .build();
            }
            case DIRECT -> {
                log.info("选择Direct模式: 简单对话，无需Agent编排");
                String reply = directChat(request.getMessage(), ragContext);
                log.info("Direct对话完成: 回复长度={}字符", reply.length());
                yield ChatResponse.builder()
                        .reply(reply)
                        .build();
            }
        };
    }

    /**
     * 直接对话模式（不经过 Agent 编排）
     */
    private String directChat(String message, String context) {
        String promptText = buildDirectPrompt(message, context);
        log.info("Direct模式调用LLM: Prompt长度={}字符, 包含上下文={}",
                promptText.length(), context != null && !context.isBlank());

        long llmStartTime = System.currentTimeMillis();
        String reply = modelRouter.call(new Prompt(promptText), null)
                .getResult().getOutput().getText();
        long llmElapsed = System.currentTimeMillis() - llmStartTime;

        log.info("LLM调用完成: 回复长度={}字符, 耗时={}ms", reply.length(), llmElapsed);

        return reply;
    }

    private String buildDirectPrompt(String message, String context) {
        if (context != null && !context.isBlank()) {
            return String.format("""
                    你是一个专业的 AI 助手。请基于以下参考信息回答用户的问题。
                    
                    参考信息：
                    %s
                    
                    用户问题：%s
                    """, context, message);
        }
        return "你是一个专业的 AI 助手。请回答用户的问题。\n\n用户问题：" + message;
    }

    private String resolveConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return conversationId;
    }
}
