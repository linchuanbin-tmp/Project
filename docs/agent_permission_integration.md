# Agent 权限集成规范 —— 三级 RBAC 下各 Agent 的权限边界与待实现清单

本文档明确了 BankAgent 平台的三级 RBAC 权限体系应如何贯穿到 Tool Agent、Code Agent、RAG Agent 三个智能体模块。当前各 Agent 的权限集成程度不一，本文既是**设计规范**也是**待实现清单**。

---

## 1. 权限体系回顾

平台定义了三级 RBAC，以及两个核心的安全维度：

```
角色维度：
  ROLE_ADMIN       → 系统管理员（全局）
  ROLE_DEPT_ADMIN  → 部门管理员（仅本部门）
  ROLE_USER        → 普通员工（仅本部门 + 仅已授权资源）

数据维度：
  部门隔离（Department Isolation）  → 用户只能访问本部门数据
  密级控制（Clearance Level 1-3）  → 用户只能查看 ≤ 自己密级的文档
```

用户信息通过 Gateway 注入的请求头传递：

| Header | 内容 |
|--------|------|
| `X-User-Name` | 用户名 |
| `X-User-Roles` | 角色列表（逗号分隔），如 `ROLE_USER,ROLE_DEPT_ADMIN` |
| `X-User-Permissions` | 权限列表（逗号分隔），如 `user:read,dept:write` |

每个 Agent 都需要从这些头中获取用户上下文，并按角色执行相应的权限控制。

---

## 2. Code Agent — SQL 生成与执行的权限控制

### 2.1 当前状态

**已实现：** SQL 白名单校验（[SqlValidationService.java](../code-agent/src/main/java/com/agent/code/service/SqlValidationService.java)）— 五层静态校验：

| 层级 | 校验内容 | 问题 |
|------|----------|------|
| 1 | 操作类型：仅允许 `SELECT` | 静态规则，**所有角色一刀切** |
| 2 | 关键字黑名单：拦截 `DROP`、`DELETE`、`INSERT` 等 | 静态规则，**所有角色一刀切** |
| 3 | 表名白名单：只允许 `information_schema` 中存在的表 | **不区分角色**，普通员工也能查全部表 |
| 4 | 列名白名单：只允许表中实际存在的列 | **不区分角色** |
| 5 | 复杂度限制：JOIN 表数、WHERE 条件数 | **不区分角色** |

**未实现：** 请求中**完全不读取 `X-User-Roles` 和 `X-User-Permissions` 头**。所有用户看到的元数据和可查询的表完全一样。

### 2.2 应实现的权限分层

```
ROLE_USER：
  ├── 仅 SELECT
  ├── 仅本部门相关的表/视图（sys_user 中 dept_id 对应的部门数据）
  ├── 敏感列（salary, password 等）不可查 → HITL 审批流程
  └── 复杂度严格限制（JOIN ≤ 2, 条件 ≤ 5）

ROLE_DEPT_ADMIN：
  ├── 仅 SELECT
  ├── 本部门所有表
  ├── 敏感列可查但需审计日志
  └── 复杂度较宽松（JOIN ≤ 4, 条件 ≤ 10）

ROLE_ADMIN：
  ├── 仅 SELECT（一律禁止写操作）
  ├── 全部表可查
  ├── 全部列可查
  └── 复杂度最宽松（JOIN ≤ 8, 条件 ≤ 20）
```

### 2.3 待实现：表级权限控制

数据库 `sys_permission` 表中已有 `table_name` 字段设计（见 [db_design.md](db_design.md) 3.4 节），但目前未被 Code Agent 使用。

**实现方案：**

1. `CodeAgentController` 接收 `X-User-Roles`、`X-User-Permissions` 头，传递给 `MetadataCacheService`
2. `MetadataCacheService` 在构建元数据缓存时，根据角色过滤可见表列表：
   - `ROLE_ADMIN` → 全部表
   - `ROLE_DEPT_ADMIN` → `sys_permission` 中分配给该角色的表
   - `ROLE_USER` → 同上
3. `SqlValidationService` 的第三层（表名白名单）使用角色过滤后的表列表
4. 列级过滤：在第四层校验中，额外屏蔽敏感列（`salary`、`password_hash` 等），`ROLE_USER` 访问敏感列时触发 HITL

### 2.4 待实现：HITL SQL 审计阻断

当普通用户尝试查询超越权限的数据时，不应直接报错，而应走 HITL 审批流程：

```
1. 用户输入自然语言查询
2. Code Agent 生成 SQL
3. SqlValidationService 检测到越权（表/列不在白名单）
4. 创建 sys_notification: notifyType="SQL_AUDIT", status=2（待处理）
5. 部门管理员审批 → 批准后允许本次查询执行
```

注意：当前 `handleAction` 只更新通知状态（见 [notification_and_hitl.md](notification_and_hitl.md) 第 6 节），**缺少 SQL 重新触发的回调机制**——这是需要补充的。

---

## 3. Tool Agent — 日程/会议室/路径规划的权限控制

### 3.1 当前状态

**已实现（良好）：**

| 端点 | 权限控制 |
|------|----------|
| `MeetingRoomAdminController` 全部端点 | 读取 `X-User-Roles` 头，仅允许 `ROLE_ADMIN` |
| `MeetingScheduleAdminController` 全部端点 | 同上 |
| `DELETE /tool/my-schedule/{id}` | 校验 `X-User-Name` == schedule.booker |

**未实现：**

| 缺口 | 影响 |
|------|------|
| `POST /tool/execute`（LLM 工具调用） | **完全不传用户上下文给 LLM**。LLM 不知道当前用户是谁、什么角色，无法做权限感知的工具选择 |
| `GET /tool/meeting-rooms` | 任何认证用户都能看到所有会议室 |
| `POST /tool/meeting-room/book` | 不限制谁可以预订会议室（所有用户平等） |
| `GET /tool/schedules` | 任何用户可以查任何人的日程（`users` 参数无权限校验） |
| DeepSeek system prompt | 硬编码，不包含用户角色/部门信息 |

### 3.2 应实现的权限分层

```
ROLE_USER：
  ├── 可查看本部门成员的日程
  ├── 可预订会议室
  ├── 不可修改他人日程
  └── LLM 工具调用时，仅暴露本部门可用工具

ROLE_DEPT_ADMIN：
  ├── 可查看全部门成员的日程
  ├── 可替本部门成员预订/取消
  └── 不可管理会议室资源

ROLE_ADMIN：
  ├── 全局日程查看
  ├── 会议室 CRUD 管理
  └── 全局 schedule 管理
```

### 3.3 待实现：LLM Prompt 注入用户上下文

当前 `DeepSeekService` 的 system prompt 是静态的（[DeepSeekService.java 第 43 行](../tool-agent/src/main/java/com/agent/tool/service/DeepSeekService.java)）。需要在每次 LLM 调用时动态注入：

```
System: 当前用户为 zhangsan（ROLE_USER，信贷审批部）。
        你只能查询该用户本部门的日程、预订其部门可用的会议室。
        不可执行超出用户权限的操作。
```

实现方式：
1. `ToolController.execute()` 接收 `X-User-Name`、`X-User-Roles`
2. 构造 `ToolRequest` 时注入用户上下文
3. `DeepSeekService` 将用户上下文拼入 system prompt
4. 后处理阶段校验 LLM 输出的操作是否在权限范围内

---

## 4. RAG Agent — 文档检索的权限控制

### 4.1 当前状态

RAG Agent 模块标注为 "Coming Soon"（task-service 中硬编码抛出异常）。但权限设计已经完备：user-service 的 `DocumentController` 实现了三层权限过滤：

1. **部门隔离**：`.eq(deptId).or().isNull(deptId)` — 文档要么属于用户部门，要么是全局文档
2. **密级控制**：`user.clearanceLevel >= document.securityLevel`
3. **审批覆盖**：检查是否有已批准的 `RAG_APPLY` 通知

### 4.2 RAG Agent 实现时应遵循的规范

当 RAG Agent 实现后，应在以下环节集成权限：

```
检索阶段（Milvus Metadata Filtering）：
  ├── 向量检索时注入 metadata filter:
  │     { "dept_id": {"$in": [user.deptId, null]} }
  │     过滤掉不属于本部门且非全局的文档分片
  └── 返回前再做一层 clearance_level 过滤

生成阶段（LLM Prompt）：
  ├── System prompt 注入: "当前用户安全级别: L{clearanceLevel}，
  │     回答中不可引用超出入级别文档中的具体数据"
  └── 如果检索结果中有 accessible=false 的文档，
  │     LLM 应回复"部分信息需要更高权限"
  │     而不是泄漏文档标题

HITL 越权申请：
  ├── 用户在 RAG 界面搜索到但无法查看的文档
  ├── 前端显示"申请访问"按钮
  ├── 创建 sys_notification: notifyType="RAG_APPLY", status=2
  └── 部门管理员审批后可临时查看
```

### 4.3 文档密级定义

| 级别 | 标签 | 可访问者 | 示例 |
|------|------|----------|------|
| 1 | 公开 | 所有认证用户 | 公司制度、操作手册 |
| 2 | 部门内部 | 本部门用户 | 信贷审批内控手册 |
| 3 | 机密 | ROLE_ADMIN + 特批 | 审计报告、风控模型参数 |

清除级别对应关系：用户 `clearance_level` ≥ 文档 `security_level` 即可访问。

---

## 5. HITL 审批回调机制（所有 Agent 共用）

### 5.1 当前缺口

通知审批（[NotificationController.handleAction](../user-service/src/main/java/com/agent/user/controller/NotificationController.java) 第 80-89 行）执行后：
- ✅ status 从 2 → 3（批准）或 4（拒绝）
- ✅ opinion 追加到 content
- ❌ **不会**重新触发 SQL 执行
- ❌ **不会**生成临时文档访问令牌
- ❌ **不会**通知调用方审批结果

### 5.2 应实现的回调流程

```
管理员审批 APPROVE
  │
  ├── notifyType == "RAG_APPLY"
  │     → 生成临时 Redis Token: "rag:temp_access:{userId}:{documentId}"
  │     → TTL = 24h（超时自动回收权限）
  │     → 用户重新请求文档 → DocumentService 检查 Redis 中是否有临时令牌
  │
  └── notifyType == "SQL_AUDIT"
        → 生成临时 Redis Token: "sql:temp_approval:{userId}:{sqlHash}"
        → TTL = 5min（一次性使用）
        → 前端拿到 token 后调用 POST /code/execute?approval=xxx
        → SqlExecutionService 验证 token 后放行
```

对应的前端也需要轮询或通过 WebSocket 接收审批结果通知。

---

## 6. 跨 Agent 共享的用户上下文传递规范

### 6.1 当前状态

task-service 在转发 Agent 调用时，**只传了用户名到 HTTP body 或 query**，没有传角色/权限/部门。各 Agent 各自独立从 HTTP header 取，没有统一约定。

### 6.2 推荐规范

所有 Agent 服务的 Controller 统一接收以下 header：

```java
@RequestHeader("X-User-Name") String username,
@RequestHeader(value = "X-User-Roles", defaultValue = "") String roles,
@RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions
```

或者在 task-service 转发时，将这三个 header 一起转发（而不是只转发 username）：

```java
// task-service TaskServiceImpl 当前实现（简化）
HttpHeaders headers = new HttpHeaders();
headers.set("X-User-Name", username);
// 应补充：
headers.set("X-User-Roles", roles);
headers.set("X-User-Permissions", permissions);
```

### 6.3 权限判断辅助方法

建议在每个 Agent 服务中创建统一的权限工具类：

```java
public class PermissionUtils {
    public static boolean isAdmin(String roles) {
        return StringUtils.hasText(roles) && roles.contains("ROLE_ADMIN");
    }

    public static boolean isDeptAdmin(String roles) {
        return StringUtils.hasText(roles) && roles.contains("ROLE_DEPT_ADMIN");
    }

    public static boolean isDeptAdminOrAbove(String roles) {
        return isAdmin(roles) || isDeptAdmin(roles);
    }
}
```

当前 tool-agent 中 `checkAdminRole` 方法分散在两个 Controller 中各实现一次，应统一。

---

## 7. 优先级排序

| 优先级 | 任务 | 影响范围 |
|--------|------|----------|
| **P0** | Code Agent 表级/列级权限过滤 | 数据安全，当前所有用户能看到全部表 |
| **P0** | Code Agent 敏感列 HITL 阻断 | 合规要求，工资、密码等字段无保护 |
| **P1** | Tool Agent LLM prompt 注入用户上下文 | 工具调用安全，LLM 不知道在为谁工作 |
| **P1** | HITL 审批后回调（SQL 重执行/临时令牌） | 功能完整性，审批后无法继续操作 |
| **P1** | RAG Agent 按本文规范实现 | 新功能，一次性做对 |
| **P2** | Tool Agent schedules 查询权限校验 | 隐私保护 |
| **P2** | Tool Agent 会议室预订权限分级 | 业务规则完善 |
| **P2** | 统一 PermissionUtils 工具类 | 代码质量 |
