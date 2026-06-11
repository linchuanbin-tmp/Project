# Docker 半容器化开发方案（Alternative 方案）

> 本文档是原版 `README.md` 的**替代部署方案**，仅替换其中"环境安装"和"数据库/Redis 启动"部分。  
> **Java / Maven / Node.js 的安装与后端前端的启动方式与原 README 完全一致，无需重复配置。**

---

## 核心思路

| 组件 | 运行方式 | 原因 |
|------|---------|------|
| **MySQL** | Docker 容器 | 免安装，版本锁定，自动导入数据 |
| **Redis** | Docker 容器 | 免安装，一行命令启动 |
| **gateway-service** | 本地 `mvn spring-boot:run` | 保留热重载，方便调试 |
| **user-service** | 本地 `mvn spring-boot:run` | 同上 |
| **tool-agent** | 本地 `mvn spring-boot:run` | 同上 |
| **web-ui** | 本地 `npm run dev` | 同上 |

你只需要安装 **Docker Desktop**，不再需要手动安装和配置 MySQL、Redis。

---

## 一、前置要求

| 依赖 | 版本 | 用途 | 验证命令 |
|------|------|------|----------|
| Java | 17 | Spring Boot 3.x | `java -version` |
| Maven | 3.9+ | 后端构建 | `mvn -v` |
| Node.js | 18.16+ | 前端构建 | `node -v` |
| **Docker Desktop** | **4.0+** | **运行 MySQL + Redis** | `docker -v` |

> ✅ 不再需要本地安装 MySQL 和 Redis

---

## 二、新增文件说明

```
项目根目录/
├── docker-compose.yml                    # ⭐ 一键启动 MySQL + Redis
├── docker/
│   ├── config/
│   │   └── mysql.cnf                     # MySQL 字符集 + 时区配置
│   └── init/
│       └── agent_platform_backup_utf8.sql # UTF-8 版初始化 SQL（原文件为 UTF-16LE）
└── README_DOCKER.md                      # 本文档
```

---

## 三、快速开始

### 第 1 步：启动基础设施

```bash
# 在项目根目录执行
docker compose up -d
```

预期输出：
```
✔ Container agent_mysql  Started
✔ Container agent_redis  Started
```

**MySQL 会自动完成：**
- 创建数据库 `agent_platform`（utf8mb4 字符集）
- 导入表结构和测试数据（`meeting_room`, `meeting_schedule`, `sys_user`）
- 创建默认用户 `admin / 123456`

### 第 2 步：验证服务就绪

```bash
# 验证 MySQL（等待约 20-30 秒首次启动）
docker exec agent_mysql mysqladmin ping -u root -p123456

# 验证 Redis
docker exec agent_redis redis-cli ping
# 应返回: PONG
```

### 第 3 步：启动后端（与原 README 完全一致）

按顺序在**三个独立终端**中执行：

```bash
# 终端 1 - Gateway
cd gateway-service && mvn spring-boot:run

# 终端 2 - User Service
cd user-service && mvn spring-boot:run

# 终端 3 - Tool Agent
cd tool-agent && mvn spring-boot:run
```

### 第 4 步：启动前端（与原 README 完全一致）

```bash
cd web-ui && npm install && npm run dev
```

---

## 四、日常操作

```bash
# 后台启动（推荐，不占用终端）
docker compose up -d

# 查看运行状态
docker compose ps

# 查看日志
docker compose logs mysql
docker compose logs redis

# 停止（数据保留）
docker compose down

# 停止并清空所有数据（谨慎！重置到初始状态）
docker compose down -v
```

---

## 五、数据库连接信息

容器启动后，本地后端直接使用以下配置连接（与原 `application.yml` 一致，无需修改）：

| 参数 | 值 |
|------|----|
| Host | `localhost` |
| Port | `3306` |
| Database | `agent_platform` |
| Username | `root` |
| Password | `123456` |
| Redis Host | `localhost:6379` |

> 如果你本地已有 MySQL/Redis 占用了 3306/6379 端口，先停掉本地服务再启动 Docker：
> ```bash
> # macOS 停止本地 MySQL
> brew services stop mysql
> # macOS 停止本地 Redis
> brew services stop redis
> ```

---

## 六、常见问题

**Q: `docker compose up` 之后 MySQL 一直重启怎么办？**  
A: 查看日志 `docker compose logs mysql`，通常是端口冲突。确认本地没有其他 MySQL 进程在运行。

**Q: 数据库数据在哪里？**  
A: 存储在 Docker Volume `agent_mysql_data` 中，`docker compose down` 不会删除数据，只有加 `-v` 才会清除。

**Q: 如何连接 MySQL 查看数据？**  
```bash
docker exec -it agent_mysql mysql -u root -p123456 agent_platform
```

**Q: 其他组员 push 了新的 SQL 迁移怎么办？**  
A: 暂无自动迁移，手动执行：
```bash
docker exec -i agent_mysql mysql -u root -p123456 agent_platform < 新的迁移文件.sql
```
