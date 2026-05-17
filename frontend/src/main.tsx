/**
 * React 应用入口文件
 * 负责渲染根组件并配置全局 Provider
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
// Ant Design 的全局配置提供者
import { ConfigProvider } from 'antd';
// 中文语言包
import zhCN from 'antd/locale/zh_CN';
// 根组件
import App from './App';
// 全局样式
import './index.css';

/**
 * 渲染 React 应用到 DOM
 * createRoot 是 React 18 的新 API，支持并发特性
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
    // StrictMode 帮助发现潜在问题（仅在开发模式生效）
    <React.StrictMode>
        {/* ConfigProvider 提供全局配置，这里设置语言为中文 */}
        <ConfigProvider locale={zhCN}>
            <App />
        </ConfigProvider>
    </React.StrictMode>
);
