# 通知系统 & HITL 人机协同消息流程

本文档说明 BankAgent 平台的消息通知系统，包括通知生命周期、消息线程模型、HITL（Human-in-the-Loop）审批流程、以及前后端的交互方式。

---

## 1. 数据模型

表名：`sys_notification`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | bigint | 主键，自增 |
| `sender_id` | bigint | 发送者用户 ID |
| `receiver_id` | bigint | 接收者用户 ID |
| `title` | varchar | 消息标题 |
| `content` | text | 消息正文 |
| `notify_type` | varchar | 通知类型，见下表 |
| `status` | int | 通知状态，见下表 |
| `payload` | text | JSON 格式的附加数据（如 `documentId`、SQL 语句等） |
| `parent_id` | bigint | 父消息 ID（回复链） |
| `thread_id` | bigint | 对话线程 ID |
| `create_time` | datetime | 创建时间 |
| `update_time` | datetime | 更新时间 |
| `deleted` | int | 逻辑删除（MyBatis-Plus @TableLogic） |

---

## 2. 通知状态机

```
0 (未读) ──读──→ 1 (已读)
                    │
                    ▼
0 (未读) ──需审批──→ 2 (待处理) ──批准──→ 3 (已批准)
                    │              │
                    │              └──拒绝──→ 4 (已拒绝)
                    │
                    └── 普通消息收到即1 (已读)，不会进入2
```

| 状态值 | 含义 | 触发条件 |
|--------|------|----------|
| 0 | 未读 | 新消息到达 |
| 1 | 已读 | 用户点击查看 |
| 2 | 待处理 | HITL 消息需要管理员审批（如 RAG 越权申请、SQL 审计阻断） |
| 3 | 已批准 | 管理员批准 |
| 4 | 已拒绝 | 管理员拒绝 |

---

## 3. 通知类型 (`notify_type`)

| 类型 | 用途 | 走 HITL 审批？ |
|------|------|:---:|
| `RAG_APPLY` | 用户申请访问超出自己权限的文档 | 是（status 进入 2，需要管理员批准/拒绝） |
| `SQL_AUDIT` | Code Agent 生成的 SQL 包含敏感操作（DELETE/DROP/UPDATE）被拦截 | 是 |
| 其他自定义类型 | 管理员群发消息等 | 否（status 通常直接为 0 → 1） |

---

## 4. API 端点

所有端点前缀：`/api/user/notification`

### 4.1 用户端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list?status=&notifyType=` | 获取当前用户的通知列表，可按状态和类型筛选 |
| GET | `/unread-count` | 获取未读数量（前端 10 秒轮询此接口） |
| GET | `/thread/{threadId}` | 获取对话线程的全部消息（需验证用户是参与者） |
| POST | `/send` | 发送消息给其他用户 |
| PUT | `/read/{id}` | 将单条通知标记为已读（验证接收者是当前用户） |
| POST | `/action` | 对待处理通知执行批准/拒绝（仅管理员） |

### 4.2 发送消息请求体

```json
{
  "receiverId": 2,
  "title": "文档越权申请",
  "content": "用户 zhangsan 申请查看《信贷审批内控手册》",
  "notifyType": "RAG_APPLY",
  "payload": "{\"documentId\": 42}",
  "parentId": null
}
```

### 4.3 审批动作请求体

```json
{
  "notificationId": 42,
  "action": "APPROVE",
  "opinion": "该员工有合理业务需求，批准本次访问"
}
```

`action` 取值：`APPROVE`（批准）或 `DENY`（拒绝）。

---

## 5. 消息线程模型

### 5.1 回复链

系统支持消息回复和多轮对话：

1. A 给 B 发了一条消息 → `parentId = null`，`threadId = null`
2. B 回复 A 的消息 → `parentId = 第一条消息的 id`
3. 系统在发送回复时自动创建 `threadId`（被设为第一条消息的 `id`）
4. 后续回复自动继承 `threadId`

### 5.2 获取线程

`GET /thread/{threadId}` 返回该线程的全部消息（按时间排序）。系统会验证请求者至少是线程中**一条消息的发送者或接收者**，防止越权查看他人对话。

---

## 6. HITL 审批流程

### 6.1 RAG 文档越权申请

```
1. 用户尝试访问超出权限的文档
2. 后端 DocumentService 检测到权限不足 → 创建一条 notifyType="RAG_APPLY" 的通知
   - payload 包含 JSON: {"documentId": 42}
   - status = 2（待处理）
3. 部门管理员在消息中心看到待审批通知
4. 管理员点击 APPROVE 或 DENY
5. 后端 NotificationService.handleAction() 更新 status 为 3 或 4
6. 如果批准：用户下次访问该文档时，DocumentService 检查到有已批准的通知 → 放行
7. 如果拒绝：文档保持不可访问
```

关键实现：[DocumentServiceImpl.java](../user-service/src/main/java/com/agent/user/service/impl/SysDocumentServiceImpl.java) 第 86-99 行，通过解析 `payload` JSON 中的 `documentId` 字段来匹配对应的审批记录。

### 6.2 SQL 审计阻断

```
1. Code Agent 生成的 SQL 包含敏感操作（DELETE、DROP、UPDATE 等）
2. SQL AST 审计引擎检测到 → 阻断执行 → 创建 notifyType="SQL_AUDIT" 的通知
   - payload 包含 JSON: 原始 SQL 语句
   - status = 2（待处理）
3. 管理员审批后决定是否允许执行
```

注意：当前审批动作（`handleAction`）**只更新通知状态**（2 → 3 或 4），不自动触发下游操作（如重新执行 SQL、生成临时令牌等）。审批结果由业务层通过查询通知状态来判断。

---

## 7. 前端交互

### 7.1 未读数轮询

`MainLayout.vue` 每 10 秒调用 `GET /user/notification/unread-count`，结果展示在：
- 侧边栏 "Messages" 菜单项的红色数字 badge
- Copilot 悬浮按钮的红色圆点

### 7.2 消息列表页

路由：`/app/notification`，组件：[views/notification/index.vue](../web-ui/src/views/notification/index.vue)

功能：
- 按状态筛选（全部 / 未读 / 待处理）
- 点击消息查看详情 + 回复
- 待处理消息显示 APPROVE / DENY 按钮（仅管理员可见）

### 7.3 API 调用

前端 API 封装在 [api/notification.ts](../web-ui/src/api/notification.ts)：

```typescript
import { getNotifications, getUnreadCount, sendNotification, markAsRead, handleAction, getThread } from '@/api/notification'

// 获取未读数（轮询用）
const count = await getUnreadCount()

// 获取通知列表
const list = await getNotifications({ status: 2, notifyType: 'RAG_APPLY' })

// 发送消息
await sendNotification({ receiverId: 2, title: '标题', content: '内容' })

// 标记已读
await markAsRead(notificationId)

// 审批
await handleAction({ notificationId: 42, action: 'APPROVE', opinion: '同意' })

// 查看线程
const thread = await getThread(threadId)
```

### 7.4 Gateway 轮询豁免

通知未读数轮询 (`/api/user/notification/unread-count`) 是唯一被 Gateway 的 Session 滑动窗口排除在外的接口——轮询请求**不刷新 Session TTL**。这避免了因后台轮询导致 Session 无限期存活。

> 豁免逻辑硬编码在 [JwtAuthGlobalFilter.java](../gateway-service/src/main/java/com/agent/gateway/filter/JwtAuthGlobalFilter.java) 第 86-87 行。如果将来新增轮询类接口，需要同步更新该处。

---

## 8. 当前已知限制

1. **审批后无自动回调**：`handleAction` 更新状态后不会自动触发 SQL 重新执行或生成临时访问令牌。需要业务层主动查询通知状态。
2. **无批量审批**：每条待处理通知需要单独调用 `/action`。
3. **无推送机制**：前端依赖 10 秒轮询获取未读数，没有 WebSocket 推送。
4. **`/user/list` 无权限控制**：任何认证用户都可以获取全量用户列表（发送消息时选接收人用），可能造成隐私泄露。
