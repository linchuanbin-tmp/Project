# Gateway 路由表 & JWT 认证流程

本文档说明 BankAgent 平台的 API 网关路由映射、JWT 认证全链路、以及微服务间的信任模型。**任何人在新增 API 端点或 WebSocket 通道时，必须了解本文内容。**

---

## 1. 架构概览

```
客户端 (Browser)
    │  HTTP / WebSocket
    ▼
nginx (:80) ─── 静态文件 (web-ui/dist) + 反向代理
    │
    ▼
gateway-service (:8080)  ←── 统一入口：路由 + JWT 验证 + Session 管理
    │
    ├── /api/user/**, /api/admin/**  →  user-service (:8081)
    ├── /api/task/**                 →  task-service (:8082)
    ├── /api/tool/**                 →  tool-agent (:8083)
    ├── /api/code/**                 →  code-agent (:8084)
    ├── /api/dashboard/route         →  Python 推理服务 (:8090)
    ├── /ws/task/**                  →  task-service WebSocket (:8082)
    └── /ws/**                       →  tool-agent WebSocket (:8083)
```

所有请求必须经过 Gateway。**下游服务不直接暴露端口**，不存在绕过 Gateway 访问的路径。

---

## 2. 完整路由表

文件：[gateway-service/src/main/resources/application.yml](../gateway-service/src/main/resources/application.yml)

| 路由 ID | Path 断言 | 上游 URI | 过滤器 | 说明 |
|---------|-----------|----------|--------|------|
| `user-service` | `/api/user/**`, `/api/admin/**` | `USER_SERVICE_URI` (默认 localhost:8081) | StripPrefix=1, **RateLimiter** (10rps, burst 20, 按 IP) | 唯一有速率限制的路由 |
| `task-service` | `/api/task/**` | `TASK_SERVICE_URI` (默认 localhost:8082) | StripPrefix=1 | 无速率限制 |
| `tool-agent` | `/api/tool/**` | `TOOL_SERVICE_URI` (默认 localhost:8083) | StripPrefix=1 | 无速率限制 |
| `code-agent` | `/api/code/**` | `CODE_SERVICE_URI` (默认 localhost:8084) | StripPrefix=1 | 无速率限制 |
| `dashboard-route` | `/api/dashboard/route` | `CODE_AGENT_PYTHON_BASE_URL` (默认 localhost:8090) | **SetPath=/route** | 不是 StripPrefix，是把路径重写为 `/route` |
| `task-ws` | `/ws/task/**` | `TASK_SERVICE_WS_URI` (默认 ws://localhost:8082) | StripPrefix=1 | WebSocket 连接 |
| `tool-ws` | `/ws/**` | `TOOL_SERVICE_WS_URI` (默认 ws://localhost:8083) | **SetPath=/tool/ws** | 兜底 WebSocket 路由，所有 `/ws/` 开头都走 tool-agent |

### 2.1 StripPrefix 行为

`StripPrefix=1` 表示去掉路径的第一段。例如：
- 客户端请求 `/api/user/info` → Gateway 转发给 user-service 时变成 `/user/info`
- user-service 的 Controller 只写 `@GetMapping("/user/info")` 即可

### 2.2 SetPath 行为

`SetPath` 是完全替换路径。例如：
- `/api/dashboard/route` → Python 服务收到的路径是 `/route`
- `/ws/tool/execute` → tool-agent 收到的路径是 `/tool/ws`（所有 `/ws/**` 都映射到 tool-agent 的 WebSocket 处理器）

---

## 3. JWT 认证全链路

### 3.1 白名单

文件：[JwtAuthGlobalFilter.java](../gateway-service/src/main/java/com/agent/gateway/filter/JwtAuthGlobalFilter.java)（第 38-42 行）

以下路径**完全跳过认证**：

| 路径 | 原因 |
|------|------|
| `/api/user/login` | 登录不需要 Token |
| `/api/user/register` | 注册不需要 Token |
| `/ws` 及所有子路径 | WebSocket 连接在 HTTP Upgrade 阶段没有 Authorization header |

注意：**所有 WebSocket 连接都跳过 JWT 验证**。WebSocket 的安全性依赖连接建立后的业务层校验。

### 3.2 认证流程（JwtAuthGlobalFilter, order=-100）

```
1. 请求进入 Gateway
2. 检查路径是否在白名单 → 是 → 直接放行
3. 从 Authorization header 提取 Bearer Token
4. 用 JWT_SECRET 验签并解析 Claims（username, roles, permissions）
5. 查 Redis: GET "session:active:{username}"
   ├── key 不存在 → 返回 401 "Session expired due to inactivity."
   ├── key 存在但 value != 当前 token → 返回 401（说明已被其他设备登录挤掉）
   └── key 存在且 value == 当前 token → 验证通过
6. 滑动窗口续期：
   ├── 如果是轮询请求（路径以 /notification/unread-count 结尾）→ 跳过续期
   └── 否则 → Redis EXPIRE 重置 TTL
7. 将用户信息注入请求头，转发给下游服务
```

### 3.3 注入下游的请求头

Gateway 在转发前向 HTTP 请求添加三个自定义头：

| Header | 内容 | 示例 |
|--------|------|------|
| `X-User-Name` | 用户名 | `zhangsan` |
| `X-User-Roles` | 逗号分隔的角色列表 | `ROLE_ADMIN,ROLE_USER` |
| `X-User-Permissions` | 逗号分隔的权限列表 | `user:read,user:write` |

下游服务从这些头读取用户信息。**不需要再次解析 JWT**。

### 3.4 单会话强制

Gateway 的 Session 检查逻辑：同一用户同时只能有一个有效的 Session。如果用户 A 在设备 1 登录后又在设备 2 登录，设备 2 的 Token 会覆盖 Redis 中 `session:active:{A}` 的值，设备 1 下次请求时会被 401。

### 3.5 Gateway 401 响应格式

Gateway 在认证失败时直接返回 401，响应体是：

```json
{"code": 401, "message": "Session expired due to inactivity."}
```

这是**原始 JSON 字符串**，不是 Controller 返回的 `Result<T>` 包装。前端 axios 拦截器已兼容这两种格式。

---

## 4. 下游服务安全模型

### 4.1 Gateway → 下游：基于信任头

- Gateway 已验证 JWT，下游服务**信任** `X-User-*` 头
- 下游服务**不重新验证 Token**（不需要 JWT_SECRET，task-service 的 jwt.secret 配置项实际上是冗余的）
- 安全前提：**Docker 网络隔离**，下游服务不暴露公网端口

### 4.2 user-service 二次验证

user-service 有自己的 `JwtAuthenticationFilter`（与 Gateway 的 Filter 独立），原因：
- user-service 在开发环境可单独启动调试（不经过 Gateway）
- 在 Docker 环境下，user-service 仍依赖 Gateway 注入的 `X-User-Name` 头获取当前用户

### 4.3 task-service / tool-agent / code-agent

这些服务**完全不设防**——无 JWT 过滤器，无 Security 配置。它们 100% 依赖 Gateway 的认证和 Docker 网络隔离。

task-service 的 `/task/list/all` 端点手动检查 `X-User-Roles` 头中是否包含 `ROLE_ADMIN`，但这是一个业务校验而非安全措施（头可能被伪造，如果攻击者绕过 Gateway）。

---

## 5. 环境变量速查

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `USER_SERVICE_URI` | user-service 地址 | `http://localhost:8081` |
| `TASK_SERVICE_URI` | task-service 地址 | `http://localhost:8082` |
| `TOOL_SERVICE_URI` | tool-agent 地址 | `http://localhost:8083` |
| `CODE_SERVICE_URI` | code-agent 地址 | `http://localhost:8084` |
| `TOOL_SERVICE_WS_URI` | tool-agent WebSocket | `ws://localhost:8083` |
| `TASK_SERVICE_WS_URI` | task-service WebSocket | `ws://localhost:8082` |
| `CODE_AGENT_PYTHON_BASE_URL` | Python 推理服务 | `http://localhost:8090` |
| `JWT_SECRET` | JWT 签名密钥 | 必须设置（至少 32 字符） |
| `SPRING_DATA_REDIS_HOST` | Redis 地址 | `localhost` |

注意：Gateway 的 Redis 配置在 `application.yml` 中硬编码为 `localhost:6379`，不走环境变量。在 Docker 环境中 Redis host 需要是容器名 `redis`——已在 `docker-compose.yml` 中通过 `SPRING_DATA_REDIS_HOST` 覆盖。

---

## 6. 常见操作指南

### 新增一个公开 API（无需登录）

在 `JwtAuthGlobalFilter.java` 的 `WHITELIST` 中添加路径前缀。

### 新增一个需要认证的 API

1. 在对应微服务的 Controller 中添加端点
2. 在 `application.yml` 中确认路由的 Path 断言能匹配新端点
3. 如果新端点属于已有路由前缀（如 `/api/user/xxx`），无需修改 Gateway 配置
4. Controller 中通过 `@RequestHeader("X-User-Name")` 获取当前用户

### 新增一个 WebSocket 端点

1. 如果属于 task-service：确认路径以 `/ws/task/` 开头
2. 如果属于 tool-agent：确认路径以 `/ws/` 开头，在 tool-agent 中注册 WebSocket handler
3. WebSocket 路径会自动加入白名单（以 `/ws` 开头），无需修改 Filter

### Session 超时时间调整

管理员通过 Settings 页面（`SystemConfigController`）修改，或直接改 Redis 键 `sys:config:session_timeout`（单位：分钟，默认 30）。
