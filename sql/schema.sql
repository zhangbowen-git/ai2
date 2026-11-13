-- ======================================================================
-- 智能客服 Agent — 数据库建表脚本
-- 数据库：aitest2（与 application.yml 配置一致）
-- ======================================================================

CREATE DATABASE IF NOT EXISTS aitest2 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aitest2;

-- ----------------------------------------------------------------------
-- 1. 知识库文档元数据表
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS kb_chunk;
DROP TABLE IF EXISTS kb_document;
DROP TABLE IF EXISTS agent_ticket;

CREATE TABLE kb_document (
    id            BIGINT       NOT NULL COMMENT '主键',
    doc_name      VARCHAR(255) NOT NULL COMMENT '文档名称',
    category      VARCHAR(64)  NOT NULL DEFAULT 'after_sales' COMMENT '业务分类（权限隔离）',
    tags          VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',
    file_type     VARCHAR(32)  DEFAULT NULL COMMENT '文件类型：pdf/docx/md/txt/xlsx',
    file_size     BIGINT       DEFAULT 0  COMMENT '文件大小（字节）',
    chunk_size    INT          DEFAULT 500 COMMENT '分片大小（字符）',
    chunk_overlap INT          DEFAULT 100 COMMENT '分片重叠窗口（字符）',
    version       INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    status        INT          NOT NULL DEFAULT 0 COMMENT '状态：0处理中 / 1已启用 / 2已禁用 / 3处理失败',
    chunk_count   INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    error_msg     VARCHAR(512) DEFAULT NULL COMMENT '处理失败原因',
    create_by     BIGINT       DEFAULT NULL COMMENT '上传人ID',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cat_status (category, status),
    KEY idx_doc_name (doc_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档元数据';

-- ----------------------------------------------------------------------
-- 2. 知识库文档切片表（doc_id → kb_document.id 有外键逻辑关系）
-- ----------------------------------------------------------------------
CREATE TABLE kb_chunk (
    id          BIGINT     NOT NULL COMMENT '主键',
    doc_id      BIGINT     NOT NULL COMMENT '所属文档ID（kb_document.id）',
    chunk_index INT        NOT NULL COMMENT '分片序号（从0开始）',
    vector_id   VARCHAR(64) NOT NULL COMMENT 'Milvus 中对应向量ID（UUID）',
    content     MEDIUMTEXT NOT NULL COMMENT '分片文本原文',
    create_time DATETIME   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vector_id (vector_id),
    KEY idx_doc_id (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档切片';

-- ----------------------------------------------------------------------
-- 3. 人工客服工单表
-- ----------------------------------------------------------------------
CREATE TABLE agent_ticket (
    id          BIGINT       NOT NULL COMMENT '主键',
    ticket_no   VARCHAR(32)  NOT NULL COMMENT '工单编号',
    session_id  VARCHAR(64)  DEFAULT NULL COMMENT '来源会话ID',
    user_id     VARCHAR(64)  DEFAULT NULL COMMENT '用户ID',
    reason      VARCHAR(512) DEFAULT NULL COMMENT '转人工原因',
    queue_no    INT          DEFAULT NULL COMMENT '排队序号',
    status      INT          NOT NULL DEFAULT 0 COMMENT '状态：0排队中 / 1已接入 / 2已结束',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工客服工单';
