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

部署前请确认以下环境已安装，版本必须匹配（完整版本信息在第7）：

| 依赖 | 版本 | 用途 | 验证命令 |
|------|------|------|----------|
| Java | 17  | Spring Boot 3.x 运行环境 | `java -version` |
| Maven | 3.9+ | 后端构建 | `mvn -v` |
| MySQL | 8.0.33+ | 业务数据库 | `mysql --version` |
| Redis | 5.0+ | 缓存 + 限流 + 日程冲突检测 | `redis-cli --version` |
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
│   │   ├── config/SecurityConfig.java       # 跨域 + 安全放行
│   │   └── GatewayServiceApplication.java   # 启动类 + IP 限流解析器
│   ├── src/main/resources/
│   │   └── application.yml                  # 路由配置（关键文件）
│   └── pom.xml                              # Spring Cloud Gateway 3.1.9
│
├── 📁 user-service/                 # 用户中心 (端口 8081)
│   ├── src/main/java/com/agent/user/
│   │   ├── controller/              # LoginController
│   │   ├── service/impl/            # UserServiceImpl (JWT + BCrypt)
│   │   ├── mapper/                  # UserMapper (MyBatis-Plus)
│   │   ├── entity/                  # User 实体类
│   │   ├── dto/                     # LoginRequest/LoginResponse/Result
│   │   └── utils/JwtUtil.java       # JWT 生成与校验
│   └── pom.xml                      # Spring Boot 3.2.0 + MyBatis-Plus 3.5.5
│
├── 📁 tool-agent/                   # 工具 Agent 核心 (端口 8083)
│   ├── src/main/java/com/agent/tool/
│   │   ├── controller/ToolController.java   # 会议室/日程/路线/AI 接口
│   │   ├── service/                 # MeetingRoomService, ScheduleService
│   │   ├── service/impl/              # 业务实现 (MySQL + Redis)
│   │   ├── mapper/                  # MeetingRoomMapper, MeetingScheduleMapper
│   │   ├── entity/                  # MeetingRoom, MeetingSchedule
│   │   ├── dto/                     # 请求/响应 DTO
│   │   ├── handler/                 # TaskProgressWebSocketHandler
│   │   └── config/WebSocketConfig.java    # WebSocket 注册 (/tool/ws)
│   ├── src/main/resources/
│   │   └── application.yml            # MySQL + Redis 连接配置
│   └── pom.xml                      # Spring Boot 3.2.0 + Redis + MyBatis-Plus
│
├── 📁 task-service/                 # 任务中心 (端口 8082，预留)
│   └── pom.xml                      # 仅创建，功能未开发
│
├── 📁 web-ui/                       # Vue 3 前端
│   ├── src/
│   │   ├── views/tool/index.vue     # 工具 Agent 主页面（核心）
│   │   ├── api/tool.ts              # 前端 API 封装
│   │   ├── utils/websocket.ts       # WebSocket 客户端封装
│   │   └── components/MapContainer.vue  # 高德地图组件
│   ├── vite.config.js               # Vite 配置 + 代理规则
│   └── package.json                 # Vue 3 + Element-Plus + Axios
│
├── 📁 Redis/                        # Redis 配置目录（可选）
│   └── redis.conf                   # 如需要自定义配置可放这里
│
├── 📄 agent_platform_backup.sql     # ⭐ MySQL 数据库备份文件
│                                    # 包含：sys_user, meeting_room, meeting_schedule
│                                    # 测试数据：admin/123456, 3 个会议室
│
├── 📄 pom.xml                       # 父工程 Maven 配置（聚合所有模块）
├── 📄 mvnw / mvnw.cmd               # Maven Wrapper（无需全局安装 Maven）
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
| `sys_user` | id, username, password, real_name, role, status, create_time | 用户登录表，password 为 BCrypt 加密 |
| `meeting_room` | id, room_name, floor, capacity, facilities, status | 会议室资源表，预设 3 条数据            |
| `meeting_schedule` | id, room_id, booker, start_time, end_time, topic, status | 预定记录表，room_id=0 表示个人日程     |

**默认测试数据**：
- 用户：`admin` / `123456`（BCrypt 加密），角色 `admin`
- 会议室：301会议室(10人)、302会议室(20人)、501大会议室(50人)

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
| 1 | MySQL | 3306 | `mysqld --console` | `mysql -u root -p` |
| 2 | Redis | 6379 | `redis-server` | `redis-cli ping` |
| 3 | Gateway | 8080 | `cd gateway-service && mvn spring-boot:run` | `curl http://localhost:8080` |
| 4 | User Service | 8081 | `cd user-service && mvn spring-boot:run` | `curl http://localhost:8081/api/user/login` |
| 5 | Tool Agent | 8083 | `cd tool-agent && mvn spring-boot:run` | `curl http://localhost:8083/api/tool/meeting-rooms` |

**注意**：
- Gateway 必须在 User Service 和 Tool Agent 之后启动，否则路由转发会 502
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

------------------------------------------------------------------------------------------------------------------------

## 六、前端部署

### 6.1 安装依赖

```bash
cd web-ui

# 使用 npm（推荐）
npm install

# 或使用 yarn
yarn install
```

### 6.2 配置代理（vite.config.js）

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

### 6.3 启动开发服务器

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

### 6.4 浏览器访问

打开 http://localhost:3000

------------------------------------------------------------------------------------------------------------------------

## 七、完整版本信息

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

