/**
 * 会话列表组件
 * 展示所有历史会话，支持点击切换和删除
 */

import React from 'react';
// Ant Design 组件
import { List, Typography, Empty } from 'antd';
// Ant Design 图标
import { DeleteOutlined } from '@ant-design/icons';
// 全局状态
import { useAppStore } from '@/store/appStore';
// 日期格式化库
import dayjs from 'dayjs';

// 解构 Typography 的 Text 组件
const { Text } = Typography;

/**
 * 会话列表组件
 */
const ConversationList: React.FC = () => {
    // 从 Store 中获取状态和方法
    const conversations = useAppStore((state) => state.conversations);
    const currentConversationId = useAppStore((state) => state.currentConversationId);
    const setCurrentConversation = useAppStore((state) => state.setCurrentConversation);
    const deleteConversation = useAppStore((state) => state.deleteConversation);

    // 如果没有会话，显示空状态
    if (conversations.length === 0) {
        return (
            <div style={{ padding: '40px 16px' }}>
                <Empty description="暂无对话记录" />
            </div>
        );
    }

    return (
        // 使用 Ant Design 的 List 组件渲染会话列表
        <List
            dataSource={conversations}
            renderItem={(conv) => (
                <List.Item
                    style={{
                        cursor: 'pointer',
                        // 当前选中的会话高亮显示
                        background: currentConversationId === conv.id ? '#e6f7ff' : 'transparent',
                        padding: '12px 16px',
                        // 添加过渡动画
                        transition: 'all 0.3s',
                    }}
                    // 点击整个项切换会话
                    onClick={() => setCurrentConversation(conv.id)}
                    // 右侧操作按钮
                    actions={[
                        <DeleteOutlined
                            key="delete"
                            onClick={(e) => {
                                // 阻止事件冒泡，避免触发父元素的 onClick
                                e.stopPropagation();
                                deleteConversation(conv.id);
                            }}
                            style={{ color: '#ff4d4f' }}
                        />,
                    ]}
                >
                    {/* 列表项内容 */}
                    <List.Item.Meta
                        title={
                            // 会话标题，超出部分省略
                            <Text strong ellipsis style={{ maxWidth: 180 }}>
                                {conv.title}
                            </Text>
                        }
                        description={
                            // 描述信息：更新时间和消息数量
                            <Text type="secondary" style={{ fontSize: 12 }}>
                                {dayjs(conv.updatedAt).format('MM-DD HH:mm')} · {conv.messages.length} 条消息
                            </Text>
                        }
                    />
                </List.Item>
            )}
        />
    );
};

export default ConversationList;
