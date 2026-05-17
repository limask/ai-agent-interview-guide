package com.agent.platform.infrastructure.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 模型路由器
 * <p>
 * 职责：
 * <ul>
 *   <li>根据任务复杂度选择合适的模型（主力模型 / 快速模型）</li>
 *   <li>结合熔断器实现自动降级</li>
 *   <li>统一模型调用入口，屏蔽底层差异</li>
 * </ul>
 */
@Slf4j
@Component
public class ModelRouter {

    private final OpenAiChatModel primaryModel;
    private final OpenAiChatModel fallbackModel;
    private final CircuitBreaker circuitBreaker;

    public ModelRouter(
            @Qualifier("primaryChatModel") OpenAiChatModel primaryModel,
            @Qualifier("fallbackChatModel") OpenAiChatModel fallbackModel,
            CircuitBreaker circuitBreaker) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 同步调用模型，自带熔断降级
     *
     * @param prompt    提示词
     * @param forceModel 指定模型名称（为 null 时自动路由）
     * @return 模型响应
     */
    public ChatResponse call(Prompt prompt, String forceModel) {
        ChatModel selectedModel = selectModel(forceModel);

        try {
            ChatResponse response = selectedModel.call(prompt);
            circuitBreaker.recordSuccess();
            log.debug("模型调用成功，使用模型: {}", getModelName(selectedModel));
            return response;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("主模型调用失败，尝试降级: {}", e.getMessage());
            return callWithFallback(prompt);
        }
    }

    /**
     * 流式调用模型，自带熔断降级
     */
    public Flux<ChatResponse> stream(Prompt prompt, String forceModel) {
        ChatModel selectedModel = selectModel(forceModel);

        return Flux.defer(() -> {
            try {
                return selectedModel.stream(prompt)
                        .doOnNext(r -> circuitBreaker.recordSuccess())
                        .onErrorResume(e -> {
                            circuitBreaker.recordFailure();
                            log.warn("流式调用主模型失败，降级到备用模型: {}", e.getMessage());
                            return fallbackModel.stream(prompt);
                        });
            } catch (Exception e) {
                circuitBreaker.recordFailure();
                log.warn("流式调用启动失败，降级到备用模型: {}", e.getMessage());
                return fallbackModel.stream(prompt);
            }
        });
    }

    /**
     * 根据条件选择模型
     */
    private ChatModel selectModel(String forceModel) {
        if (forceModel != null) {
            if (forceModel.contains("mini")) {
                return fallbackModel;
            }
            return primaryModel;
        }

        if (!circuitBreaker.allowRequest()) {
            log.info("熔断器开启，自动降级到备用模型");
            return fallbackModel;
        }

        return primaryModel;
    }

    /**
     * 使用备用模型进行降级调用
     * <p>
     * 当主模型调用失败时，此方法作为故障转移机制，
     * 尝试使用备用模型处理请求，提高系统可用性。
     *
     * @param prompt 对话提示对象，包含用户输入和上下文信息
     * @return ChatResponse 模型返回的对话响应
     * @throws RuntimeException 当备用模型也调用失败时抛出，表示所有模型均不可用
     */
    private ChatResponse callWithFallback(Prompt prompt) {
        try {
            log.info("使用备用模型进行降级调用");
            return fallbackModel.call(prompt);
        } catch (Exception fallbackEx) {
            log.error("备用模型也调用失败", fallbackEx);
            throw new RuntimeException("所有模型均不可用", fallbackEx);
        }
    }
    /**
     * 获取模型名称标识
     * <p>
     * 根据传入的ChatModel实例判断是主模型还是备用模型，
     * 返回对应的模型名称字符串，用于日志记录和监控。
     *
     * @param model ChatModel实例，需要识别的模型对象
     * @return String 模型名称标识，格式为"类型 (模型名)"
     */
    private String getModelName(ChatModel model) {
        if (model == primaryModel) {
            return "primary (qwen-turbo)";
        }
        return "fallback (qwen-turbo)";
    }

    //    private String getModelName(ChatModel model) {
//        if (model == primaryModel) {
//            return "primary (gpt-4o)";
//        }
//        return "fallback (gpt-4o-mini)";
//    }


}
