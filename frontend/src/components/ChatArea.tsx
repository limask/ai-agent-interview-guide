/**
 * 聊天区域组件
 * 包含：配置卡片、消息列表、输入框
 */

import React, { useEffect } from 'react';
// Ant Design 组件
import { Card, Select, Switch, Space, Typography, Divider } from 'antd';
// Ant Design 图标
import { RobotOutlined } from '@ant-design/icons';
// 全局状态
import { useAppStore } from '@/store/appStore';
// 类型定义
import { AgentMode } from '@/types';
// 子组件
import MessageList from './MessageList';
import ChatInput from './ChatInput';

// 解构 Typography 的 Title 和 Text 组件
const { Title, Text } = Typography;

/**
 * 聊天区域组件
 */
const ChatArea: React.FC = () => {
    // 从 Store 中获取状态和方法
    const currentConversationId = useAppStore((state) => state.currentConversationId);
    const conversations = useAppStore((state) => state.conversations);
    const agentMode = useAppStore((state) => state.agentMode);
    const enableRag = useAppStore((state) => state.enableRag);
    const setAgentMode = useAppStore((state) => state.setAgentMode);
    const setEnableRag = useAppStore((state) => state.setEnableRag);
    const createConversation = useAppStore((state) => state.createConversation);

    // 查找当前会话
    const currentConversation = conversations.find(
        (conv) => conv.id === currentConversationId
    );

    // 组件挂载时，如果没有当前会话则创建一个
    useEffect(() => {
        if (!currentConversationId) {
            createConversation();
        }
    }, [currentConversationId, createConversation]);

    // 如果还没有会话，显示欢迎界面
    if (!currentConversationId || !currentConversation) {
        return (
            <div style={{ padding: 40, textAlign: 'center' }}>
                <Title level={3}>欢迎使用 AI Agent Platform</Title>
                <Text type="secondary">点击左侧"新建对话"开始交流</Text>
            </div>
        );
    }

    return (
        // 聊天区域容器，使用 flex 布局
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            {/* 顶部配置卡片 */}
            <Card
                size="small"
                style={{
                    margin: 16,
                    marginBottom: 0,
                    borderRadius: 8,
                }}
            >
                <Space wrap>
                    {/* Agent 模式选择器 */}
                    <Space>
                        <Text strong>Agent 模式:</Text>
                        <Select
                            value={agentMode}
                            onChange={setAgentMode}
                            style={{ width: 150 }}
                            options={[
                                { value: AgentMode.REACT, label: 'ReAct' },
                                { value: AgentMode.PLANNER, label: 'Planner' },
                                { value: AgentMode.REFLECTION, label: 'Reflection' },
                            ]}
                        />
                    </Space>

                    {/* 分隔线 */}
                    <Divider type="vertical" />

                    {/* RAG 检索开关 */}
                    <Space>
                        <Text strong>RAG 检索:</Text>
                        <Switch checked={enableRag} onChange={setEnableRag} />
                    </Space>

                    <Divider type="vertical" />

                    {/* 消息数量统计 */}
                    <Space>
                        <RobotOutlined />
                        <Text type="secondary">{currentConversation.messages.length} 条消息</Text>
                    </Space>
                </Space>
            </Card>

            {/* 消息列表区域 */}
            <MessageList messages={currentConversation.messages} />

            {/* 底部输入框区域 */}
            <div style={{ padding: '16px', borderTop: '1px solid #f0f0f0' }}>
                <ChatInput conversationId={currentConversationId} />
            </div>
        </div>
    );
};

export default ChatArea;
