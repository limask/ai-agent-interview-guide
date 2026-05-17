/**
 * 全局状态管理 Store
 * 使用 Zustand 库实现轻量级状态管理
 * 支持持久化存储（localStorage）
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Conversation, Message, AgentMode } from '@/types';
import { v4 as uuidv4 } from 'uuid';

/**
 * 应用状态接口
 * 定义了所有需要全局管理的状态
 */
interface AppState {
    // 会话列表
    conversations: Conversation[];
    // 当前选中的会话 ID
    currentConversationId: string | null;
    // 当前 Agent 工作模式
    agentMode: AgentMode;
    // 是否启用 RAG 检索
    enableRag: boolean;
    // 加载状态（发送消息时显示 loading）
    loading: boolean;

    // 添加消息到指定会话
    addMessage: (conversationId: string, message: Message) => void;
    // 更新消息内容
    updateMessage: (conversationId: string, messageId: string, content: string) => void;
    // 创建新会话
    createConversation: () => string;
    // 切换当前会话
    setCurrentConversation: (id: string) => void;
    // 设置 Agent 模式
    setAgentMode: (mode: AgentMode) => void;
    // 设置 RAG 开关
    setEnableRag: (enable: boolean) => void;
    // 设置加载状态
    setLoading: (loading: boolean) => void;
    // 删除会话
    deleteConversation: (id: string) => void;
    // 更新会话标题
    updateConversationTitle: (id: string, title: string) => void;
}

/**
 * 创建 Zustand Store
 * 使用 persist 中间件实现状态持久化
 */
export const useAppStore = create<AppState>()(
    persist(
        // Store 的实现函数
        (set, get) => ({
            // === 初始状态 ===
            conversations: [],
            currentConversationId: null,
            agentMode: AgentMode.REACT,
            enableRag: true,
            loading: false,

            // === Actions ===

            /**
             * 添加消息到指定会话
             * @param conversationId 会话 ID
             * @param message 要添加的消息
             */
            addMessage: (conversationId, message) => {
                // set 函数用于更新状态
                set((state) => {
                    // 遍历所有会话，找到目标会话并添加消息
                    const conversations = state.conversations.map((conv) => {
                        if (conv.id === conversationId) {
                            return {
                                ...conv,
                                messages: [...conv.messages, message],
                                updatedAt: new Date(),
                            };
                        }
                        return conv;
                    });
                    return { conversations };
                });
            },

            /**
             * 更新消息内容
             * @param conversationId 会话 ID
             * @param messageId 消息 ID
             * @param content 新的消息内容
             */
            updateMessage: (conversationId, messageId, content) => {
                set((state) => {
                    const conversations = state.conversations.map((conv) => {
                        if (conv.id === conversationId) {
                            const updatedMessages = conv.messages.map((msg) => {
                                if (msg.id === messageId) {
                                    return {
                                        ...msg,
                                        content: content,
                                    };
                                }
                                return msg;
                            });
                            return {
                                ...conv,
                                messages: updatedMessages,
                                updatedAt: new Date(),
                            };
                        }
                        return conv;
                    });
                    return { conversations };
                });
            },

            /**
             * 创建新会话
             * @returns 新会话的 ID
             */
            createConversation: () => {
                // 生成唯一 ID
                const id = uuidv4();
                // 创建新会话对象
                const newConversation: Conversation = {
                    id,
                    title: '新对话',
                    messages: [],
                    createdAt: new Date(),
                    updatedAt: new Date(),
                };
                // 更新状态：添加到列表并设为当前会话
                set((state) => ({
                    conversations: [newConversation, ...state.conversations],
                    currentConversationId: id,
                }));
                return id;
            },

            /**
             * 切换当前会话
             * @param id 要切换到的会话 ID
             */
            setCurrentConversation: (id) => {
                set({ currentConversationId: id });
            },

            /**
             * 设置 Agent 工作模式
             * @param mode 新的工作模式
             */
            setAgentMode: (mode) => {
                set({ agentMode: mode });
            },

            /**
             * 设置 RAG 检索开关
             * @param enable 是否启用
             */
            setEnableRag: (enable) => {
                set({ enableRag: enable });
            },

            /**
             * 设置加载状态
             * @param loading 是否加载中
             */
            setLoading: (loading) => {
                set({ loading });
            },

            /**
             * 删除指定会话
             * @param id 要删除的会话 ID
             */
            deleteConversation: (id) => {
                set((state) => {
                    // 过滤掉要删除的会话
                    const conversations = state.conversations.filter((conv) => conv.id !== id);
                    // 如果删除的是当前会话，切换到第一个会话
                    const currentConversationId =
                        state.currentConversationId === id
                            ? conversations.length > 0
                                ? conversations[0].id
                                : null
                            : state.currentConversationId;
                    return { conversations, currentConversationId };
                });
            },

            /**
             * 更新会话标题
             * @param id 会话 ID
             * @param title 新标题
             */
            updateConversationTitle: (id, title) => {
                set((state) => ({
                    conversations: state.conversations.map((conv) =>
                        conv.id === id ? { ...conv, title } : conv
                    ),
                }));
            },
        }),
        // 持久化配置
        {
            // localStorage 中的键名
            name: 'ai-agent-storage',
            // 只持久化部分状态（排除 loading 等临时状态）
            partialize: (state) => ({
                conversations: state.conversations,
                currentConversationId: state.currentConversationId,
                agentMode: state.agentMode,
                enableRag: state.enableRag,
            }),
        }
    )
);
