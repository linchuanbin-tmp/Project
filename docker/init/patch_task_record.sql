-- ----------------------------
-- 创建任务记录表 task_record
-- ----------------------------
DROP TABLE IF EXISTS `task_record`;
CREATE TABLE `task_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT          COMMENT '全局任务唯一ID',
  `task_type`     varchar(20)  NOT NULL                         COMMENT '任务类型: CODE, RAG, TOOL',
  `status`        varchar(20)  NOT NULL DEFAULT 'INIT'          COMMENT '状态: INIT, RUNNING, SUCCESS, FAIL',
  `user_id`       bigint       NOT NULL                         COMMENT '提交人用户ID',
  `input`         text         NOT NULL                         COMMENT '用户的自然语言输入 (Prompt)',
  `output`        text         DEFAULT NULL                     COMMENT 'AI 最终输出结果',
  `error_msg`     text         DEFAULT NULL                     COMMENT '失败时的错误详细描述',
  `attempt_count` int          NOT NULL DEFAULT 0               COMMENT '重试次数',
  `elapsed_time`  int          DEFAULT 0                        COMMENT '任务执行耗时 (毫秒)',
  `created_at`    datetime     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务生命周期记录表';
