-- =====================================================
-- agent_platform database initialization script
-- Automatically executed on first Docker container startup
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET TIME_ZONE = '+08:00';

-- ----------------------------
-- Meeting Room Table
-- ----------------------------
DROP TABLE IF EXISTS `meeting_room`;
CREATE TABLE `meeting_room` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `room_name`   varchar(50) NOT NULL           COMMENT 'Meeting room name',
  `building`    varchar(100) DEFAULT NULL      COMMENT 'Building',
  `floor`       varchar(20) NOT NULL           COMMENT 'Floor',
  `capacity`    int         NOT NULL           COMMENT 'Capacity',
  `facilities`  varchar(200) DEFAULT NULL      COMMENT 'Equipment (projector/whiteboard etc.)',
  `status`      int          DEFAULT '1'       COMMENT '1=Available 0=Maintenance',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     int          DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Meeting Room Table';

INSERT INTO `meeting_room` VALUES
  (1, 'Room 301', 'Building A', '3', 10, 'Projector,Whiteboard',              1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'Room 302', 'Building A', '3', 20, 'Projector,Video Conference',        1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'Room 201', 'Building A', '2',  8, 'Whiteboard',                 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (4, 'Room 501 (Large)', 'Building B', '5', 50, 'Projector,Audio,Video Conference', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- Meeting / Schedule Table
-- ----------------------------
DROP TABLE IF EXISTS `meeting_schedule`;
CREATE TABLE `meeting_schedule` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `room_id`    bigint       NOT NULL           COMMENT 'Meeting room ID (0=personal schedule)',
  `booker`     varchar(50)  NOT NULL           COMMENT 'Booker',
  `start_time` datetime     NOT NULL           COMMENT 'Start time',
  `end_time`   datetime     NOT NULL           COMMENT 'End time',
  `topic`      varchar(200) DEFAULT NULL       COMMENT 'Topic',
  `status`     int          DEFAULT '1'        COMMENT '1=Valid 0=Cancelled',
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    int          DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Meeting / Schedule Table';

-- ----------------------------
-- User Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`              bigint      NOT NULL AUTO_INCREMENT,
  `username`        varchar(50) NOT NULL           COMMENT 'Login username',
  `password`        varchar(100) NOT NULL          COMMENT 'BCrypt encrypted password',
  `real_name`       varchar(50)  DEFAULT NULL      COMMENT 'Real name',
  `role`            varchar(20)  DEFAULT 'user'    COMMENT 'Role description',
  `status`          int          DEFAULT '1'       COMMENT '1=Enabled 0=Disabled',
  `dept_id`         bigint       DEFAULT NULL      COMMENT 'Belongs to Department ID',
  `clearance_level` tinyint      NOT NULL DEFAULT 1 COMMENT 'Clearance level: 1=Public, 2=Internal, 3=Confidential',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         tinyint      NOT NULL DEFAULT 0 COMMENT 'Logical deleted 0=normal 1=deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=6
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='System User Table';

-- Default account passwords are all 123456
INSERT INTO `sys_user` VALUES
  (1, 'admin', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Administrator', 'admin', 1, NULL, 3, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'credit_mgr', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Credit Manager', 'dept_admin', 1, 1, 2, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (3, 'credit_staff', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Credit Staff', 'user', 1, 1, 1, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (4, 'compliance_mgr', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Compliance Manager', 'dept_admin', 1, 2, 2, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (5, 'compliance_staff', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', 'Compliance Staff', 'user', 1, 2, 1, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0);

-- ----------------------------
-- Role Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `role_code`   varchar(50) NOT NULL           COMMENT 'Role code',
  `role_name`   varchar(50) NOT NULL           COMMENT 'Role name',
  `description` varchar(250) DEFAULT NULL      COMMENT 'Role description',
  `status`      int          DEFAULT '1'       COMMENT '1=Enabled 0=Disabled',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT 'Logical deleted 0=normal 1=deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='System Role Table';

INSERT INTO `sys_role` VALUES
  (1, 'ROLE_ADMIN', 'System Administrator', 'Has all system management privileges', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'ROLE_USER', 'Employee', 'Access to knowledge base retrieval, code generation, and tool call agents', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'ROLE_DEPT_ADMIN', 'Department Administrator', 'Manages department members and reviews RAG audits', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- Permission Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `perm_code`     varchar(100) NOT NULL          COMMENT 'Permission code (e.g. user:view)',
  `perm_name`     varchar(100) NOT NULL          COMMENT 'Permission name',
  `resource_path` varchar(200) DEFAULT NULL      COMMENT 'Request API path',
  `method`        varchar(10)  DEFAULT '*'       COMMENT 'Request method (GET/POST/PUT/DELETE/*)',
  `type`          tinyint      DEFAULT '1'       COMMENT '1=Menu/Directory 2=API/Button',
  `parent_id`     bigint       DEFAULT '0'       COMMENT 'Parent permission ID',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT 'Logical deleted 0=normal 1=deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='System Permission Table';

INSERT INTO `sys_permission` VALUES
  (1, 'user:view',   'View Employee List',   '/admin/users',       'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'user:role',   'Assign Employee Role',   '/admin/user/role',   'POST', 2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'user:status', 'Enable/Disable Employee',   '/admin/user/status', 'PUT',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (4, 'role:view',   'View Role List',   '/admin/roles',       'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (5, 'task:submit', 'Submit Agent Task',  '/task/submit',       'POST', 2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (6, 'task:query',  'Query Agent Task',  '/task/**',           'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- User-Role Association Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `role_id` bigint NOT NULL COMMENT 'Role ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User-Role Association Table';

-- Admin account is assigned ROLE_ADMIN by default
INSERT INTO `sys_user_role` VALUES 
  (1, 1),
  (2, 3),
  (3, 2),
  (4, 3),
  (5, 2);

-- ----------------------------
-- Role-Permission Association Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL COMMENT 'Role ID',
  `perm_id` bigint NOT NULL COMMENT 'Permission ID',
  PRIMARY KEY (`role_id`,`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role-Permission Association Table';

-- System administrator has all permissions
INSERT INTO `sys_role_permission` VALUES
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6);

-- Regular staff and department admins only have agent task submission and query permissions
INSERT INTO `sys_role_permission` VALUES
  (2, 5), (2, 6),
  (3, 5), (3, 6);

-- ----------------------------
-- Department Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_department`;
CREATE TABLE `sys_department` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `dept_name`   varchar(100) NOT NULL           COMMENT 'Department name',
  `description` varchar(250) DEFAULT NULL      COMMENT 'Department description',
  `parent_id`   bigint       DEFAULT 0          COMMENT 'Parent department ID',
  `status`      int          DEFAULT 1          COMMENT 'Department status: 1=enabled, 0=disabled',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Department Table';

INSERT INTO `sys_department` VALUES
  (1, 'Credit Department', 'Handles credit assessment, loan authorization, and corporate risk analysis.', 0, 1, '2026-07-03 10:25:48'),
  (2, 'Compliance Department', 'Responsible for Anti-Money Laundering verification, auditing, and regulatory compliance.', 0, 1, '2026-07-03 10:25:48'),
  (3, 'Asset Management Department', 'Manages investment portfolios, assets allocation, and banking funds.', 0, 1, '2026-07-03 10:25:48'),
  (4, 'Retail Banking Department', 'Provides banking products and services for retail customers.', 0, 1, '2026-07-03 10:25:48'),
  (5, 'Investment Banking Department', 'Focuses on capital markets underwriting and corporate mergers & acquisitions.', 0, 1, '2026-07-03 10:25:48'),
  (6, 'Risk Control Department', 'Analyzes market risk thresholds and sets financial liquidity hazards parameters.', 0, 1, '2026-07-03 10:25:48');

-- ----------------------------
-- Department Document Table
-- ----------------------------
DROP TABLE IF EXISTS `sys_document`;
CREATE TABLE `sys_document` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `title`          varchar(100) NOT NULL,
  `content`        text         NOT NULL,
  `dept_id`        bigint       DEFAULT NULL,
  `security_level` tinyint      NOT NULL DEFAULT 1 COMMENT '1=Public, 2=Internal, 3=Confidential',
  `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP,
  `file_type`         varchar(20)  DEFAULT 'MARKDOWN' COMMENT 'MARKDOWN/PDF/DOCX/PPT',
  `file_size`         bigint       DEFAULT NULL COMMENT 'Original file size (bytes), NULL for MARKDOWN',
  `minio_object_key`  varchar(500) DEFAULT NULL COMMENT 'MinIO object key, NULL for MARKDOWN',
  `parse_status`      varchar(20)  DEFAULT NULL COMMENT 'PENDING/DONE/FAILED, NULL for MARKDOWN',
  PRIMARY KEY (`id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Department Document Table';

INSERT INTO `sys_document` (id, title, content, dept_id, security_level, create_time) VALUES
  (7, 'Q1 2025 Credit Assessment Manual', 'Standard operational guidelines for evaluating corporate credit risk in the first quarter of 2025.', 1, 2, '2026-07-03 10:25:48'),
  (8, 'Confidential Credit Risk Evaluation for Corporate Accounts', 'Secret guidelines for loan limits and credit authorization metrics.', 1, 3, '2026-07-03 10:25:48'),
  (9, 'Standard Loan Agreement Template', 'Standard legal template for corporate loan agreements.', 1, 1, '2026-07-03 10:25:48'),
  (10, 'AML Compliance Operational Handbook', 'Detailed procedures for Anti-Money Laundering monitoring and suspicious transaction reporting.', 2, 2, '2026-07-03 10:25:48'),
  (11, 'Internal Audit Code of Conduct', 'Guiding principles for compliance verification and internal audit schedules.', 2, 1, '2026-07-03 10:25:48'),
  (12, 'High Risk Client Investigation Guidelines', 'Confidential manual for auditing shell companies and high net worth individuals.', 2, 3, '2026-07-03 10:25:48'),
  (13, 'BankAgent Platform User Manual', 'Welcome to BankAgent. This guide explains how to use our Tool Agents, Code Agents, and RAG systems. Please keep your account password secure.', NULL, 1, '2026-07-03 10:25:48'),
  (14, 'Security Operations & Clearance Levels Guide', 'General information about bank security clearance levels. Level-1 is Public, Level-2 is Internal, and Level-3 is Confidential. Escalation requires manager approval.', NULL, 1, '2026-07-03 10:25:48'),
  (15, 'RBAC & Security Clearance System Design Specification', '# Role-Based Access Control (RBAC) & Security Clearance Design Specification

This document provides a comprehensive overview of the **Role-Based Access Control (RBAC)** and **Security Clearance Level (SCL)** architecture implemented within the BankAgent platform. It serves as a guide for development teams, system auditors, and instructors.

---

## 1. Core Architectural Concepts

To guarantee financial security and data confidentiality, the platform combines **Role-Based Permissions (RBAC)** with **Mandatory Attribute-Based Isolation (Department & Clearance Levels)**. 

Every user session is bounded by:
1. **System Role (Role-based)**: Dictates what functional features the user can invoke (e.g. executing text-to-SQL, auditing schedules, viewing logs).
2. **Department Allocation (Attribute-based)**: Restricts access to department-specific knowledge assets and databases.
3. **Security Clearance Level (Clearance-based)**: Determines the maximum confidentiality classification the user can retrieve.

---

## 2. Role Definitions & Permissions Matrix

The system pre-defines three major system roles:

| Role Code | Role Name | Allowed Functional Operations | Target Users |
| :--- | :--- | :--- | :--- |
| `ROLE_ADMIN` | System Administrator | Full user management, department roster reorganization, system logs auditing, global configuration editing. | System Auditors / IT Admins |
| `ROLE_DEPT_ADMIN` | Department Administrator | Management of department members, approval of RAG access requests, SQL query audit reviews. | Department Heads / Audit Managers |
| `ROLE_USER` | Standard Employee | Running standard business flows, querying local vector knowledge databases, asking AI agents questions. | Loan Officers / Compliance Staff |

---

## 3. Data Isolation & Security Clearance Levels (SCL)

Documents in the vector database and relational tables are cataloged under three clearance levels:

* **Level-1: Public (L1)**
  * **Scope**: General banking policies, standard operating templates, user handbooks.
  * **Access**: Accessible by any authenticated user, regardless of their department allocation.
* **Level-2: Internal (L2)**
  * **Scope**: Active project notes, standard department manuals, compliance auditing guidelines.
  * **Access**: Restricted to employees *within the same department* who possess a clearance level of **Level-2 or above**.
* **Level-3: Confidential (L3)**
  * **Scope**: Proprietary risk models, high-risk audit investigations, private client evaluation data.
  * **Access**: Restricted to senior members *within the same department* with **Clearance Level-3**.

### Multi-Dimensional Access Control Rule (SQL expression):
```sql
-- Accessible if:
-- 1. Document is global (dept_id IS NULL)
-- 2. OR (User is in the same department AND has sufficient clearance level)
-- 3. OR (User has an approved temporary access bypass token/record)
SELECT * FROM sys_document
WHERE (dept_id IS NULL) 
   OR (dept_id = :user_dept_id AND :user_clearance >= security_level)
   OR EXISTS (
       SELECT 1 FROM sys_notification 
       WHERE notify_type = ''RAG_APPLY'' 
         AND sender_id = :user_id 
         AND status = 3 -- Approved
         AND JSON_EXTRACT(payload, ''$.documentId'') = sys_document.id
   );
```

---

## 4. Human-in-the-Loop (HITL) Clearance Escalation Workflow

When an employee attempts to retrieve an internal document but has insufficient clearance, the platform implements a **Human-in-the-Loop (HITL)** approval flow to prevent rigid denial of service while maintaining strict auditing:

1. **Submission**: The user requests access, specifying a business justification (e.g., "required for Q3 loan risk report"). This generates a notification with type `RAG_APPLY` and status `2` (Read Pending Approval) routed to the department manager.
2. **Evaluation**: The department manager reviews the request. They can approve or reject the request, providing an audit comment.
3. **Activation**: Upon approval, the status changes to `3` (Approved). The document retrieval algorithm detects the approved record, and dynamically grants access to that specific document.', NULL, 1, '2026-07-03 10:25:48');

-- ----------------------------
-- Notification Table
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
  `parent_id`   bigint       DEFAULT NULL   COMMENT 'Parent notification ID',
  `thread_id`   bigint       DEFAULT NULL   COMMENT 'Root conversation thread ID',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT 'Logical deleted 0=normal 1=deleted',
  PRIMARY KEY (`id`),
  KEY `idx_thread_id` (`thread_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System Notification Table';

INSERT INTO `sys_notification` (`id`, `sender_id`, `receiver_id`, `title`, `content`, `notify_type`, `status`, `payload`, `opinion`, `create_time`, `update_time`, `deleted`) VALUES
  (1, 3, 2, 'RAG Permission Escalation Request', 'Employee @credit_staff requests temporary access to "Confidential Credit Risk Evaluation for Corporate Accounts" (Security: Level-3).', 'RAG_APPLY', 2, '{"documentId":8,"title":"Confidential Credit Risk Evaluation for Corporate Accounts","clearanceLevel":3,"reason":"Need to review Q2 auditing notes."}', NULL, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (2, 3, 2, 'Warning: Risky SQL Execution Request', 'AI Agent has intercepted a suspicious database deletion command by @credit_staff.', 'SQL_AUDIT', 2, '{"sql":"DELETE FROM bank_ledger WHERE customer_id = 992 AND balance < 100.00;","riskScore":98,"reason":"Unrestricted deletion statement detected without where index."}', NULL, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0),
  (3, 5, 4, 'Bug Diagnosis Report: Model Hallucination', 'Automated trace log for abnormal retrieval citation matching score.', 'BUG_REPORT', 1, '{"citationScore":0.31,"prompts":"What is the maximum credit line for high-net-worth clients?","output":"According to Section 4, the maximum credit line is 50 million dollars. [Unverifiable Citation: Section 9]","milvusRetrieval":["Section 4: Credit limits are determined by risk rating...","Section 7: Shell companies are restricted to 1 million..."]}', NULL, '2026-07-03 10:25:48', '2026-07-03 10:25:48', 0);

-- ----------------------------
-- System Configuration Table (dynamic params, e.g. session timeout)
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `param_key`   varchar(100) NOT NULL COMMENT 'Config key',
  `param_value` varchar(500) NOT NULL COMMENT 'Config value',
  `description` varchar(250) DEFAULT NULL COMMENT 'Description',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_param_key` (`param_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System Configuration Table';

INSERT INTO `sys_config` (`id`, `param_key`, `param_value`, `description`) VALUES
  (1, 'session_timeout', '30', 'Session inactivity timeout in minutes');

-- ----------------------------
-- Task Record Table
-- ----------------------------
CREATE TABLE IF NOT EXISTS `task_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT          COMMENT 'Global task unique ID',
  `task_type`     varchar(20)  NOT NULL                         COMMENT 'Task type: CODE, RAG, TOOL',
  `status`        varchar(20)  NOT NULL DEFAULT 'INIT'          COMMENT 'Status: INIT, RUNNING, SUCCESS, FAIL',
  `user_id`       bigint       NOT NULL                         COMMENT 'Submitter user ID',
  `input`         text         NOT NULL                         COMMENT 'User natural language input (Prompt)',
  `output`        text         DEFAULT NULL                     COMMENT 'AI final output',
  `error_msg`     text         DEFAULT NULL                     COMMENT 'Error details on failure',
  `attempt_count` int          NOT NULL DEFAULT 0               COMMENT 'Retry count',
  `elapsed_time`  int          DEFAULT 0                        COMMENT 'Task execution time (milliseconds)',
  `created_at`    datetime     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Task lifecycle record table';

-- ----------------------------
-- RAG Knowledge Base
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rag_knowledge_base` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `name`            varchar(120) NOT NULL COMMENT 'Knowledge base display name',
  `description`     varchar(500) DEFAULT NULL,
  `owner_username`  varchar(100) DEFAULT NULL,
  `dept_id`         bigint       DEFAULT NULL COMMENT 'Department scope; null = platform-wide',
  `visibility`      varchar(30)  NOT NULL DEFAULT 'DEPARTMENT' COMMENT 'PRIVATE, DEPARTMENT, GLOBAL',
  `security_level`  tinyint      NOT NULL DEFAULT 1,
  `status`          varchar(30)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, ARCHIVED, DELETED',
  `document_count`  int          NOT NULL DEFAULT 0,
  `chunk_count`     int          NOT NULL DEFAULT 0,
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_status` (`status`),
  KEY `idx_kb_dept_status` (`dept_id`, `status`),
  KEY `idx_kb_owner` (`owner_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG knowledge base metadata';

-- ----------------------------
-- RAG Source Document
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rag_source_document` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `kb_id`              bigint       NOT NULL,
  `sys_document_id`    bigint       DEFAULT NULL,
  `title`              varchar(200) NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `file_type`          varchar(32)  DEFAULT NULL,
  `mime_type`          varchar(120) DEFAULT NULL,
  `file_size`          bigint       DEFAULT NULL,
  `storage_provider`   varchar(30)  NOT NULL DEFAULT 'minio',
  `storage_bucket`     varchar(120) DEFAULT NULL,
  `storage_object_key` varchar(500) DEFAULT NULL,
  `content_hash`       varchar(64)  DEFAULT NULL COMMENT 'SHA-256 of original file',
  `parsed_text`        longtext     DEFAULT NULL,
  `status`             varchar(30)  NOT NULL DEFAULT 'ACTIVE',
  `parser_status`      varchar(30)  NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED, PARSED, PARSE_PENDING, PARSE_FAIL',
  `index_status`       varchar(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, INDEXED, INDEX_FAIL',
  `chunk_count`        int          NOT NULL DEFAULT 0,
  `security_level`     tinyint      NOT NULL DEFAULT 1,
  `dept_id`            bigint       DEFAULT NULL,
  `uploaded_by`        varchar(100) DEFAULT NULL,
  `error_message`      varchar(1000) DEFAULT NULL,
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_source_kb` (`kb_id`),
  KEY `idx_source_sys_document` (`sys_document_id`),
  KEY `idx_source_status` (`status`, `parser_status`, `index_status`),
  KEY `idx_source_dept_security` (`dept_id`, `security_level`),
  KEY `idx_source_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG uploaded source document metadata';

-- ----------------------------
-- RAG Document Chunk
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rag_document_chunk` (
  `id`                bigint       NOT NULL AUTO_INCREMENT,
  `document_id`       bigint       NOT NULL,
  `chunk_index`       int          NOT NULL,
  `chunk_text`        mediumtext   NOT NULL,
  `token_count`       int          DEFAULT 0,
  `vector_id`         varchar(128) NOT NULL,
  `embedding_profile` varchar(64)  NOT NULL DEFAULT 'local-bge-m3',
  `embedding_model`   varchar(120) DEFAULT NULL,
  `vector_collection` varchar(120) DEFAULT NULL,
  `index_status`      varchar(30)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'RUNNING, SUCCESS, FAIL',
  `security_level`    tinyint      NOT NULL DEFAULT 1,
  `dept_id`           bigint       DEFAULT NULL,
  `content_hash`      varchar(64)  DEFAULT NULL,
  `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`       datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vector_id` (`vector_id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_chunk_profile_document` (`embedding_profile`, `document_id`),
  KEY `idx_dept_security` (`dept_id`, `security_level`),
  KEY `idx_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG document chunk metadata';

-- ----------------------------
-- RAG Query Log
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rag_query_log` (
  `id`                bigint       NOT NULL AUTO_INCREMENT,
  `user_id`           bigint       NOT NULL,
  `username`          varchar(100) NOT NULL,
  `question`          text         NOT NULL,
  `answer`            mediumtext   DEFAULT NULL,
  `retrieved_doc_ids` varchar(500) DEFAULT NULL,
  `blocked_doc_ids`   varchar(500) DEFAULT NULL,
  `embedding_profile` varchar(64)  DEFAULT NULL,
  `embedding_model`   varchar(120) DEFAULT NULL,
  `vector_collection` varchar(120) DEFAULT NULL,
  `top_k`             int          DEFAULT 5,
  `latency_ms`        int          DEFAULT 0,
  `status`            varchar(30)  NOT NULL DEFAULT 'INIT',
  `error_msg`         text         DEFAULT NULL,
  `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_username` (`username`),
  KEY `idx_query_embedding_profile` (`embedding_profile`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG query audit log';

-- ----------------------------
-- RAG Index Task
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rag_index_task` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `document_id` bigint      DEFAULT NULL,
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

SET FOREIGN_KEY_CHECKS = 1;

