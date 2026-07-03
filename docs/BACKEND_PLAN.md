# 后端开发计划（个人）

> 更新时间：2026-06-16（基于代码实际现状核对 + Docker 全量配置完成）
> 负责模块：用户中心（user-service）、任务中心（task-service）、网关（gateway-service）、Docker 部署

---

## 一、截止日期与状态

| 模块 | Deadline | 状态 |
|------|----------|------|
| Docker 全量配置 | — | ✅ 已完成 |
| RBAC 用户权限系统 | **6月20日** | ✅ 已完成 |
| user-service JWT 过滤器 | **6月20日** | ✅ 已完成 |
| Gateway JWT 过滤器 | **6月20日** | ✅ 已完成 |
| code-agent 模块集成及 Docker 化 | **7月1日** | ✅ 已完成 |
| 任务中心（task-service） | **6月30日** | 🔴 未开始 |
| 服务器部署（DigitalOcean） | **6月30日** | 🔴 未开始 |

---

## 二、已完成工作

### ✅ Docker 全量容器化（2026-06-16 完成，2026-07-01 升级支持 Code Agent）

**新增文件：**

| 文件 | 说明 |
|------|------|
| `.env` | ⭐ 核心配置文件，所有端口/密码/API Key 集中在这里，切换环境只改这一个文件（已被 .gitignore 排除） |
| `.env.example` | `.env` 模板，会 commit 到仓库让组员知道需要哪些变量 |
| `docker-compose.yml` | 全量重写，包含 MySQL, Redis, gateway, user-service, task-service, tool-agent, web-ui, code-agent, code-agent-python 共 9 个服务 |
| `user-service/Dockerfile` | 多阶段构建，使用 Maven + standard JRE 17 以支持 ARM64/AMD64 跨平台编译，限制 `-Xmx256m` |
| `gateway-service/Dockerfile` | 同上 |
| `task-service/Dockerfile` | 同上 |
| `tool-agent/Dockerfile` | 同上 |
| `code-agent/Dockerfile` | 同上（为新增的 code-agent 模块构建 Java 镜像） |
| `code-agent/data/Dockerfile` | 新增 python:3.10-slim 镜像，构建 Flask Text-to-SQL 推理服务 |
| `web-ui/Dockerfile` | Node 20 Alpine，Vite dev server，`--host 0.0.0.0` |

**修改与整合工作（2026-07-01 追加）：**
- **修复 Code Agent 路由 404 Bug**：修正 `CodeAgentController` 的 `@RequestMapping` 基地址为 `/code`，以配合网关的 `StripPrefix=1`。
- **网关路由**：在 `gateway-service/application.yml` 添加了 `code-agent` 动态代理映射（路由到 `http://code-agent:8084`），并强制通过 JWT 权限检查过滤器。
- **环境参数化**：将 `code-agent` 的数据库、Redis、Python推理服务 URL 全部配置化，支持环境变量覆盖。
- **开发脚本更新**：在 `dev.sh` 中添加了本地一键拉起全套微服务（包括 `task-service`、`code-agent` 和 Python 推理服务）终端的功能。

**修改文件：**

| 文件 | 改了什么 |
|------|---------|
| `gateway-service/application.yml` | 路由 URI 从硬编码 `localhost` 改为 `${USER_SERVICE_URI:http://localhost:8081}` 等，本地开发不受影响 |
| `tool-agent/application.yml` | API Key 改为 `${AI_DEEPSEEK_API_KEY:默认值}` 格式 |
| `user-service/application.yml` | JWT Secret 改为 `${JWT_SECRET:默认值}` 格式 |
| `web-ui/vite.config.js` | proxy target 改为 `process.env.GATEWAY_URL \|\| 'http://localhost:8080'`；加 `host: 0.0.0.0` |
| `.gitignore` | 追加 `docs/` 排除本地文档 |

**设计要点：**
- `${VAR:默认值}` 语法：有环境变量就用环境变量，没有就用默认值 → **本地不用 Docker 直接跑和之前完全一样**
- 所有 Spring Boot Dockerfile build context 都设为根目录（因为 task-service/tool-agent 的 pom.xml 都声明了根 pom 为 parent）
- 服务间通信用 Docker 服务名（`mysql`、`redis`、`user-service` 等），不用 localhost
- 对外只暴露 gateway (8080) 和 frontend (3000)，其余服务仅内网可访问

**切换本地/云服务器的方法：** 只改 `.env` 文件里的端口/密码即可，其他文件不用动

---

## 三、代码现状（核对结果）

### user-service

| 文件 | 现状 |
|------|------|
| `UserController` | 只有 `/user/login`；`/user/info` 是空壳返回硬编码字符串 |
| `JwtUtil` | ✅ generateToken / parseToken / validate；但 token 里只有单个 `role` 字符串 |
| `SecurityConfig` | ⚠️ `anyRequest().authenticated()` **但没加 JWT Filter**，现在除了 `/user/login` 其他接口全 403 |
| `LoginResponse` | 只有 token/username/role(单字符串)/realName |
| `User` 实体 | role 是普通 String 字段，没有 RBAC 表结构 |

### gateway-service

| 文件 | 现状 |
|------|------|
| `SecurityConfig` | `anyExchange().permitAll()` 完全放行，没有鉴权 |
| `application.yml` | 路由配置完整；Redis 限流已配；URI 已改为环境变量 |

### task-service

| 文件 | 现状 |
|------|------|
| `TaskApplication.java` | 空启动类 |
| `application.yml` | 只有 `server.port: 8082` |

### code-agent & code-agent-python (2026-07-01 合并)

| 模块 | 现状 |
|------|------|
| `code-agent` (Java) | ✅ 已整合。支持自然语言生成 SQL 并通过白名单校验后直接在 MySQL 执行并返回结果。端口 8084。 |
| `code-agent-python` (Python) | ✅ 已整合。提供 Flask + OpenAI/DeepSeek API 推理服务。端口 8090。 |

---

## 四、待开发任务清单

### ✅ 任务1：RBAC 用户权限系统（user-service）（已于6月18日提前完成）
**Deadline：6月20日**

#### 新增数据库表（同步写进 `docker/init/` 的 SQL 脚本）

```sql
sys_role          (id, role_code, role_name, description, status, create_time)
sys_permission    (id, perm_code, perm_name, resource_path, method, type, parent_id)
sys_role_permission (role_id, perm_id)
sys_user_role     (user_id, role_id)
```

#### 新建实体和 Mapper
- [x] `SysRole` / `SysPermission` / `SysUserRole` / `SysRolePermission` 实体
- [x] 对应 Mapper 接口

#### 修改现有代码
- [x] `JwtUtil.generateToken()` 改为接受 `List<String> roles, List<String> permissions`
- [x] `UserServiceImpl.login()` 查询用户角色和权限列表传入 JWT
- [x] `LoginResponse` 加 `List<String> roles` 和 `List<String> permissions`
- [x] **新建 `JwtAuthenticationFilter extends OncePerRequestFilter`**（关键！已修复 403 问题）
- [x] 在 `SecurityConfig` 注册 JWT Filter 并启用方法级安全机制 (`@EnableMethodSecurity`)

#### 新增 API
- [x] `POST /user/register` — 注册（默认分配 `ROLE_USER` 角色）
- [x] `GET /user/info` — 返回当前用户信息+角色+权限
- [x] `GET /admin/users` — 用户列表（需 admin 角色）
- [x] `POST /admin/user/role` — 分配角色（需 admin 角色）
- [x] `PUT /admin/user/status` — 启用/禁用员工（需 admin 角色）
- [x] `GET /admin/roles` — 角色列表（需 admin 角色）

---

### ✅ 任务2：Gateway JWT 过滤器（gateway-service）（已于6月18日提前完成）
**Deadline：6月20日**

> ⚠️ Gateway 用 WebFlux（响应式），不能用 Servlet 的 `OncePerRequestFilter`，要用 `GlobalFilter`

- [x] 新建 `JwtAuthGlobalFilter implements GlobalFilter`
  - 白名单放行：`/api/user/login`、`/api/user/register`
  - 验证通过：解析 JWT，把 username/roles/permissions 写入下游请求 Header (`X-User-Name`, `X-User-Roles`, `X-User-Permissions`)
  - 验证失败：返回 401 JSON
- [x] `application.yml` 白名单路径与 `jwt.secret` 密钥读取配置化

---

### 🔴 任务3：任务中心（task-service）
**Deadline：6月30日**

#### 补全 application.yml

```yaml
spring:
  application:
    name: task-service
  datasource: ...   # 参考 user-service
  data:
    redis: ...
jwt:
  secret: ${JWT_SECRET:...}
```

#### 新增数据库表

```sql
task_record (
  id, task_type ENUM('CODE','RAG','TOOL'),
  status ENUM('INIT','RUNNING','SUCCESS','FAIL'),
  user_id, input TEXT, output TEXT,
  error_msg TEXT, attempt_count INT DEFAULT 0,
  created_at DATETIME, updated_at DATETIME
)
```

#### 实现功能
- [ ] `POST /task/submit` — 提交任务，返回 task_id
- [ ] `GET /task/{id}` — 查询状态和结果
- [ ] `GET /task/list` — 当前用户任务列表
- [ ] 静态路由转发（TOOL → `${TOOL_SERVICE_URI}`；CODE/RAG 等组员接口就绪后填）
- [ ] 状态机：INIT → RUNNING → SUCCESS/FAIL，落库
- [ ] WebSocket 进度推送（参考 `tool-agent/handler/TaskProgressWebSocketHandler.java`）
- [ ] 失败重试（attempt_count < 3）

---

### 🔴 任务4：服务器部署（DigitalOcean）
**Deadline：6月30日**

服务器：`Basic / 1 vCPU / 2 GB RAM / 50 GB Disk`（新加坡区）

- [ ] SSH 登录，安装 Docker + Docker Compose
- [ ] clone 仓库 or 上传文件
- [ ] 创建 `.env` 文件（在服务器上填写生产配置）
- [ ] `docker compose up -d` 启动所有服务
- [ ] 防火墙：只开放 3000（前端）、8080（网关）、22（SSH）
- [ ] 验证：`curl http://<服务器IP>:8080/api/user/login`

---

## 五、服务器资源评估

| 服务 | 估算内存（加 -Xmx256m） |
|------|----------------------|
| MySQL | ~300 MB |
| Redis | ~50 MB |
| gateway-service | ~200 MB |
| user-service | ~250 MB |
| task-service | ~250 MB |
| tool-agent | ~300 MB |
| code-agent (Java) | ~250 MB |
| code-agent-python (Flask) | ~100 MB |
| web-ui (Node) | ~150 MB |
| **合计** | **~1.85 GB** ✅ 可以跑 |

**AI Agent 资源（给组长参考）：**

| Agent | GPU 需求 | 推荐 |
|-------|---------|------|
| Tool Agent | ❌ | 当前服务器即可 |
| Code Agent | ❌ ONNX/LLM CPU | 通过 Flask 调用 API，内存资源开销低 |
| RAG Agent | ✅ 需要 GPU | RunPod / AutoDL 按小时计费 |

---

## 六、开发顺序

```
Week 1（6月16-20日）
  Day 1：设计 RBAC 表 + SQL 迁移脚本
  Day 2：SysRole/Permission 实体 + Mapper + CRUD
  Day 3：JWT 改造（List<String> roles）+ user-service JwtAuthenticationFilter
  Day 4：Gateway JwtAuthGlobalFilter + 白名单
  Day 5：/user/info、/admin/* 接口 + 联调，PR

Week 2（6月23-30日）
  Day 1-2：task-service 基础框架（表 + 接口）
  Day 3-4：状态机 + 路由转发 + WebSocket
  Day 5：服务器部署 + 验证
```

---

## 七、可复用的现有代码

| 可复用 | 位置 | 用途 |
|--------|------|------|
| JWT 验证逻辑 | `user-service/utils/JwtUtil.java` | Gateway Filter 里验证 token |
| WebSocket Handler | `tool-agent/handler/TaskProgressWebSocketHandler.java` | task-service WebSocket 参考 |
| Result 统一响应 | `user-service/dto/Result.java` | task-service 直接复制用 |
| Redis ZSet 操作 | `tool-agent/service/impl/ScheduleServiceImpl.java` | 缓存操作参考 |

---

## 八、重要技术注意事项

1. **Gateway 用 WebFlux**：不能用 `OncePerRequestFilter`，要用 `GlobalFilter` + `ServerWebExchange`
2. **Spring Boot env var binding**：`SPRING_DATASOURCE_URL` 自动覆盖 `spring.datasource.url`，无需改 application.yml
3. **Dockerfile build context 必须是根目录**：因为 task-service/tool-agent 的 pom.xml 声明了根 pom 为 parent，需要 `mvn install -N` 先安装父 pom
4. **JVM 内存限制**：每个服务 `-Xmx256m`，避免 2 GB 服务器 OOM
5. **user-service 当前有 403 bug**：已修复。已注册 `JwtAuthenticationFilter` 并启用方法级权限校验。
