# 🐳 BankAgent - Docker 部署与本地开发指南

本项目提供了两种基于 Docker 的本地运行方案，您可以根据开发和部署测试的不同阶段进行选择：

1. **方案 A：半容器化开发方案 (推荐日常开发)**
   * **MySQL + Redis** 跑在 Docker 容器中。
   * **前端和后端微服务** 直接在本地运行。
   * **优点**：支持前端和 Java 后端的热重载、方便断点调试和快速修改。

2. **方案 B：全量容器化部署方案 (推荐环境演示与测试)**
   * **MySQL + Redis + 4个后端微服务 + 前端 Nginx** 全部打包并在 Docker 容器中运行。
   * **优点**：一键傻瓜式启动所有服务，完全不受本地 Java、Node、Maven 等环境和版本影响。

---

## ⚙️ 一、前置准备

1. **Docker Desktop** (版本 4.0+) 必须已安装并启动。
2. 确保本地没有占用以下端口的进程：
   * `3306` (MySQL)
   * `6379` (Redis)
   * `80` (Nginx 前端接入)
   * `8080`/`8081`/`8082`/`8083` (后端微服务及网关)

---

## 🚀 二、方案 A：半容器化开发方案（推荐日常开发调试）

基础设施使用 Docker 容器，免去手动安装 MySQL 和 Redis 的繁琐，前端和后端保持在本地宿主机运行，便于开发调试。

### 第 1 步：启动 MySQL & Redis
在项目根目录下执行：
```bash
docker compose up -d mysql redis
```
*MySQL 容器启动后，会自动创建 `agent_platform` 数据库，并导入完整的表结构及初始数据。*

### 第 2 步：验证基础设施状态
```bash
# 验证 MySQL（等待大约 15 秒首次初始化完成）
docker exec agent_mysql mysqladmin ping -u root -p123456

# 验证 Redis
docker exec agent_redis redis-cli ping
# 应返回: PONG
```

### 第 3 步：在本地启动后端微服务
> ⚠️ **注意**：本地运行时，Maven 可能会默认使用系统的高版本 JDK（例如 JDK 21+ 或 JDK 26），导致 Lombok 兼容报错。请强制指定使用 **JDK 17** 运行。请根据您本地实际安装的 JDK 17 路径替换下面的路径值：

**macOS / Linux**：
```bash
# 1. 终端窗口 1：启动网关 (8080)
export JAVA_HOME="/Users/your_username/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home" # 替换为您的本地路径
cd gateway-service && mvn spring-boot:run -DskipTests

# 2. 终端窗口 2：启动用户服务 (8081)
export JAVA_HOME="/Users/your_username/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home"
cd user-service && mvn spring-boot:run -DskipTests

# 3. 终端窗口 3：启动工具服务 (8083)
export JAVA_HOME="/Users/your_username/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home"
cd tool-agent && mvn spring-boot:run -DskipTests
```
*(注：macOS 用户也可以直接在项目根目录下运行 `bash dev.sh` 一键自动分窗口启动所有服务)*

**Windows (CMD)**：
```cmd
:: 1. 启动网关 (8080)
set JAVA_HOME="C:\Program Files\Java\jdk-17"  &:: 替换为你的 JDK17 安装目录
cd gateway-service && mvn spring-boot:run -DskipTests

:: 2. 启动用户服务 (8081)
set JAVA_HOME="C:\Program Files\Java\jdk-17"
cd user-service && mvn spring-boot:run -DskipTests

:: 3. 启动工具服务 (8083)
set JAVA_HOME="C:\Program Files\Java\jdk-17"
cd tool-agent && mvn spring-boot:run -DskipTests
```

**Windows (PowerShell)**：
```powershell
# 1. 启动网关 (8080)
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"   # 替换为你的 JDK17 安装目录
cd gateway-service; mvn spring-boot:run -DskipTests

# 2. 启动用户服务 (8081)
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
cd user-service; mvn spring-boot:run -DskipTests

# 3. 启动工具服务 (8083)
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
cd tool-agent; mvn spring-boot:run -DskipTests
```

### 第 4 步：本地启动前端
```bash
cd web-ui
npm install
npm run dev
```
前端开发地址：`http://localhost:3000`

---

## 📦 三、方案 B：全量 Docker 容器化部署方案（适合一键演示与测试）

此方案无需您配置本地的 JDK 17、Node.js 或 Maven，通过 Docker 一键打包并运行包含微服务和 Nginx 代理在内的整套系统。

### 第 1 步：编译前端静态资源（⚠️ 必须）
由于全量方案中 Nginx 会直接挂载前端的打包产物目录 `./web-ui/dist`，**在首次启动或前端代码修改后，必须先在本地进行打包构建**：
```bash
cd web-ui
npm install
npm run build
```
这会在 `web-ui` 目录下生成 `dist` 静态资源目录。

### 第 2 步：一键打包并启动全量容器
返回项目根目录，执行：
```bash
docker compose up --build -d
```
Docker 会依次拉取镜像，并通过多阶段构建编译四个后端 Spring Boot 服务（Gateway, User, Task, Tool），最后通过 Nginx 进行反向代理接入。

### 第 3 步：访问服务
全量部署成功后，系统统一由 **Nginx (80 端口)** 提供服务接入：
- **前端页面**：[http://localhost](http://localhost) (输入默认账号：`admin` 密码：`123456` 即可登录进入系统)
- **后端网关 API**：`http://localhost:8080`

### 第 4 步：日常管理
```bash
# 查看所有运行中的服务容器
docker compose ps

# 查看后端网关或特定服务的运行日志
docker compose logs -f gateway-service
docker compose logs -f tool-agent

# 停止全量服务（数据保留）
docker compose down

# 停止全量服务并清空数据库（谨慎！数据卷会重置到初始状态）
docker compose down -v
```

---

## 🔑 四、数据库与 Redis 连接配置

无论是方案 A 还是方案 B，数据库和 Redis 都由 Docker 统一托管：

| 参数 | 值 |
|------|----|
| MySQL Host | `localhost` (本地开发连) / `mysql` (Docker 容器内连) |
| MySQL Port | `3306` |
| 初始数据库 | `agent_platform` |
| 用户名 / 密码 | `root / 123456` |
| Redis Host | `localhost:6379` (本地开发连) / `redis` (Docker 容器内连) |

> 💡 **端口冲突提示**：如果本地电脑已独立安装了 MySQL 或 Redis 导致容器端口被占用，请先停止本地服务：
> 
> **macOS (Homebrew)**:
> ```bash
> brew services stop mysql
> brew services stop redis
> ```
> 
> **Windows (CMD/PowerShell - 管理员身份)**:
> ```cmd
> net stop mysql
> net stop redis
> # 或者直接在“服务”管理器 (services.msc) 中手动停用对应的 MySQL 或 Redis 服务
> ```

---

## 💎 五、🚀 新增功能说明 (管理员后台资产系统)

为了提供更完善的后台治理能力，本项目新增了管理员专属的资产配置功能：

### 1. 资产与日程管理（Resource & Asset Management）
* **路径**：以管理员身份登录后，左下角侧边栏可进入 **Resource Management** 菜单。
* **双标签管理工作流**：
  * **Meeting Rooms（会议室维护）**：支持对物理会议室资源进行全生命周期管理（列表、新增、编辑、删除、运行/维护状态切换）。设施录入由原始逗号输入框升级为**高保真多选器材列表**（支持 Projector、Whiteboard、Video Conference、Audio System 等多种规格设备）。
  * **Schedules & Bookings（预约与排期管理）**：支持对全局预约进行统一监控与调度。管理员可随时修改预约主题、时间范围（精确至秒级日期时间），并提供一键取消（Cancel）预约、自动关联物理会议室名称等能力。

### 2. 全量初始数据英文转译（Localization Clean-up）
* 我们对 Docker 启动时加载的初始 SQL 脚本进行了全量重构。
* 默认初始化构建出的数据全部切换为英文（包括 `sys_user` 真实姓名 `Administrator`、各系统的角色的英文描述 `Employee`，以及会议室与其附属设施的英文名称）。
* 这确保了任何团队成员重新通过 Docker 拉起全新的数据库容器时，系统即刻处于完全脱汉的纯英文界面。

### 3. 微服务级 X-User-Roles 鉴权
* 配合 Gateway 网关对安全 JWT 字段解析，在下游 `tool-agent` 服务中加入了安全拦截，对所有管理员相关的资源/排期 CRUD 接口执行 `X-User-Roles` 请求头校验，确保非 Admin 权限的恶意请求无法越权调用后台功能。
