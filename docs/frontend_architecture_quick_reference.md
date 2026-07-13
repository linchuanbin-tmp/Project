# 前端架构速查

本文档面向需要在前端新增页面、API 调用、或修改现有功能的开发者。覆盖路由守卫、多 Tab 系统、API 调用规范、状态管理、i18n、通知系统、WebSocket 等核心机制。

---

## 1. 路由系统

文件：[web-ui/src/router/index.ts](../web-ui/src/router/index.ts)

### 1.1 路由结构

```
/                           → AuthLayout（公开页面壳子）
  /login                    → 登录页
  /register                 → 注册页
/app                        → MainLayout（认证页面壳子）
  /app/dashboard            → Dashboard
  /app/tool                 → Tool Agent
  /app/code                 → Code Agent
  /app/rag                  → RAG Agent
  /app/dept-docs            → 部门文档库
  /app/my-tasks             → 我的任务
  /app/my-schedules         → 我的日程
  /app/notification         → 消息中心
  /app/settings             → 设置
  /app/admin/users          → 用户管理（仅 ROLE_ADMIN）
  /app/admin/resources      → 资源管理（仅 ROLE_ADMIN）
  /app/admin/my-dept        → 部门管理（ROLE_DEPT_ADMIN 或 ROLE_ADMIN）
  /app/admin/task-center    → 任务中心（仅 ROLE_ADMIN）
/:pathMatch(.*)*            → 兜底，重定向到 /login
```

### 1.2 路由守卫（五层检查，第 126-175 行）

```typescript
router.beforeEach(async (to, _from, next) => {
    // 1. 刷新恢复：有 token 但无 userInfo → 静默调用 /user/info
    // 2. 鉴权：非公开页面 + 未登录 → 跳 /login
    // 3. 反跳：已登录访问 /login 或 /register → 跳 /app/dashboard
    // 4. 单角色检查：meta.requiresRole === 'ROLE_ADMIN'
    // 5. 多角色检查：meta.requiresAnyRole === ['ROLE_DEPT_ADMIN', 'ROLE_ADMIN']
})
```

### 1.3 新增页面的步骤

1. 在 `router/index.ts` 的 `/app` children 中添加路由，设置 `meta.title`
2. 在 `src/views/` 下创建 Vue 组件
3. 如需角色控制：加 `meta: { requiresRole: 'ROLE_ADMIN' }` 或 `meta: { requiresAnyRole: [...] }`
4. **不需要**手动注册 Tab——路由切换时自动创建（见下文）

---

## 2. 多 Tab 系统

文件：[web-ui/src/layouts/MainLayout.vue](../web-ui/src/layouts/MainLayout.vue)（第 154-272 行）

### 2.1 工作原理

- `openTabs` 响应式数组存储所有打开的 Tab
- `watch(route.path)` 自动检测新路径并添加 Tab
- 切换 Tab 使用 `router.push(path)`，组件由 `<keep-alive>` 缓存——**不销毁、不重载**，滚动位置和表单状态保持
- 关闭 Tab 时递增 `cacheKeys[path]` 计数器，作为 `<keep-alive>` 的 key 后缀，强制清除该页面的缓存

### 2.2 Tab 标签的 i18n 映射

Tab 标题通过 `metaKey` 映射到 `$t('menu.xxx')`。新页面的路径需要在 `MainLayout.vue` 第 253-266 行的 `watch` 中添加 `metaKey` 映射，否则标签会 fallback 到 `route.meta.title`。

---

## 3. API 调用规范

文件：[web-ui/src/utils/request.ts](../web-ui/src/utils/request.ts)

### 3.1 Axios 实例配置

```typescript
baseURL: '/api'      // Vite 代理到 Gateway，生产环境由 nginx 处理
timeout: 60000       // 60 秒（兼容 LLM 推理的长时间等待）
```

### 3.2 自动行为

| 环节 | 行为 |
|------|------|
| 请求发送前 | 自动从 `userStore.token` 读取并附加 `Authorization: Bearer <token>` |
| 响应到达后 | 如果 `res.code === 200`：自动解包，返回 `res.data` |
| 响应到达后 | 如果 `res.code === 401`：静默退出，跳 `/login?expired=1` |
| 响应到达后 | 如果 `res.code === 500`：`ElMessage.error` 显示 `message` 字段 |
| HTTP 错误 | 401 → 同 code 401；其他 → 显示错误提示 |
| 兼容性 | 如果响应体没有 `code` 字段（非 Result 格式），直接返回原始数据 |

### 3.3 API 模块组织

所有 API 函数统一放在 `src/api/` 目录下：

| 文件 | 覆盖范围 |
|------|----------|
| `api/task.ts` | 任务提交、轮询、历史 |
| `api/code.ts` | SQL 生成、执行、HITL 覆盖 |
| `api/tool.ts` | 会议室、日程冲突、路径规划 |
| `api/notification.ts` | 消息列表、未读数、线程、标记已读、审批动作 |
| `api/department.ts` | 成员管理、文档 CRUD、部门 CRUD |

### 3.4 新增 API 调用的步骤

1. 在 `src/api/` 下新建或复用文件
2. `import request from '@utils/request'`
3. 导出 async 函数，调用 `request.get/post/put/delete`
4. 调用方直接 `await` 拿到的是解包后的 `data` 字段

```typescript
// src/api/example.ts
import request from '@utils/request'
export const getExample = (id: number) => request.get(`/user/example/${id}`)

// 组件中使用
const data = await getExample(123)  // data 已经是 { id: 123, name: "..." }
```

---

## 4. 状态管理（Pinia）

### 4.1 Store 清单

| Store | 文件 | 职责 |
|-------|------|------|
| `useUserStore` | `stores/modules/user.ts` | Token、用户信息、登录/登出 |
| `useTaskStore` | `stores/modules/task.ts` | 当前任务列表、任务状态追踪 |

### 4.2 userStore 关键 API

```typescript
userStore.token          // 当前 JWT Token（同步到 localStorage）
userStore.userInfo       // 用户信息对象（包含 roles, permissions, deptName, clearanceLevel）
userStore.isLoggedIn     // computed: !!token
userStore.login(user, pass)  // POST /user/login → 存 token → GET /user/info → 弹通知
userStore.logout()       // POST /user/logout → 清状态 → 跳 /login → 弹通知
userStore.logout(true)   // 静默登出（Token 过期时用，不调接口不弹通知）
userStore.getUserInfo()  // GET /user/info，刷新时恢复用户信息
```

### 4.3 新增 Store 的步骤

1. 在 `stores/modules/` 下创建文件，使用 `defineStore` + Composition API
2. 在 `stores/index.ts` 中 re-export

---

## 5. i18n 国际化

### 5.1 语言文件

| 文件 | 语言 |
|------|------|
| `locales/en.ts` | English（默认） |
| `locales/zh-CN.ts` | 简体中文 |
| `locales/zh-TW.ts` | 繁體中文 |

### 5.2 使用方式

```html
<!-- 模板中 -->
<span>{{ $t('menu.dashboard') }}</span>

<!-- script 中 -->
<script setup>
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const label = t('common.save')
</script>
```

### 5.3 语言切换机制

- 用户在 Settings 页面选择语言 → `locale.value = 'en'`
- `App.vue` 的 `watch(locale)` 自动同步 `document.documentElement.lang` 和 `localStorage`
- Element Plus 组件库的 locale 通过 `<el-config-provider>` 自动跟随

### 5.4 新增翻译条目的步骤

在三个 locale 文件中**各加一份**对应的 key。JSON 结构按功能分为 `menu`、`dashboard`、`code`、`tool`、`settings`、`adminUsers` 等顶层 key。

---

## 6. 通知系统

### 6.1 UI 层

- `ElNotification`：用于操作成功/失败的飘入通知（macOS 风格毛玻璃效果），在 `App.vue` 中全局定义样式
- `ElMessage`：用于表单验证等轻量提示，同样毛玻璃风格，固定在右侧而非顶部居中

### 6.2 何处自动触发

| 场景 | 触发方式 |
|------|----------|
| 登录成功 | `userStore.login()` 中调用 `ElNotification` |
| 登出 | `userStore.logout()` 中调用 `ElNotification` |
| API 业务错误 | `request.ts` 响应拦截器自动 `ElMessage.error` |
| API 网络错误 | `request.ts` 响应拦截器自动 `ElMessage.error` |

### 6.3 未读消息轮询

`MainLayout.vue` 每 10 秒轮询 `GET /api/user/notification/unread-count`，结果显示为：
- 侧边栏 "Messages" 菜单项的红点 badge
- Copilot Widget 按钮上的红点

---

## 7. WebSocket 客户端

文件：[web-ui/src/utils/websocket.ts](../web-ui/src/utils/websocket.ts)

### 7.1 连接流程

```typescript
import { wsClient } from '@utils/websocket'

// 1. 先 POST 获取 taskId
const task = await submitTask({ taskType: 'TOOL', query: '...' })

// 2. 连接 WebSocket
wsClient.connect(`ws://host/ws/task/progress?taskId=${task.id}`)

// 3. 监听事件
wsClient.on('open', () => { /* 连接成功 */ })
wsClient.on('message', (data) => { /* 收到进度推送 */ })
wsClient.on('close', () => { /* 连接关闭 */ })
wsClient.on('error', (err) => { /* 连接错误 */ })

// 4. 用完关闭
wsClient.close()
```

### 7.2 重要机制

- **自动重连**：最多 3 次，间隔 2 秒
- **轮询兜底**：在 `'open'` 事件中启动 `setInterval` 每 500ms 轮询 `GET /api/task/{id}`，防止 WebSocket 连接建立时任务已经完成而错过消息
- **单例模式**：全局只有一个 `wsClient` 实例，一个 WebSocket 连接

---

## 8. 全局 Copilot Widget

文件：[web-ui/src/components/CopilotWidget.vue](../web-ui/src/components/CopilotWidget.vue)

- 固定在 `MainLayout.vue` 中渲染（所有认证页面可见）
- 右下角悬浮圆形按钮 → 点击展开 440×620 面板
- 意图路由：用户输入文本 → `POST /api/dashboard/route` → 得到 intent 分类 → 分发给对应处理逻辑
- 聊天记录持久化在 `localStorage` key `copilot_chat_history`
- TOOL 类任务完成后通过 `window.dispatchEvent(new CustomEvent('copilot-tool-result', ...))` 通知当前页面
- **如果新增 intent 类型**：在 `submitQuery()` 方法中添加对应的分发逻辑

---

## 9. 快速排查

| 问题 | 排查方向 |
|------|----------|
| 新页面路由打不开 | 检查是否在 `/app` children 下，是否有 `meta.public` 或角色限制 |
| API 返回数据是 `undefined` | 后端可能没包装 `Result<T>`，检查拦截器兼容逻辑 |
| Tab 标签显示英文 key 而非翻译 | 在 MainLayout.vue 的 metaKey 映射中添加路径 |
| 切换 Tab 后状态丢失 | 检查是否在关闭 Tab 时意外清了 `cacheKeys` |
| WebSocket 连不上 | 检查 Vite 代理配置的 `ws: true`，检查 Gateway 的 tool-ws 路由 |
| Token 频繁过期 | 检查 Redis 中 `session:active:{username}` 的 TTL；轮询接口应排除在续期外 |
