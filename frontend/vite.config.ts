// 导入 Vite 的配置函数和 React 插件
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
// 导入 path 模块用于处理文件路径
import path from 'path'

// 导出 Vite 配置
export default defineConfig({
    // 注册 React 插件，支持 JSX 语法
    plugins: [react()],

    // 配置模块解析规则
    resolve: {
        alias: {
            // 设置路径别名 @ 指向 src 目录
            // 这样可以用 '@/components/Button' 代替 '../../../components/Button'
            '@': path.resolve(__dirname, './src'),
        },
    },

    // 开发服务器配置
    server: {
        // 开发服务器运行端口
        port: 3000,

        // 代理配置：将 /api 开头的请求转发到后端
        proxy: {
            '/api': {
                // 后端服务器地址
                target: 'http://localhost:8080',
                // 改变请求的 origin 为目标 URL
                changeOrigin: true,
            },
        },
    },
})
