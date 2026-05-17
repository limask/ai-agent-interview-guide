package com.agent.platform.service.intent;

import com.agent.platform.infrastructure.llm.ModelRouter;
import com.agent.platform.model.enums.AgentMode;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意图识别服务
 * <p>
 * 分析用户输入的意图，决定使用哪种 Agent 模式和工具集合。
 * 支持基于规则的快速判断和基于 LLM 的深度理解。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRecognizer {

    private final ModelRouter modelRouter;

    private static final String INTENT_PROMPT_TEMPLATE = """
            你是一个意图识别引擎。请分析用户的输入，判断用户的意图类别。
            
            意图类别：
            1. SIMPLE_CHAT - 简单闲聊、问候、闲谈
            2. KNOWLEDGE_QA - 知识问答，需要检索知识库
            3. TOOL_USE - 需要使用工具（搜索、计算、数据库查询等）
            4. COMPLEX_TASK - 复杂任务，需要多步推理和规划
            5. CODE_RELATED - 代码相关问题
            
            用户输入: %s
            
            请仅返回意图类别名称（如 SIMPLE_CHAT），不要返回其他内容。
            """;

    /**
     * 识别用户意图
     *
     * @param userInput 用户输入
     * @return 意图识别结果
     */
    public IntentResult recognize(String userInput) {

        log.info("========== 开始意图识别 ==========");
        log.info("输入文本: {}", truncate(userInput, 100));
        log.info("输入长度: {} 字符", userInput.length());

        long ruleStartTime = System.currentTimeMillis();
        IntentResult ruleResult = ruleBasedRecognize(userInput);
        long ruleElapsed = System.currentTimeMillis() - ruleStartTime;

        if (ruleResult != null) {
            log.info("规则匹配成功: 耗时={}ms", ruleElapsed);
            log.info("命中意图: {}", ruleResult.getIntentType());
            log.info("置信度: {}", ruleResult.getConfidence());
            log.info("Agent模式: {}", ruleResult.getAgentMode());
            log.info("需要RAG: {}", ruleResult.isNeedsRag());
            log.info("================================");
            return ruleResult;
        }
        log.info("规则未匹配: 耗时={}ms, 转入LLM识别", ruleElapsed);
        return llmBasedRecognize(userInput);
    }

    /**
     * 基于规则的快速意图判断
     */
    private IntentResult ruleBasedRecognize(String input) {
        String lower = input.toLowerCase().trim();

        if (lower.matches("^(你好|hi|hello|嗨|早上好|晚上好|hey).*")) {
            log.debug("规则命中: SIMPLE_CHAT (问候语匹配)");
            return IntentResult.builder()
                    .intentType("SIMPLE_CHAT")
                    .agentMode(AgentMode.DIRECT)
                    .confidence(0.95)
                    .needsRag(false)
                    .suggestedTools(List.of())
                    .build();
        }

        if (lower.contains("计算") || lower.matches(".*\\d+[+\\-*/]\\d+.*")) {
            log.debug("规则命中: TOOL_USE (计算相关)");
            return IntentResult.builder()
                    .intentType("TOOL_USE")
                    .agentMode(AgentMode.REACT)
                    .confidence(0.9)
                    .needsRag(false)
                    .suggestedTools(List.of("calculator"))
                    .build();
        }

        if (lower.contains("搜索") || lower.contains("查找") || lower.contains("最新")) {
            log.debug("规则命中: TOOL_USE (搜索相关)");
            return IntentResult.builder()
                    .intentType("TOOL_USE")
                    .agentMode(AgentMode.REACT)
                    .confidence(0.85)
                    .needsRag(false)
                    .suggestedTools(List.of("search"))
                    .build();
        }

        if (lower.contains("查询数据") || lower.contains("sql") || lower.contains("数据库")) {
            log.debug("规则命中: TOOL_USE (数据库查询)");
            return IntentResult.builder()
                    .intentType("TOOL_USE")
                    .agentMode(AgentMode.REACT)
                    .confidence(0.85)
                    .needsRag(false)
                    .suggestedTools(List.of("database_query"))
                    .build();
        }

        if (lower.contains("文档") || lower.contains("上传") || lower.contains("总结") ||
                lower.contains("根据") || lower.contains("知识库")) {
            log.debug("规则命中: KNOWLEDGE_QA (知识库问答)");
            return IntentResult.builder()
                    .intentType("KNOWLEDGE_QA")
                    .agentMode(AgentMode.REACT)
                    .confidence(0.9)
                    .needsRag(true)
                    .suggestedTools(List.of())
                    .build();
        }
        log.debug("规则未命中任何模式");
        return null;
    }

    /**
     * 基于 LLM 的意图识别（兜底方案）
     */
    private IntentResult llmBasedRecognize(String input) {
        try {
            log.info("开始LLM意图识别");

            String promptText = String.format(INTENT_PROMPT_TEMPLATE, input);

            log.debug("LLM Prompt长度: {} 字符", promptText.length());

            long llmStartTime = System.currentTimeMillis();
            String intentType = modelRouter.call(new Prompt(promptText), "gpt-4o-mini")
                    .getResult()
                    .getOutput()
                    .getText()
                    .trim()
                    .toUpperCase();
            long llmElapsed = System.currentTimeMillis() - llmStartTime;

            log.info("LLM返回意图: {}", intentType);
            log.info("LLM调用耗时: {}ms", llmElapsed);

            AgentMode mode = mapToAgentMode(intentType);
            boolean needsRag = "KNOWLEDGE_QA".equals(intentType);

            log.info("LLM意图映射: type={}, mode={}, needsRag={}", intentType, mode, needsRag);
            log.info("================================");

            return IntentResult.builder()
                    .intentType(intentType)
                    .agentMode(mode)
                    .confidence(0.8)
                    .needsRag(needsRag)
                    .suggestedTools(List.of())
                    .build();
        } catch (Exception e) {
            log.error("========== LLM意图识别失败 ==========");
            log.error("异常类型: {}", e.getClass().getSimpleName());
            log.error("异常消息: {}", e.getMessage());
            log.error("降级策略: 使用默认意图 KNOWLEDGE_QA");
            log.error("====================================");

            return IntentResult.builder()
                    .intentType("KNOWLEDGE_QA")
                    .agentMode(AgentMode.REACT)
                    .confidence(0.5)
                    .needsRag(true)
                    .suggestedTools(List.of())
                    .build();
        }
    }

    private AgentMode mapToAgentMode(String intentType) {
        return switch (intentType) {
            case "SIMPLE_CHAT" -> AgentMode.DIRECT;
            case "COMPLEX_TASK" -> AgentMode.PLANNER;
            default -> AgentMode.REACT;
        };
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    @Data
    @Builder
    public static class IntentResult {
        private String intentType;
        private AgentMode agentMode;
        private double confidence;
        private boolean needsRag;
        private List<String> suggestedTools;
    }
}
