/**
 * 侧边栏组件
 * 包含：标题、新建按钮、导航菜单、会话列表
 */

import React from 'react';
// Ant Design 组件
import { Menu, Button, Typography } from 'antd';
// Ant Design 图标
import {
    MessageOutlined,
    FileTextOutlined,
    PlusOutlined,
} from '@ant-design/icons';
// 全局状态
import { useAppStore } from '@/store/appStore';
// 子组件：会话列表
import ConversationList from './ConversationList';

// 解构 Typography 的 Title 组件
const { Title } = Typography;

/**
 * 侧边栏组件的属性接口
 */
interface SidebarProps {
    // 当前激活的标签
    activeTab: 'chat' | 'documents';
    // 标签切换回调函数
    onTabChange: (tab: 'chat' | 'documents') => void;
}

/**
 * 侧边栏组件
 */
const Sidebar: React.FC<SidebarProps> = ({ activeTab, onTabChange }) => {
    // 从 Store 中获取创建会话的方法
    const createConversation = useAppStore((state) => state.createConversation);

    /**
     * 处理新建对话按钮点击
     * 1. 创建新会话
     * 2. 切换到聊天标签
     */
    const handleNewChat = () => {
        createConversation();
        onTabChange('chat');
    };

    return (
        // 侧边栏容器，使用 flex 布局
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            {/* 顶部区域：标题和新建按钮 */}
            <div style={{ padding: '16px', borderBottom: '1px solid #f0f0f0' }}>
                {/* 应用标题 */}
                <Title level={4} style={{ marginBottom: 16, textAlign: 'center' }}>
                    AI Agent Platform
                </Title>
                {/* 新建对话按钮，block 表示占满宽度 */}
                <Button type="primary" icon={<PlusOutlined />} block onClick={handleNewChat}>
                    新建对话
                </Button>
            </div>

            {/* 导航菜单 */}
            <Menu
                mode="inline"
                selectedKeys={[activeTab]}
                style={{ borderBottom: '1px solid #f0f0f0' }}
                items={[
                    {
                        key: 'chat',
                        icon: <MessageOutlined />,
                        label: '智能对话',
                        onClick: () => onTabChange('chat'),
                    },
                    {
                        key: 'documents',
                        icon: <FileTextOutlined />,
                        label: '文档管理',
                        onClick: () => onTabChange('documents'),
                    },
                ]}
            />

            {/* 只有在聊天标签时才显示会话列表 */}
            {activeTab === 'chat' && (
                // 会话列表区域，可滚动
                <div style={{ flex: 1, overflow: 'auto' }}>
                    <ConversationList />
                </div>
            )}
        </div>
    );
};

export default Sidebar;
