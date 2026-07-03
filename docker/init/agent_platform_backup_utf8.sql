-- =====================================================
-- agent_platform 数据库初始化脚本（UTF-8 干净版）
-- 由 Docker 容器首次启动时自动执行
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET TIME_ZONE = '+08:00';

-- ----------------------------
-- 会议室表
-- ----------------------------
DROP TABLE IF EXISTS `meeting_room`;
CREATE TABLE `meeting_room` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `room_name`   varchar(50) NOT NULL           COMMENT '会议室名称',
  `building`    varchar(100) DEFAULT NULL      COMMENT '楼宇',
  `floor`       varchar(20) NOT NULL           COMMENT '楼层',
  `capacity`    int         NOT NULL           COMMENT '容纳人数',
  `facilities`  varchar(200) DEFAULT NULL      COMMENT '设备（投影仪/白板等）',
  `status`      int          DEFAULT '1'       COMMENT '1可用 0维护中',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     int          DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议室表';

INSERT INTO `meeting_room` VALUES
  (1, 'Room 301', 'Building A', '3', 10, 'Projector,Whiteboard',              1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'Room 302', 'Building A', '3', 20, 'Projector,Video Conference',        1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'Room 201', 'Building A', '2',  8, 'Whiteboard',                 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (4, 'Room 501 (Large)', 'Building B', '5', 50, 'Projector,Audio,Video Conference', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 会议/日程预约表
-- ----------------------------
DROP TABLE IF EXISTS `meeting_schedule`;
CREATE TABLE `meeting_schedule` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `room_id`    bigint       NOT NULL           COMMENT '会议室ID（0=个人日程）',
  `booker`     varchar(50)  NOT NULL           COMMENT '预约人',
  `start_time` datetime     NOT NULL           COMMENT '开始时间',
  `end_time`   datetime     NOT NULL           COMMENT '结束时间',
  `topic`      varchar(200) DEFAULT NULL       COMMENT '主题',
  `status`     int          DEFAULT '1'        COMMENT '1有效 0取消',
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    int          DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议/日程预约表';

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`              bigint      NOT NULL AUTO_INCREMENT,
  `username`        varchar(50) NOT NULL           COMMENT '登录用户名',
  `password`        varchar(100) NOT NULL          COMMENT 'BCrypt 加密密码',
  `real_name`       varchar(50)  DEFAULT NULL      COMMENT '真实姓名',
  `role`            varchar(20)  DEFAULT 'user'    COMMENT '角色描述',
  `status`          int          DEFAULT '1'       COMMENT '1启用 0禁用',
  `dept_id`         bigint       DEFAULT NULL      COMMENT 'Belongs to Department ID',
  `clearance_level` tinyint      NOT NULL DEFAULT 1 COMMENT 'Clearance level: 1=Public, 2=Internal, 3=Confidential',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=6
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统用户表';

-- 默认账号密码均为 123456
INSERT INTO `sys_user` VALUES
  (1, 'admin', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Administrator', 'admin', 1, NULL, 3, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'credit_mgr', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Credit Manager', 'dept_admin', 1, 1, 2, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (3, 'credit_staff', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Credit Staff', 'user', 1, 1, 1, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (4, 'compliance_mgr', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Compliance Manager', 'dept_admin', 1, 2, 2, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (5, 'compliance_staff', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Compliance Staff', 'user', 1, 2, 1, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0);

-- ----------------------------
-- 角色表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `role_code`   varchar(50) NOT NULL           COMMENT '角色编码',
  `role_name`   varchar(50) NOT NULL           COMMENT '角色名称',
  `description` varchar(250) DEFAULT NULL      COMMENT '角色描述',
  `status`      int          DEFAULT '1'       COMMENT '1启用 0禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统角色表';

INSERT INTO `sys_role` VALUES
  (1, 'ROLE_ADMIN', 'System Administrator', 'Has all system management privileges', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'ROLE_USER', 'Employee', 'Access to knowledge base retrieval, code generation, and tool call agents', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'ROLE_DEPT_ADMIN', 'Department Administrator', 'Manages department members and reviews RAG audits', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 权限表
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `perm_code`     varchar(100) NOT NULL          COMMENT '权限编码 (如 user:view)',
  `perm_name`     varchar(100) NOT NULL          COMMENT '权限名称',
  `resource_path` varchar(200) DEFAULT NULL      COMMENT '请求接口路径',
  `method`        varchar(10)  DEFAULT '*'       COMMENT '请求方法 (GET/POST/PUT/DELETE/*)',
  `type`          tinyint      DEFAULT '1'       COMMENT '1菜单/目录 2接口/按钮',
  `parent_id`     bigint       DEFAULT '0'       COMMENT '父级权限ID',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统权限表';

INSERT INTO `sys_permission` VALUES
  (1, 'user:view',   'View Employee List',   '/admin/users',       'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'user:role',   'Assign Employee Role',   '/admin/user/role',   'POST', 2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'user:status', 'Enable/Disable Employee',   '/admin/user/status', 'PUT',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (4, 'role:view',   'View Role List',   '/admin/roles',       'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (5, 'task:submit', 'Submit Agent Task',  '/task/submit',       'POST', 2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (6, 'task:query',  'Query Agent Task',  '/task/**',           'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 用户与角色关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- admin 账号默认分配 ROLE_ADMIN 角色
INSERT INTO `sys_user_role` VALUES 
  (1, 1),
  (2, 3),
  (3, 2),
  (4, 3),
  (5, 2);

-- ----------------------------
-- 角色与权限关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `perm_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`,`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 系统管理员拥有全部权限
INSERT INTO `sys_role_permission` VALUES
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6);

-- 普通员工及部门管理员仅拥有提交和查询 Agent 任务权限
INSERT INTO `sys_role_permission` VALUES
  (2, 5), (2, 6),
  (3, 5), (3, 6);

-- ----------------------------
-- 部门表
-- ----------------------------
DROP TABLE IF EXISTS `sys_department`;
CREATE TABLE `sys_department` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `dept_name`   varchar(100) NOT NULL           COMMENT '部门名称',
  `description` varchar(250) DEFAULT NULL      COMMENT '部门描述',
  `parent_id`   bigint       DEFAULT 0          COMMENT '上级部门ID',
  `status`      int          DEFAULT 1          COMMENT '部门状态: 1启用, 0禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

INSERT INTO `sys_department` VALUES
  (1, 'Credit Department', 'Handles credit assessment, loan authorization, and corporate risk analysis.', 0, 1, '2026-07-03 10:25:48'),
  (2, 'Compliance Department', 'Responsible for Anti-Money Laundering verification, auditing, and regulatory compliance.', 0, 1, '2026-07-03 10:25:48'),
  (3, 'Asset Management Department', 'Manages investment portfolios, assets allocation, and banking funds.', 0, 1, '2026-07-03 10:25:48'),
  (4, 'Retail Banking Department', 'Provides banking products and services for retail customers.', 0, 1, '2026-07-03 10:25:48'),
  (5, 'Investment Banking Department', 'Focuses on capital markets underwriting and corporate mergers & acquisitions.', 0, 1, '2026-07-03 10:25:48'),
  (6, 'Risk Control Department', 'Analyzes market risk thresholds and sets financial liquidity hazards parameters.', 0, 1, '2026-07-03 10:25:48');

-- ----------------------------
-- 部门文档表
-- ----------------------------
DROP TABLE IF EXISTS `sys_document`;
CREATE TABLE `sys_document` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `title`          varchar(100) NOT NULL,
  `content`        text         NOT NULL,
  `dept_id`        bigint       DEFAULT NULL,
  `security_level` tinyint      NOT NULL DEFAULT 1 COMMENT '1=Public, 2=Internal, 3=Confidential',
  `create_time`    datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门文档表';

INSERT INTO `sys_document` VALUES
  (7, 'Q1 2025 Credit Assessment Manual', 'Standard operational guidelines for evaluating corporate credit risk in the first quarter of 2025.', 1, 2, '2026-07-03 10:25:48'),
  (8, 'Confidential Credit Risk Evaluation for Corporate Accounts', 'Secret guidelines for loan limits and credit authorization metrics.', 1, 3, '2026-07-03 10:25:48'),
  (9, 'Standard Loan Agreement Template', 'Standard legal template for corporate loan agreements.', 1, 1, '2026-07-03 10:25:48'),
  (10, 'AML Compliance Operational Handbook', 'Detailed procedures for Anti-Money Laundering monitoring and suspicious transaction reporting.', 2, 2, '2026-07-03 10:25:48'),
  (11, 'Internal Audit Code of Conduct', 'Guiding principles for compliance verification and internal audit schedules.', 2, 1, '2026-07-03 10:25:48'),
  (12, 'High Risk Client Investigation Guidelines', 'Confidential manual for auditing shell companies and high net worth individuals.', 2, 3, '2026-07-03 10:25:48'),
  (13, 'BankAgent Platform User Manual', 'Welcome to BankAgent. This guide explains how to use our Tool Agents, Code Agents, and RAG systems. Please keep your account password secure.', NULL, 1, '2026-07-03 10:25:48'),
  (14, 'Security Operations & Clearance Levels Guide', 'General information about bank security clearance levels. Level-1 is Public, Level-2 is Internal, and Level-3 is Confidential. Escalation requires manager approval.', NULL, 1, '2026-07-03 10:25:48');

-- ----------------------------
-- 消息通知表
-- ----------------------------
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `sender_id`   bigint       NOT NULL,
  `receiver_id` bigint       NOT NULL,
  `title`       varchar(150) NOT NULL,
  `content`     text         NOT NULL,
  `notify_type` varchar(50)  DEFAULT 'CHAT' COMMENT 'CHAT/RAG_APPLY/SQL_AUDIT/BUG_REPORT',
  `status`      int          DEFAULT '1'    COMMENT '1=Unread, 2=Read Pending Approval, 3=Approved/Resolved, 4=Rejected/Denied',
  `payload`     text         DEFAULT NULL   COMMENT 'JSON payload parameters',
  `opinion`     text         DEFAULT NULL   COMMENT 'Approval review comments',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

INSERT INTO `sys_notification` VALUES
  (1, 3, 2, 'RAG Permission Escalation Request', 'Employee @credit_staff requests temporary access to "Confidential Credit Risk Evaluation for Corporate Accounts" (Security: Level-3).', 'RAG_APPLY', 2, '{"documentId":8,"title":"Confidential Credit Risk Evaluation for Corporate Accounts","clearanceLevel":3,"reason":"Need to review Q2 auditing notes."}', NULL, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (2, 3, 2, 'Warning: Risky SQL Execution Request', 'AI Agent has intercepted a suspicious database deletion command by @credit_staff.', 'SQL_AUDIT', 2, '{"sql":"DELETE FROM bank_ledger WHERE customer_id = 992 AND balance < 100.00;","riskScore":98,"reason":"Unrestricted deletion statement detected without where index."}', NULL, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (3, 5, 4, 'Bug Diagnosis Report: Model Hallucination', 'Automated trace log for abnormal retrieval citation matching score.', 'BUG_REPORT', 1, '{"citationScore":0.31,"prompts":"What is the maximum credit line for high-net-worth clients?","output":"According to Section 4, the maximum credit line is 50 million dollars. [Unverifiable Citation: Section 9]","milvusRetrieval":["Section 4: Credit limits are determined by risk rating...","Section 7: Shell companies are restricted to 1 million..."]}', NULL, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0);

SET FOREIGN_KEY_CHECKS = 1;
