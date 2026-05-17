# AI Agent Platform Frontend

基于 React + TypeScript + Vite + Ant Design 构建的现代化前端应用。

## 技术栈

- **React 18** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Ant Design 5** - UI 组件库
- **Zustand** - 状态管理
- **Axios** - HTTP 客户端
- **React Markdown** - Markdown 渲染

## 快速开始

### 安装依赖

bash npm install

### 开发模式

bash npm run dev

访问 http://localhost:3000

### 生产构建

bash npm run build

### 预览生产版本

bash npm run preview

## 功能特性

### 1. 智能对话
- ✅ 支持 ReAct、Planner、Reflection 三种 Agent 模式
- ✅ SSE 流式响应，实时显示生成内容
- ✅ RAG 检索增强，可开关控制
- ✅ 对话历史持久化存储
- ✅ 思考过程可视化展示
- ✅ 检索来源文档展示

### 2. 文档管理
- ✅ 拖拽上传文档
- ✅ 支持 PDF、DOCX、TXT、MD 格式
- ✅ 自动解析和向量化
- ✅ 上传历史记录

### 3. 会话管理
- ✅ 多会话切换
- ✅ 会话标题自动更新
- ✅ 删除无用会话
- ✅ 本地持久化存储

## 项目结构

src/
├── components/          # React 组件
│   ├── Sidebar.tsx      # 侧边栏
│   ├── ChatArea.tsx     # 聊天区域
│   ├── MessageList.tsx  # 消息列表
│   ├── ChatInput.tsx    # 输入框
│   └── DocumentManager.tsx  # 文档管理
├── services/            # API 服务
│   └── api.ts
├── store/               # 状态管理
│   └── appStore.ts
├── types/               # TypeScript 类型
│   └── index.ts
├── App.tsx              # 根组件
├── main.tsx             # 入口文件
└── index.css            # 全局样式

## API 代理配置

开发环境下，Vite 会自动代理 `/api` 请求到后端服务器 `http://localhost:8080`。

如需修改，请编辑 `vite.config.ts` 中的 `server.proxy` 配置。

## License

Apache License 2.0
