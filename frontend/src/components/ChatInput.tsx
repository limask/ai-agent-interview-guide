/**
 * 聊天输入框组件
 * 支持多行文本输入、发送消息、停止生成
 */

import React, { useState, useRef } from 'react';
// Ant Design 组件
import { Input, Button, Space, message, Tooltip } from 'antd';
// Ant Design 图标
import { SendOutlined, StopOutlined } from '@ant-design/icons';
// 全局状态
import { useAppStore } from '@/store/appStore';
// API 服务
import { apiService } from '@/services/api';
// 类型定义
import { ChatRequest, Message } from '@/types';
// UUID 生成器
import { v4 as uuidv4 } from 'uuid';

// 解构 TextArea 组件
const { TextArea } = Input;

/**
 * 聊天输入框组件的属性接口
 */
interface ChatInputProps {
    // 当前会话 ID
    conversationId: string;
}

/**
 * 聊天输入框组件
 */
const ChatInput: React.FC<ChatInputProps> = ({ conversationId }) => {
    // 输入框的值
    const [inputValue, setInputValue] = useState('');
    // 是否正在流式生成
    const [isStreaming, setIsStreaming] = useState(false);
    // 用于中止请求的控制器
    const abortControllerRef = useRef<AbortController | null>(null);

    // 从 Store 中获取方法
    const addMessage = useAppStore((state) => state.addMessage);
    const updateMessage = useAppStore((state) => state.updateMessage);
    const agentMode = useAppStore((state) => state.agentMode);
    const enableRag = useAppStore((state) => state.enableRag);
    const loading = useAppStore((state) => state.loading);
    const setLoading = useAppStore((state) => state.setLoading);

    /**
     * 处理发送消息
     */
    const handleSubmit = async () => {
        // 验证：不能为空且不在加载中
        if (!inputValue.trim() || loading) {
            return;
        }

        // 创建用户消息对象
        const userMessage: Message = {
            id: uuidv4(),
            role: 'user',
            content: inputValue.trim(),
            timestamp: new Date(),
        };

        // 添加到会话
        addMessage(conversationId, userMessage);
        // 清空输入框
        setInputValue('');
        // 设置加载状态
        setLoading(true);

        try {
            // 构建请求参数
            const request: ChatRequest = {
                conversationId,
                message: userMessage.content,
                mode: agentMode,
                enableRag,
            };

            // 调用流式接口
            const response = await apiService.chatStream(request);

            // 获取读取器
            const reader = response.body?.getReader();
            if (!reader) {
                throw new Error('无法读取响应流');
            }

            // 创建文本解码器
            const decoder = new TextDecoder();
            // 累积的 AI 回复内容
            let assistantMessageContent = '';
            // 是否完成
            let isDone = false;

            // 创建 AI 消息的 ID 和对象
            const assistantMessageId = uuidv4();
            const assistantMessage: Message = {
                id: assistantMessageId,
                role: 'assistant',
                content: '',
                timestamp: new Date(),
            };

            // 先添加一个空的 AI 消息
            addMessage(conversationId, assistantMessage);

            // 循环读取流数据
            while (!isDone) {
                const { done, value } = await reader.read();
                isDone = done;

                if (done) {
                    break;
                }

                // 解码二进制数据为文本
                const chunk = decoder.decode(value, { stream: true });

                // 按行分割
                const lines = chunk.split('\n');

                // 处理每一行
                for (const line of lines) {
                    const trimmedLine = line.trim();

                    // SSE 格式：data: 开头
                    if (trimmedLine.startsWith('data:')) {
                        // 正确去除 data: 前缀
                        let data = trimmedLine.substring(5).trim();

                        // 结束标记
                        if (data === '[DONE]') {
                            isDone = true;
                            break;
                        }

                        // 累积内容
                        if (data) {
                            assistantMessageContent += data;

                            // 更新消息内容
                            updateMessage(conversationId, assistantMessageId, assistantMessageContent);
                        }
                    }
                }
            }
        } catch (error: any) {
            // 错误处理
            console.error('Chat error:', error);
            message.error(error.message || '发送消息失败');
        } finally {
            // 清理状态
            setLoading(false);
            setIsStreaming(false);
        }
    };

    /**
     * 处理停止生成
     */
    const handleStop = () => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
            setLoading(false);
            setIsStreaming(false);
            message.info('已停止生成');
        }
    };

    /**
     * 处理键盘事件
     * Enter 发送，Shift+Enter 换行
     */
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit();
        }
    };

    return (
        // 垂直方向的紧凑布局
        <Space.Compact style={{ width: '100%' }} direction="vertical">
            {/* 多行文本输入框 */}
            <TextArea
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
                autoSize={{ minRows: 2, maxRows: 6 }}
                disabled={loading}
            />
            {/* 按钮区域 */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                {loading ? (
                    // 加载中显示停止按钮
                    <Tooltip title="停止生成">
                        <Button danger icon={<StopOutlined />} onClick={handleStop}>
                            停止
                        </Button>
                    </Tooltip>
                ) : (
                    // 否则显示发送按钮
                    <Button
                        type="primary"
                        icon={<SendOutlined />}
                        onClick={handleSubmit}
                        disabled={!inputValue.trim()}
                    >
                        发送
                    </Button>
                )}
            </div>
        </Space.Compact>
    );
};

export default ChatInput;
