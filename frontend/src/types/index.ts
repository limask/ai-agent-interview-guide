/**
 * 类型定义文件
 * 定义了所有与后端 API 交互的数据结构
 */

/**
 * Agent 工作模式枚举
 * REACT: 推理-行动循环模式
 * PLANNER: 规划模式
 * REFLECTION: 反思模式
 */
export enum AgentMode {
    REACT = 'REACT',
    PLANNER = 'PLANNER',
    REFLECTION = 'REFLECTION',
}

/**
 * 聊天请求接口
 * 对应后端的 ChatRequest DTO
 */
export interface ChatRequest {
    // 会话 ID（可选，为空则创建新会话）
    conversationId?: string;
    // 用户消息内容（必填）
    message: string;
    // Agent 工作模式（可选）
    mode?: AgentMode;
    // 是否启用 RAG 检索增强生成
    enableRag?: boolean;
    // 指定使用的工具列表
    tools?: string[];
    // 模型参数覆盖配置
    modelOptions?: ModelOptions;
}

/**
 * 模型参数配置接口
 */
export interface ModelOptions {
    // 模型名称
    model?: string;
    // 温度参数：控制随机性（0-1）
    temperature?: number;
    // 最大生成 token 数
    maxTokens?: number;
}

/**
 * 聊天响应接口
 * 对应后端的 ChatResponse DTO
 */
export interface ChatResponse {
    // 会话 ID
    conversationId: string;
    // AI 回复内容
    reply: string;
    // 思考过程步骤列表
    thinkingSteps?: ThinkingStep[];
    // 使用的工具列表
    usedTools?: string[];
    // RAG 检索的来源文档
    sources?: SourceDocument[];
    // 使用的模型名称
    model?: string;
    // Token 使用统计
    tokenUsage?: TokenUsage;
    // 追踪 ID（用于调试）
    traceId?: string;
}

/**
 * 思考步骤接口
 * 展示 Agent 的推理过程
 */
export interface ThinkingStep {
    // 步骤序号
    step: number;
    // 思考内容
    thought: string;
    // 执行的动作
    action: string;
    // 观察到的结果
    observation: string;
}

/**
 * 来源文档接口
 * RAG 检索返回的相关文档片段
 */
export interface SourceDocument {
    // 文档 ID
    documentId: string;
    // 文档内容片段
    content: string;
    // 相似度分数（0-1）
    score: number;
}

/**
 * Token 使用统计接口
 */
export interface TokenUsage {
    // 提示词使用的 token 数
    promptTokens: number;
    // 完成内容使用的 token 数
    completionTokens: number;
    // 总 token 数
    totalTokens: number;
}

/**
 * 消息接口（前端内部使用）
 * 用于在界面上展示单条消息
 */
export interface Message {
    // 消息唯一 ID
    id: string;
    // 消息角色：user（用户）或 assistant（AI）
    role: 'user' | 'assistant';
    // 消息内容
    content: string;
    // 时间戳
    timestamp: Date;
    // 思考过程（仅 AI 消息）
    thinkingSteps?: ThinkingStep[];
    // 使用的工具（仅 AI 消息）
    usedTools?: string[];
    // 检索来源（仅 AI 消息）
    sources?: SourceDocument[];
    // 使用的模型（仅 AI 消息）
    model?: string;
    // Token 用量（仅 AI 消息）
    tokenUsage?: TokenUsage;
}

/**
 * 会话接口
 * 一个会话包含多条消息
 */
export interface Conversation {
    // 会话 ID
    id: string;
    // 会话标题（显示在侧边栏）
    title: string;
    // 消息列表
    messages: Message[];
    // 创建时间
    createdAt: Date;
    // 最后更新时间
    updatedAt: Date;
}

/**
 * 文档上传响应接口
 * 对应后端的 DocumentUploadResponse DTO
 */
export interface DocumentUploadResponse {
    // 文档 ID
    documentId: string;
    // 原始文件名
    fileName: string;
    // 文档切片数量
    chunkCount: number;
    // 处理状态
    status: string;
    // 处理耗时（毫秒）
    processingTimeMs: number;
}

/**
 * API 统一响应格式
 * 后端 Result 类的对应结构
 */
export interface ApiResult<T> {
    // 状态码（200 表示成功）
    code: number;
    // 响应消息
    message: string;
    // 响应数据
    data: T;
    // 追踪 ID
    traceId?: string;
}
