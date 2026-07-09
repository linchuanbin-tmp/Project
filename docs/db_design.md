# BankAgent 数据库设计说明书

本说明书提供了 BankAgent 平台数据库的物理与逻辑结构设计，供前端、后端以及各个 AI Agent 模块负责人共同参考。

---

## 1. 数据库基础信息

* **关系型数据库**: MySQL 8.0
* **字符集 (Character Set)**: `utf8mb4`
* **校对规则 (Collation)**: `utf8mb4_unicode_ci`
* **非关系型数据库**: Redis 7.x (用于缓存、滑动 Session、限流数据等)
* **向量数据库**: Milvus 2.3+ (用于存储 PDF 知识库文档的分片向量，详见 RAG 设计)

---

## 2. 实体关系模型 (ER Model 简析)

平台数据关系主要围绕以下核心链条展开：
1. **RBAC 权限体系**：`sys_user` (多对多) ➔ `sys_role` (多对多) ➔ `sys_permission`
2. **组织架构**：`sys_user` (一对一) ➔ `sys_department`
3. **安全审计与级别**：`sys_document` 绑定 `sys_department`，查询人 `sys_user` 必须满足部门隔离与 `clearance_level` 密级匹配。
4. **会议预订**：`meeting_schedule` 关联 `meeting_room` 与预订人 `sys_user`。
5. **任务追踪**：`task_record` 记录用户的自然语言输入与下游 Agent 处理完毕的输出。

---

## 3. 表结构详解

### 3.1 系统用户表 (`sys_user`)
存储用户的基本登录账户、密级、部门和逻辑删除状态。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `username` | varchar(50) | 否 | — | 登录用户名，唯一索引 |
| `password` | varchar(100) | 否 | — | BCrypt 强加密后的密码 |
| `real_name` | varchar(50) | 是 | NULL | 用户真实姓名 |
| `role` | varchar(20) | 是 | 'user' | 角色描述文字 |
| `status` | int | 是 | 1 | 账号状态: 1=启用, 0=禁用 |
| `dept_id` | bigint | 是 | NULL | 归属的部门ID |
| `clearance_level` | tinyint | 否 | 1 | 安全密级：1=公开, 2=内部, 3=机密 |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | datetime | 是 | CURRENT_TIMESTAMP | 自动更新时间 |
| `deleted` | tinyint | 否 | 0 | 逻辑删除标记：0=正常, 1=已删除 |

---

### 3.2 系统角色表 (`sys_role`)
定义系统级的角色权限分组。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `role_code` | varchar(50) | 否 | — | 角色编码 (如 `ROLE_ADMIN`, `ROLE_USER`) |
| `role_name` | varchar(50) | 否 | — | 角色展示名称 |
| `description` | varchar(250) | 是 | NULL | 角色职责描述 |
| `status` | int | 是 | 1 | 启用状态: 1=启用, 0=禁用 |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | datetime | 是 | CURRENT_TIMESTAMP | 更新时间 |
| `deleted` | tinyint | 否 | 0 | 逻辑删除标记：0=正常, 1=已删除 |

---

### 3.3 系统权限表 (`sys_permission`)
存储系统级的功能菜单、按钮权限及下游微服务的资源接口路径。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `perm_code` | varchar(100) | 否 | — | 权限字符串 (如 `user:view`, `task:submit`) |
| `perm_name` | varchar(100) | 否 | — | 权限功能名称 |
| `resource_path` | varchar(200) | 是 | NULL | 物理接口路径映射 |
| `method` | varchar(10) | 是 | '*' | 接口请求动作 (GET, POST, *, 等) |
| `type` | tinyint | 是 | 1 | 类型: 1=菜单/目录, 2=接口/功能按钮 |
| `parent_id` | bigint | 是 | 0 | 父权限ID |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | datetime | 是 | CURRENT_TIMESTAMP | 更新时间 |
| `deleted` | tinyint | 否 | 0 | 逻辑删除标记：0=正常, 1=已删除 |

---

### 3.4 用户角色关联表 (`sys_user_role`)
多对多关联中间表。

| 字段名 | 数据类型 | 允许为空 | 备注 |
| :--- | :--- | :---: | :--- |
| `user_id` | bigint | 否 | 关联 `sys_user.id` |
| `role_id` | bigint | 否 | 关联 `sys_role.id` |

---

### 3.5 角色权限关联表 (`sys_role_permission`)
多对多关联中间表。

| 字段名 | 数据类型 | 允许为空 | 备注 |
| :--- | :--- | :---: | :--- |
| `role_id` | bigint | 否 | 关联 `sys_role.id` |
| `perm_id` | bigint | 否 | 关联 `sys_permission.id` |

---

### 3.6 部门架构表 (`sys_department`)
定义多级网点与部门。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `dept_name` | varchar(100) | 否 | — | 部门名称 (如 Credit Department) |
| `description` | varchar(250) | 是 | NULL | 部门核心职能描述 |
| `parent_id` | bigint | 是 | 0 | 上级部门ID |
| `status` | int | 是 | 1 | 状态: 1=启用, 0=禁用 |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |

---

### 3.7 知识库文档表 (`sys_document`)
用于 RAG Agent 检索的企业内部文档分级控制。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `title` | varchar(100) | 否 | — | 文档标题 |
| `content` | text | 否 | — | 文档文本内容 |
| `dept_id` | bigint | 是 | NULL | 关联归属的部门ID (空代表全局公开文档) |
| `security_level` | tinyint | 否 | 1 | 密级等级：1=公开, 2=内部, 3=机密 |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 上传创建时间 |

---

### 3.8 消息通知与审批表 (`sys_notification`)
用于 RAG 审计申请或升级申请的流转消息。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `sender_id` | bigint | 否 | — | 发送人 ID (关联 `sys_user.id`) |
| `receiver_id` | bigint | 否 | — | 审批/接收人 ID (关联 `sys_user.id`) |
| `title` | varchar(100) | 否 | — | 通知标题 |
| `content` | text | 否 | — | 审批请求的详情描述 |
| `notify_type` | varchar(20) | 否 | 'MESSAGE' | 消息类别: `AUDIT` (审批), `MESSAGE` (通知) |
| `status` | int | 否 | 0 | 审批/阅读状态: 0=未读, 1=已读, 2=待审批, 3=已批准, 4=已拒绝 |
| `payload` | text | 是 | NULL | 附带数据载荷 (如 RAG 获取失败日志) |
| `parent_id` | bigint | 是 | NULL | 回复消息的父 ID |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |

---

### 3.9 会议室表 (`meeting_room`)
Tool Agent 专用的预订数据源。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `name` | varchar(100) | 否 | — | 会议室名称 (如 Room 301) |
| `capacity` | int | 否 | — | 容纳人数限制 |
| `location` | varchar(200) | 否 | — | 位置及楼层描述 |
| `status` | varchar(20) | 否 | 'AVAILABLE' | 当前状态 (AVAILABLE, IN_USE) |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 录入时间 |

---

### 3.10 会议预订日程表 (`meeting_schedule`)
Tool Agent 日程冲突检测和预订管理。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `room_id` | bigint | 是 | NULL | 预订的会议室ID (为 0 代表纯个人日程) |
| `room_name` | varchar(100) | 是 | NULL | 会议室冗余名称 |
| `booker` | varchar(50) | 否 | — | 预订人的 `username` |
| `topic` | varchar(200) | 否 | — | 会议主题 / 日程内容 |
| `start_time` | datetime | 否 | — | 会议开始时间 |
| `end_time` | datetime | 否 | — | 会议结束时间 |
| `create_time` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |

---

### 3.11 系统配置表 (`sys_config`)
用于管理端全局功能，例如控制动态会话过期时间。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `param_key` | varchar(100) | 否 | — | 配置参数键名 (唯一索引) |
| `param_value` | varchar(500) | 否 | — | 配置参数具体值 |
| `description` | varchar(250) | 是 | NULL | 参数作用描述 |
| `update_time` | datetime | 是 | CURRENT_TIMESTAMP | 修改时间 |

---

### 3.12 任务中心记录表 (`task_record` - 规划新增)
配合任务中心微服务建立，作为异步调度和历史审计追踪的基础数据结构。

| 字段名 | 数据类型 | 允许为空 | 默认值 | 备注 |
| :--- | :--- | :---: | :--- | :--- |
| `id` | bigint | 否 | — | 主键，自增 |
| `task_type` | varchar(20) | 否 | — | 任务类别: `CODE` (代码), `RAG` (知识问答), `TOOL` (工具) |
| `status` | varchar(20) | 否 | 'INIT' | 生命周期状态: `INIT`, `RUNNING`, `SUCCESS`, `FAIL` |
| `user_id` | bigint | 否 | — | 触发任务的用户 ID |
| `input` | text | 否 | — | 用户的原始文本输入 (Prompt) |
| `output` | text | 是 | NULL | AI 的最终执行产物 (表格 JSON / Markdown 文本) |
| `error_msg` | text | 是 | NULL | 运行失败时的异常栈/错误内容 |
| `attempt_count` | int | 否 | 0 | 自动重试次数纪录 |
| `elapsed_time` | int | 是 | 0 | 执行时长，单位毫秒 |
| `created_at` | datetime | 是 | CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | datetime | 是 | CURRENT_TIMESTAMP | 状态更新时间 |
