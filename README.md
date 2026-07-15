# 🚀 BankAgent

> 🌐 **服务器部署信息**：
> - **测试服务器公网 IP**：`167.172.82.161`
> - **部署状态**：目前云服务器部署正在完善中，域名解析暂未配置。本地开发与演示时，可直接通过 Docker 进行全量或半容器化本地调试。
> 
> 💡 **Docker 部署说明**：
> 本项目支持使用 **Docker Compose** 进行一键全量容器化部署，或者使用半容器化方案进行本地开发调试。
> 详细操作步骤请参阅 [Docker 部署与开发指南 (README_DOCKER.md)](./README_DOCKER.md)。

---

## 一、环境要求

部署前请确认以下环境已安装，版本必须匹配（完整版本信息在第8）：

| 依赖 | 版本 | 用途 | 验证命令 |
|------|------|------|----------|
| Java | 17  | Spring Boot 3.x 运行环境 | `java -version` |
| Maven | 3.9+ | 后端构建 | `mvn -v` |
| MySQL | 8.0.33+ | 业务数据库 | `mysql --version` |
| Redis | 5.0+ | 缓存 + 限流 + 日程冲突检测 | `redis-cli --version` |
| Docker Desktop | 最新稳定版 | 本地启动 MySQL、Redis、MinIO、Milvus、etcd | `docker --version` |
| Python | 3.10+ | 本地 RAG Embedding Worker | `python --version` |
| Node.js | 18.16+ (LTS) | 前端构建 | `node -v` |
| npm | 9.5+ | 前端包管理 | `npm -v` |

⚠️ 注意：
- Spring Boot 3.x **不支持 Java 8/11**，必须用 Java 17+
- MySQL 8.0 和 5.7 的认证插件不同，本项目使用 `mysql_native_password`
- Redis 必须启动，否则 Gateway 限流和日程冲突检测会报错

------------------------------------------------------------------------------------------------------------------------

## 二、项目结构详解

```
BankAgent/
├── 📁 gateway-service/              # 网关服务 (端口 8080)
│   ├── src/main/java/com/agent/gateway/
│   │   ├── config/SecurityConfig.java       # 跨域 + 安全放行与统一路由鉴权
│   │   └── GatewayServiceApplication.java   # 网关启动类
│   ├── src/main/resources/
│   │   └── application.yml                  # 路由规则配置（核心转发逻辑）
│   └── pom.xml                              # Spring Cloud Gateway 依赖
│
├── 📁 user-service/                 # 用户与部门中心 (端口 8081)
│   ├── src/main/java/com/agent/user/
│   │   ├── controller/              # LoginController, DepartmentController, DocumentController, NotificationController
│   │   ├── service/impl/            # UserServiceImpl, SysDepartmentServiceImpl, SysDocumentServiceImpl, SysNotificationServiceImpl
│   │   ├── mapper/                  # MyBatis-Plus 数据层接口映射
│   │   └── utils/JwtUtil.java       # JWT 工具类
│   └── pom.xml
│
├── 📁 task-service/                 # 任务执行中心 (端口 8082)
│   ├── src/main/java/com/agent/task/
│   │   ├── controller/TaskController.java   # 任务提交、协作审计接口
│   │   └── service/impl/TaskServiceImpl.java # 协同任务状态机核心处理
│   └── pom.xml
│
├── 📁 tool-agent/                   # 工具智能体核心 (端口 8083)
│   ├── src/main/java/com/agent/tool/
│   │   ├── controller/ToolController.java   # 会议室、日程预定、路线规划及大模型调用接口
│   │   └── config/WebSocketConfig.java      # 智能体交互 WebSocket 注册 (/tool/ws)
│   └── pom.xml
│
├── 📁 code-agent/                   # 代码执行审计智能体 (端口 8084)
│   ├── src/main/java/com/agent/code/        # Java 审计与熔断控制
│   └── data/                                # Python 执行子容器 (MySQL 真实隔离沙箱环境)
│
├── 📁 rag-agent/                    # RAG 知识库问答智能体 (端口 8085)
│   ├── src/main/java/com/agent/rag/
│   │   ├── controller/              # RAG 问答、索引、知识库、权限申请接口
│   │   ├── service/impl/            # 文档解析、切分、Embedding、Milvus 检索、权限过滤
│   │   ├── mapper/                  # RAG 表、用户、文档、通知相关 Mapper
│   │   └── RagAgentApplication.java # RAG Agent 启动类
│   └── pom.xml
│
├── 📁 rag-worker/                   # 本地 Embedding Worker (端口 8091)
│   ├── app.py                       # FastAPI Embedding 服务
│   ├── requirements.txt             # sentence-transformers / torch / fastapi 依赖
│   └── Dockerfile
│
├── 📁 web-ui/                       # Vue 3 前端工程 (端口 3000)
│   ├── src/
│   │   ├── views/
│   │   │   ├── tool/index.vue       # 工具 Agent (会议/日程/路线)
│   │   │   ├── code/index.vue       # SQL Agent (Text-to-SQL 与安全执行)
│   │   │   ├── rag/index.vue        # RAG Agent (向量召回与文档隔离阅览)
│   │   │   ├── document/index.vue   # 知识资产库 (文档就地增删改管理)
│   │   │   ├── admin/MyDepartment.vue # 组织架构管理 (成员名册与多级部门管理)
│   │   │   └── notification/index.vue # 消息中心 (系统通知、RAG 提权审批、SQL 执行拦截审核)
│   │   ├── api/                     # 封装 Axios 请求 (department.ts, tool.ts, etc.)
│   │   └── components/              # 通用组件
│   └── vite.config.js               # Vite 代理与打包配置
│
├── 📁 docker/                       # Docker 数据库与 Nginx 启动初始化配置
│   ├── init/                        # 数据库初始化 SQL 脚本
│   └── nginx/                       # nginx.conf 反向代理配置
│
├── 📁 scripts/                      # 本地开发验证脚本
│   ├── start-rag-worker.ps1         # 启动本地 Embedding Worker
│   ├── test-rag-embedding.ps1       # 验证 Embedding 服务
│   └── test-rag-agent.ps1           # 验证 RAG Agent 检索链路
│
├── 📄 docker-compose.yml            # 容器化部署基础配置
├── 📄 docker-compose.prod.yml       # 生产环境容器化服务挂载重写
├── 📄 deploy-prod.sh                # 生产环境一键构建部署脚本
├── 📄 pom.xml                       # 父工程 Maven 配置 (聚合构建所有模块)
└── 📄 README.md                     # 本文件
```

------------------------------------------------------------------------------------------------------------------------

## 三、数据库部署（MySQL）

### 3.1 启动 MySQL 服务

**Windows（推荐开发环境方式）**：
```powershell
# 进入 MySQL bin 目录（根据实际安装路径调整）
cd "C:\Program Files\MySQL\MySQL Server 8.0\bin"

# 手动启动（前台运行，方便看日志）
mysqld --console

# 或作为 Windows 服务启动（需先修复服务配置）
net start MySQL80
```

**Linux/macOS**：
```bash
sudo systemctl start mysql
# 或
sudo service mysql start
```

**验证**：
```bash
mysql -u root -p -e "SELECT 1;"
# 应返回 +---+
#       | 1 |
#       +---+
```

### 3.2 导入数据库

```bash
# 1. 登录 MySQL
mysql -u root -p

# 2. 创建数据库（使用 utf8mb4 支持中文和 Emoji）
CREATE DATABASE IF NOT EXISTS agent_platform 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

# 3. 退出
exit

# 4. 导入备份文件（在项目根目录执行）
mysql -u root -p agent_platform < agent_platform_backup.sql

# 5. 验证表是否创建成功
mysql -u root -p -e "USE agent_platform; SHOW TABLES;"
# 应显示：meeting_room, meeting_schedule, sys_user
```

### 3.3 数据库表结构说明

| 表名 | 字段 | 说明                         |
|------|------|----------------------------|
| `sys_user` | id, username, password, real_name, role, status, dept_id, clearance_level, create_time | 用户登录账户表，新增部门绑定 (dept_id) 与安全密级 (clearance_level: 1=公开, 2=内部, 3=机密) |
| `sys_department` | id, dept_name, description, parent_id, status, create_time | 组织架构部门管理表 |
| `sys_document` | id, title, content, dept_id, security_level, create_time, file_type, file_size, minio_object_key, parse_status | 知识资产库文档表，保存文档元数据、解析正文、MinIO 文件位置和解析状态 |
| `sys_notification` | id, sender_id, receiver_id, title, content, notify_type, status, payload, opinion, create_time, update_time, deleted | 系统消息通知与审批事务表，支持 RAG 提权及 SQL 执行审核 |
| `rag_knowledge_base` | id, name, description, owner_id, dept_id, status, create_time, update_time | RAG 知识库表，用于按知识库组织文档集合 |
| `rag_source_document` | id, kb_id, sys_document_id, title, file_type, object_key, parse_status, create_time | RAG 原始文档映射表，关联知识库、上传文件和系统文档 |
| `rag_document_chunk` | id, document_id, chunk_index, content, vector_id, security_level, dept_id, create_time | 文档分块记录表，和 Milvus 向量记录一一对应 |
| `rag_query_log` | id, trace_id, username, question, status, top_k, latency_ms, create_time | RAG 问答日志表，用于追踪检索、权限过滤和回答状态 |
| `meeting_room` | id, room_name, floor, capacity, facilities, status | 会议室资源表，预设 3 条数据            |
| `meeting_schedule` | id, room_id, booker, start_time, end_time, topic, status | 预定记录表，room_id=0 表示个人日程     |

**默认测试数据（默认密码均为 `123456`）**：
- **超级管理员**：
  - 账号：`admin` (全局管理员，密级：Confidential/Level-3)
- **信贷部门 (Credit Department)**：
  - 部门管理员：`credit_mgr` (密级：Internal/Level-2)
  - 部门员工：`credit_staff` (密级：Public/Level-1)
- **合规部门 (Compliance Department)**：
  - 部门管理员：`compliance_mgr` (密级：Internal/Level-2)
  - 部门员工：`compliance_staff` (密级：Public/Level-1)
- **预置会议室**：301会议室(10人)、302会议室(20人)、501大会议室(50人)

---

## 四、缓存部署（Redis）

### 4.1 启动 Redis

**Windows**：
```powershell
# 方式一：直接启动（前台）
redis-server

# 方式二：后台运行
redis-server --daemonize yes
```

**Linux/macOS**：
```bash
redis-server
# 或后台运行
redis-server --daemonize yes
```
------------------------------------------------------------------------------------------------------------------------

## 五、后端服务部署

### 5.1 启动顺序（必须按顺序）

| 顺序 | 服务 | 端口 | 启动命令 | 验证方式 |
|------|------|------|----------|----------|
| 1 | MySQL | 3306 | `docker compose up -d mysql` 或 `mysqld --console` | `mysql -u root -p` |
| 2 | Redis | 6379 | `docker compose up -d redis` 或 `redis-server` | `redis-cli ping` |
| 3 | MinIO | 9000 / 9001 | `docker compose up -d minio` | `http://localhost:9001` |
| 4 | Milvus + etcd | 19530 / 9091 | `docker compose up -d etcd milvus` | `docker compose ps milvus` |
| 5 | RAG Worker | 8091 | `.\scripts\start-rag-worker.ps1` | `.\scripts\test-rag-embedding.ps1 -SkipRagHealth` |
| 6 | User Service | 8081 | `.\mvnw.cmd -pl user-service spring-boot:run` | 查看 `Started UserServiceApplication` |
| 7 | Task Service | 8082 | `.\mvnw.cmd -pl task-service spring-boot:run` | 查看 `Started TaskServiceApplication` |
| 8 | Tool Agent | 8083 | `.\mvnw.cmd -pl tool-agent spring-boot:run` | `curl http://localhost:8083/tool/meeting-rooms` |
| 9 | Code Agent | 8084 | `.\mvnw.cmd -pl code-agent spring-boot:run` | 查看 `Started CodeAgentApplication` |
| 10 | RAG Agent | 8085 | `.\mvnw.cmd -pl rag-agent spring-boot:run` | `curl http://localhost:8085/rag/health` |
| 11 | Gateway | 8080 | `.\mvnw.cmd -pl gateway-service spring-boot:run` | `curl http://localhost:8080` |

**注意**：
- 本地开发可以先用 `docker compose up -d mysql redis etcd minio milvus` 一次性启动基础设施。
- Gateway 建议最后启动；否则前端请求已经进 Gateway，但后端服务未启动时会出现 502。
- RAG Worker 只有在 `RAG_EMBEDDING_PROVIDER=http` 时必须启动；如果仍使用 `mock`，可以先跳过。
- 每个服务启动后看控制台日志，确认 `Started XxxApplication in x.x seconds`

### 5.2 配置文件检查

**gateway-service/src/main/resources/application.yml**：
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081    # 确认 User Service 端口
          predicates:
            - Path=/api/user/**
        - id: tool-agent
          uri: http://localhost:8083    # 确认 Tool Agent 端口
          predicates:
            - Path=/api/tool/**
        - id: code-agent
          uri: http://localhost:8084    # 确认 Code Agent 端口
          predicates:
            - Path=/api/code/**
        - id: rag-agent
          uri: http://localhost:8085    # 确认 RAG Agent 端口
          predicates:
            - Path=/api/rag/**
        - id: tool-ws                   # WebSocket 路由（AI 助手用）
          uri: ws://localhost:8083
          predicates:
            - Path=/ws/**
          filters:
            - SetPath=/tool/ws
  redis:
    host: localhost                     # 确认 Redis 地址
    port: 6379
```

**tool-agent/src/main/resources/application.yml**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root                        # 修改为你的 MySQL 用户名
    password: your_password             # 修改为你的 MySQL 密码
  data:
    redis:
      host: localhost
      port: 6379
```

**rag-agent/src/main/resources/application.yml / .env**：
```yaml
server:
  port: 8085

rag:
  vector-store:
    provider: milvus
  embedding:
    provider: http                 # mock 可用于快速开发；http 调用 rag-worker
    endpoint: http://localhost:8091/embed
    dimension: 1024                # BAAI/bge-m3 对应 1024
  llm:
    provider: mock                 # 接真实大模型时改为 http
```

------------------------------------------------------------------------------------------------------------------------

## 六、RAG Agent 使用

### 6.1 RAG 配置

本地真实语义检索推荐使用：

```env
RAG_EMBEDDING_PROVIDER=http
RAG_EMBEDDING_ENDPOINT=http://localhost:8091/embed
RAG_EMBEDDING_MODEL=BAAI/bge-m3
RAG_EMBEDDING_DIM=1024
RAG_EMBEDDING_TIMEOUT_MS=30000
```

### 6.2 Embedding Worker

首次使用 Embedding 前，需要准备 Python 虚拟环境并安装依赖：

```powershell
python -m venv .venv-rag
.\.venv-rag\Scripts\Activate.ps1
python -m pip install -r rag-worker\requirements.txt
```

启动 Embedding Worker：

```powershell
.\scripts\start-rag-worker.ps1
```

首次请求 `/embed` 时会自动下载 `BAAI/bge-m3` 到本机 Hugging Face 缓存。模型未下载完成前，RAG 索引会等待或失败；下载完成后再次执行索引即可。

验证 Embedding：

```powershell
.\scripts\test-rag-embedding.ps1 -SkipRagHealth
```

看到 `Embedding verification passed.` 表示可用。

### 6.3 文档测试流程

1. 登录前端，进入 Knowledge Base / Documents。
2. 上传 PDF、Word 或 PPT 文档。
3. 原始文件进入 MinIO，文档记录进入 MySQL。
4. 在文档列表中打开 RAG Info，点击 Reindex / Reprocess。
5. 等待解析、分块、向量化完成。
6. 进入 RAG Agent 页面提问，检查回答和引用来源。

### 6.4 验证命令

健康检查：

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/rag/health" -Method Get
```

文档解析状态：

```powershell
docker exec agent_mysql mysql -uroot -p123456 agent_platform -e "select id,title,file_type,parse_status,left(content,120) as preview from sys_document order by id desc limit 5;"
```

chunk 数量：

```powershell
docker exec agent_mysql mysql -uroot -p123456 agent_platform -e "select count(*) as chunk_count from rag_document_chunk;"
```

重建单个文档索引：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8085/rag/index/document/{documentId}/reprocess" `
  -Method Post
```

详细说明：

- [RAG Agent Runbook](./docs/RAG_AGENT_RUNBOOK.md)
- [File Upload Storage Design](./docs/file-upload-storage-design.md)

------------------------------------------------------------------------------------------------------------------------

## 七、前端部署

### 7.1 安装依赖

```bash
cd web-ui

# 使用 npm（推荐）
npm install

# 或使用 yarn
yarn install
```

### 7.2 配置代理（vite.config.js）

```javascript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',    // 指向 Gateway
      changeOrigin: true
    },
    '/ws': {
      target: 'ws://localhost:8080',    // WebSocket 也走 Gateway
      ws: true,
      changeOrigin: true
    }
  }
}
```

**注意**：如果 3000 端口被占用，Vite 会自动切换到 3001/3002，代理配置不受影响。

### 7.3 启动开发服务器

```bash
npm run dev
# 或
yarn dev
```

控制台应显示：
```
VITE v4.x.x  ready in xxx ms

➜  Local:   http://localhost:3000/
➜  Network: use --host to expose
```

### 7.4 浏览器访问

打开 http://localhost:3000

------------------------------------------------------------------------------------------------------------------------

## 八、完整版本信息

| 组件 | 具体版本 |
|----------|----------|
| Spring Boot | 3.1.8 |
| MyBatis-Plus | 3.5.5 (spring-boot3-starter) |
| MySQL Connector/J | 8.0.33 |
| Redis (Spring Data) | 5.0.14.1 |
| Vue | 3.5.32 |
| Vite | 8.0.8 |
| Element-Plus | 2.13.6 |
| TypeScript | 5.1.6 |
| Axios | 1.15.0 |
| npm | 11.6.1 |

