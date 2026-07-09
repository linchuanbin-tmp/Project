-- ------------------------------------------------------------
-- RAG Agent metadata tables
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `rag_document_chunk` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `document_id`    bigint       NOT NULL COMMENT 'Source sys_document.id',
  `chunk_index`    int          NOT NULL COMMENT 'Chunk order within the source document',
  `chunk_text`     mediumtext   NOT NULL COMMENT 'Chunk body used for retrieval and citations',
  `token_count`    int          DEFAULT 0 COMMENT 'Estimated token count',
  `vector_id`      varchar(128) NOT NULL COMMENT 'Vector primary key in Milvus',
  `security_level` tinyint      NOT NULL DEFAULT 1 COMMENT 'Copied from sys_document.security_level',
  `dept_id`        bigint       DEFAULT NULL COMMENT 'Copied from sys_document.dept_id',
  `content_hash`   varchar(64)  DEFAULT NULL COMMENT 'Hash of the source chunk content',
  `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vector_id` (`vector_id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_dept_security` (`dept_id`, `security_level`),
  KEY `idx_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG document chunk metadata';

CREATE TABLE IF NOT EXISTS `rag_query_log` (
  `id`                bigint       NOT NULL AUTO_INCREMENT,
  `user_id`           bigint       NOT NULL,
  `username`          varchar(100) NOT NULL,
  `question`          text         NOT NULL,
  `answer`            mediumtext   DEFAULT NULL,
  `retrieved_doc_ids` varchar(500) DEFAULT NULL,
  `blocked_doc_ids`   varchar(500) DEFAULT NULL,
  `top_k`             int          DEFAULT 5,
  `latency_ms`        int          DEFAULT 0,
  `status`            varchar(30)  NOT NULL DEFAULT 'INIT',
  `error_msg`         text         DEFAULT NULL,
  `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG query audit log';

CREATE TABLE IF NOT EXISTS `rag_index_task` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `document_id` bigint      DEFAULT NULL COMMENT 'Null means full rebuild',
  `task_type`   varchar(30) NOT NULL COMMENT 'REBUILD_ALL, INDEX_DOCUMENT, DELETE_DOCUMENT',
  `status`      varchar(30) NOT NULL DEFAULT 'INIT' COMMENT 'INIT, RUNNING, SUCCESS, FAIL',
  `message`     text        DEFAULT NULL,
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_status` (`status`),
  KEY `idx_task_type` (`task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG indexing task status';
