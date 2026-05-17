/**
 * API 服务层
 * 封装所有与后端的 HTTP 请求
 * 使用 Axios 库进行网络通信
 */

import axios, { AxiosInstance } from 'axios';
import { ChatRequest, ChatResponse, DocumentUploadResponse, ApiResult } from '@/types';

/**
 * API 服务类
 * 统一管理所有后端接口调用
 */
class ApiService {
    // Axios 实例，预配置了 baseURL 和拦截器
    private client: AxiosInstance;

    /**
     * 构造函数：初始化 Axios 实例
     */
    constructor() {
        // 创建 Axios 实例并配置默认选项
        this.client = axios.create({
            // 基础 URL：所有请求都会加上这个前缀
            baseURL: '/api/v1',
            // 超时时间：120 秒（考虑到 AI 生成可能较慢）
            timeout: 120000,
            // 默认请求头
            headers: {
                'Content-Type': 'application/json',
            },
        });

        // 添加响应拦截器
        this.client.interceptors.response.use(
            // 成功响应：直接返回 data 字段
            (response) => response.data,
            // 失败响应：打印错误并抛出异常
            (error) => {
                console.error('API Error:', error);
                return Promise.reject(error);
            }
        );
    }

    /**
     * 普通聊天接口
     * @param request 聊天请求参数
     * @returns 聊天响应结果
     */
    async chat(request: ChatRequest): Promise<ApiResult<ChatResponse>> {
        // POST 请求到 /chat 接口
        return this.client.post('/chat', request);
    }

    /**
     * 流式聊天接口（SSE）
     * 使用 Fetch API 而非 Axios，因为需要处理流式响应
     * @param request 聊天请求参数
     * @returns Response 对象，需要手动读取流
     */
    async chatStream(request: ChatRequest): Promise<Response> {
        // 发起 POST 请求
        const response = await fetch('/api/v1/chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request),
        });

        // 检查响应状态
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        // 返回原生 Response 对象
        return response;
    }

    /**
     * 文档上传接口
     * @param file 要上传的文件对象
     * @returns 上传响应结果
     */
    async uploadDocument(file: File): Promise<ApiResult<DocumentUploadResponse>> {
        // 创建 FormData 对象（用于文件上传）
        const formData = new FormData();
        // 添加文件字段，键名必须与后端 @RequestParam("file") 一致
        formData.append('file', file);

        // 使用 axios 发送 POST 请求
        // 注意：这里不能用 this.client，因为需要 multipart/form-data
        const response = await axios.post('/api/v1/documents/upload', formData, {
            headers: {
                // 浏览器会自动设置正确的 Content-Type（包含 boundary）
                'Content-Type': 'multipart/form-data',
            },
        });

        // 返回响应数据
        return response.data;
    }
    /**
     * 获取文档上传历史列表
     * @returns 文档列表
     */
    async getDocumentHistory(): Promise<ApiResult<DocumentUploadResponse[]>> {
        return this.client.get('/documents/history');
    }


    /**
     * 健康检查接口
     * @returns 健康状态信息
     */
    async healthCheck(): Promise<any> {
        return this.client.get('/health');
    }
}

// 导出单例实例
export const apiService = new ApiService();
