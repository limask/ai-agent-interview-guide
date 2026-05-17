/**
 * 消息列表组件
 * 展示所有消息，支持 Markdown 渲染、思考过程折叠、来源展示
 */

import React, { useRef, useEffect } from 'react';
// Ant Design 组件
import { List, Avatar, Tag, Collapse, Typography, Space } from 'antd';
// Ant Design 图标
import { UserOutlined, RobotOutlined, ToolOutlined, SearchOutlined } from '@ant-design/icons';
// 类型定义
import { Message } from '@/types';
// Markdown 渲染组件
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

// 解构 Typography 的 Text 组件
const { Text ,Paragraph } = Typography;
// 解构 Collapse 的 Panel 组件
const { Panel } = Collapse;

/**
 * 消息列表组件的属性接口
 */
interface MessageListProps {
    // 消息数组
    messages: Message[];
}

/**
 * 消息列表组件
 */
const MessageList: React.FC<MessageListProps> = ({ messages }) => {
    // 用于自动滚动到底部的引用
    const messagesEndRef = useRef<HTMLDivElement>(null);

    // 每次消息更新时，自动滚动到底部
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    // 如果没有消息，显示欢迎提示
    if (messages.length === 0) {
        return (
            <div
                style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexDirection: 'column',
                    gap: 16,
                }}
            >
                <Avatar size={64} icon={<RobotOutlined />} style={{ background: '#1890ff' }} />
                <Text type="secondary" style={{ fontSize: 16 }}>
                    开始与 AI Agent 对话吧！
                </Text>
            </div>
        );
    }

    return (
        // 消息列表容器，可滚动
        <div
            style={{
                flex: 1,
                overflow: 'auto',
                padding: '16px 24px',
            }}
        >
            <List
                dataSource={messages}
                renderItem={(message) => (
                    <List.Item style={{ padding: '16px 0', border: 'none' }}>
                        <List.Item.Meta
                            // 头像：用户绿色，AI 蓝色
                            avatar={
                                <Avatar
                                    icon={message.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                                    style={{
                                        background: message.role === 'user' ? '#52c41a' : '#1890ff',
                                    }}
                                />
                            }
                            // 标题：显示角色和模型名称
                            title={
                                <Text strong style={{ fontSize: 14 }}>
                                    {message.role === 'user' ? '你' : 'AI Assistant'}
                                    {message.model && (
                                        <Tag color="blue" style={{ marginLeft: 8 }}>
                                            {message.model}
                                        </Tag>
                                    )}
                                </Text>
                            }
                            // 描述：消息内容和附加信息
                            description={
                                <div style={{ marginTop: 8 }}>
                                    {/* AI 消息才显示思考过程和来源 */}
                                    {message.role === 'assistant' && (
                                        <>
                                            {/* 思考过程折叠面板 */}
                                            {message.thinkingSteps && message.thinkingSteps.length > 0 && (
                                                <Collapse
                                                    size="small"
                                                    style={{ marginBottom: 12, background: '#fafafa' }}
                                                    items={[
                                                        {
                                                            key: 'thinking',
                                                            label: (
                                                                <Space>
                                                                    <RobotOutlined />
                                                                    <Text type="secondary">思考过程 ({message.thinkingSteps.length} 步)</Text>
                                                                </Space>
                                                            ),
                                                            children: (
                                                                <div style={{ maxHeight: 400, overflow: 'auto' }}>
                                                                    {message.thinkingSteps.map((step, idx) => (
                                                                        <div key={idx} style={{ marginBottom: 12 }}>
                                                                            <Text strong>步骤 {step.step}:</Text>
                                                                            <Paragraph style={{ margin: '4px 0 8px 0' }}>
                                                                                <Text type="secondary">思考: {step.thought}</Text>
                                                                            </Paragraph>
                                                                            {step.action && (
                                                                                <Paragraph style={{ margin: '4px 0 8px 0' }}>
                                                                                    <Space>
                                                                                        <ToolOutlined />
                                                                                        <Text code>{step.action}</Text>
                                                                                    </Space>
                                                                                </Paragraph>
                                                                            )}
                                                                            {step.observation && (
                                                                                <Paragraph style={{ margin: '4px 0 0 0' }}>
                                                                                    <Text type="secondary">观察: {step.observation}</Text>
                                                                                </Paragraph>
                                                                            )}
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            ),
                                                        },
                                                    ]}
                                                />
                                            )}

                                            {/* 检索来源折叠面板 */}
                                            {message.sources && message.sources.length > 0 && (
                                                <Collapse
                                                    size="small"
                                                    style={{ marginBottom: 12, background: '#f6ffed' }}
                                                    items={[
                                                        {
                                                            key: 'sources',
                                                            label: (
                                                                <Space>
                                                                    <SearchOutlined />
                                                                    <Text type="secondary">检索来源 ({message.sources.length} 个文档)</Text>
                                                                </Space>
                                                            ),
                                                            children: (
                                                                <div>
                                                                    {message.sources.map((source, idx) => (
                                                                        <div
                                                                            key={idx}
                                                                            style={{
                                                                                padding: 8,
                                                                                marginBottom: 8,
                                                                                background: '#fff',
                                                                                borderRadius: 4,
                                                                                border: '1px solid #d9f7be',
                                                                            }}
                                                                        >
                                                                            <Text strong style={{ fontSize: 12 }}>
                                                                                文档 {idx + 1} (相似度: {(source.score * 100).toFixed(1)}%)
                                                                            </Text>
                                                                            <Paragraph
                                                                                ellipsis={{ rows: 3, expandable: true }}
                                                                                style={{ margin: '4px 0 0 0', fontSize: 12 }}
                                                                            >
                                                                                {source.content}
                                                                            </Paragraph>
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            ),
                                                        },
                                                    ]}
                                                />
                                            )}
                                        </>
                                    )}

                                    {/* 消息内容：使用 Markdown 渲染 */}
                                    <div
                                        style={{
                                            padding: message.role === 'assistant' ? '12px' : '0',
                                            background: message.role === 'assistant' ? '#fafafa' : 'transparent',
                                            borderRadius: 8,
                                        }}
                                    >
                                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                            {message.content}
                                        </ReactMarkdown>
                                    </div>

                                    {/* Token 用量统计 */}
                                    {message.tokenUsage && (
                                        <div style={{ marginTop: 8 }}>
                                            <Text type="secondary" style={{ fontSize: 12 }}>
                                                Token 使用: 输入 {message.tokenUsage.promptTokens} | 输出{' '}
                                                {message.tokenUsage.completionTokens} | 总计{' '}
                                                {message.tokenUsage.totalTokens}
                                            </Text>
                                        </div>
                                    )}
                                </div>
                            }
                        />
                    </List.Item>
                )}
            />
            {/* 用于自动滚动的锚点元素 */}
            <div ref={messagesEndRef} />
        </div>
    );
};

export default MessageList;
