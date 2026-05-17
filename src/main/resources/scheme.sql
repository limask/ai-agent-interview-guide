NEW_FILE_CODE
-- AI Agent Platform 数据库初始化脚本
-- 数据库: agent_platform

-- 创建数据库(如果不存在)
CREATE DATABASE IF NOT EXISTS agent_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE agent_platform;

-- 会话表
CREATE TABLE IF NOT EXISTS conversation (
                                            id VARCHAR(64) NOT NULL COMMENT '会话ID(UUID)',
    title VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户标识',
    turn_count INT DEFAULT 0 COMMENT '消息轮次数',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除:0-未删除,1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS message (
                                       id VARCHAR(64) NOT NULL COMMENT '消息ID(UUID)',
    conversation_id VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    role VARCHAR(20) NOT NULL COMMENT '消息角色:USER/ASSISTANT/SYSTEM',
    content TEXT NOT NULL COMMENT '消息内容',
    token_count INT DEFAULT 0 COMMENT 'Token数量',
    model VARCHAR(100) DEFAULT NULL COMMENT '使用的模型',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除:0-未删除,1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_create_time (create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 文档表
CREATE TABLE IF NOT EXISTS document (
                                        id VARCHAR(64) NOT NULL COMMENT '文档ID(UUID)',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_type VARCHAR(50) DEFAULT NULL COMMENT '文件类型:pdf,docx,txt等',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    file_path VARCHAR(500) DEFAULT NULL COMMENT '文件存储路径',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '处理状态:pending/processing/completed/failed',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '上传用户',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除:0-未删除,1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';
