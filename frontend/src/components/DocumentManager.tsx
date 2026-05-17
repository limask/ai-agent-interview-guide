/**
 * 文档管理组件
 * 支持拖拽上传文档，显示上传历史
 */

import React, { useState ,useEffect} from 'react';
// Ant Design 组件
import { Card, Upload, Typography, Space, Tag , message } from 'antd';
// Ant Design 图标
import { InboxOutlined, FileTextOutlined, CheckCircleOutlined } from '@ant-design/icons';
// API 服务
import { apiService } from '@/services/api';
// 类型定义
import { DocumentUploadResponse } from '@/types';

// 解构 Dragger 组件（拖拽上传）
const { Dragger } = Upload;
// 解构 Typography 组件
const { Title, Text, Paragraph } = Typography;

/**
 * 文档管理组件
 */
const DocumentManager: React.FC = () => {
    // 上传状态
    const [uploading, setUploading] = useState(false);
    // 上传历史记录
    const [uploadHistory, setUploadHistory] = useState<DocumentUploadResponse[]>([]);
    const [loading, setLoading] = useState(false); // 新增：加载状态


    /**
     * 从后端加载历史记录
     */
    const loadHistory = async () => {
        setLoading(true);
        try {
            const result = await apiService.getDocumentHistory();
            setUploadHistory(result.data || []);
        } catch (error) {
            console.error("加载历史记录失败", error);
            message.error("加载历史记录失败");
        } finally {
            setLoading(false);
        }
    };

    // 组件挂载时自动加载历史记录
    useEffect(() => {
        loadHistory();
    }, []);
    /**
     * 处理文件上传
     * @param file 要上传的文件
     * @returns false 表示手动控制上传行为
     */
    const handleUpload = async (file: File) => {
        // 设置上传中状态
        setUploading(true);
        try {
            // 调用上传接口
            const result = await apiService.uploadDocument(file);
            // 显示成功提示
            message.success(`文档 "${result.data.fileName}" 处理成功！`);
            // 添加到历史记录
            setUploadHistory((prev) => [result.data, ...prev]);
        } catch (error: any) {
            // 错误提示
            message.error(error.message || '上传失败');
        } finally {
            // 清除上传状态
            setUploading(false);
        }
        // 返回 false 阻止默认上传行为
        return false;
    };

    // 拖拽上传组件的配置
    const uploadProps = {
        name: 'file',
        multiple: false,
        beforeUpload: handleUpload,
        accept: '.pdf,.docx,.txt,.md',
        showUploadList: false,
    };

    return (
        // 文档管理容器
        <div style={{ padding: 24, height: '100%', overflow: 'auto' }}>
            {/* 标题 */}
            <Title level={3}>文档管理</Title>
            {/* 说明文字 */}
            <Paragraph type="secondary">
                上传文档以构建知识库，支持 PDF、DOCX、TXT、MD 格式。文档将自动解析、切片并存入向量数据库。
            </Paragraph>

            {/* 拖拽上传区域 */}
            <Card style={{ marginBottom: 24 }}>
                <Dragger {...uploadProps} disabled={uploading}>
                    <p className="ant-upload-drag-icon">
                        <InboxOutlined style={{ fontSize: 48, color: uploading ? '#d9d9d9' : '#1890ff' }} />
                    </p>
                    <p className="ant-upload-text">
                        {uploading ? '上传中...' : '点击或拖拽文件到此区域上传'}
                    </p>
                    <p className="ant-upload-hint">
                        支持 PDF、DOCX、TXT、MD 格式，单个文件不超过 50MB
                    </p>
                </Dragger>
            </Card>

            {/* 上传历史区域 */}
            {uploadHistory.length > 0 && (
                <Card title={`上传历史 (${uploadHistory.length})`}>
                    <Space direction="vertical" style={{ width: '100%' }} size="middle">
                        {uploadHistory.map((doc, index) => (
                            <Card
                                key={index}
                                size="small"
                                style={{ background: '#fafafa' }}
                            >
                                <Space direction="vertical" style={{ width: '100%' }}>
                                    {/* 文件名和状态 */}
                                    <Space>
                                        <FileTextOutlined style={{ fontSize: 20, color: '#1890ff' }} />
                                        <Text strong>{doc.fileName}</Text>
                                        <Tag icon={<CheckCircleOutlined />} color="success">
                                            {doc.status}
                                        </Tag>
                                    </Space>

                                    {/* 详细信息 */}
                                    <Space wrap>
                                        <Text type="secondary">
                                            文档ID: {doc.documentId}
                                        </Text>
                                        <Text type="secondary">
                                            切片数: {doc.chunkCount}
                                        </Text>
                                        <Text type="secondary">
                                            处理时间: {doc.processingTimeMs}ms
                                        </Text>
                                    </Space>
                                </Space>
                            </Card>
                        ))}
                    </Space>
                </Card>
            )}
        </div>
    );
};

export default DocumentManager;
