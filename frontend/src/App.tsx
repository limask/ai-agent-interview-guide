/**
 * 应用根组件
 * 定义整体布局结构：侧边栏 + 主内容区
 */

import React, { useState } from 'react';
// Ant Design 的布局组件
import { Layout, theme } from 'antd';
// 自定义组件
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import DocumentManager from './components/DocumentManager';

// 解构 Layout 的子组件
const { Content, Sider } = Layout;

/**
 * 根组件函数
 * 管理顶部标签页状态（对话 / 文档）
 */
const App: React.FC = () => {
    // 当前激活的标签页：'chat' 或 'documents'
    const [activeTab, setActiveTab] = useState<'chat' | 'documents'>('chat');

    // 使用 Ant Design 的主题 Token，获取背景色
    const {
        token: { colorBgContainer },
    } = theme.useToken();

    return (
        // 最外层布局，占满整个视口
        <Layout style={{ height: '100vh' }}>
            {/* 侧边栏：固定宽度 280px，浅色主题 */}
            <Sider
                width={280}
                theme="light"
                style={{
                    background: colorBgContainer,
                    borderRight: '1px solid #f0f0f0',
                }}
            >
                {/* 侧边栏组件，传入当前标签和切换回调 */}
                <Sidebar activeTab={activeTab} onTabChange={setActiveTab} />
            </Sider>

            {/* 右侧主布局 */}
            <Layout>
                {/* 主内容区 */}
                <Content style={{ background: colorBgContainer }}>
                    {/* 根据当前标签显示不同组件 */}
                    {activeTab === 'chat' ? <ChatArea /> : <DocumentManager />}
                </Content>
            </Layout>
        </Layout>
    );
};

export default App;
