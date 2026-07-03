# 🎨 BankAgent - 前端 Web UI 工程

本项目为 BankAgent 平台的 Vue 3 前端系统。

---

## 📌 分支同步说明

⚠️ **重要提示**：
本目录下的前端代码（包括 Agent 页面拆分重构、极简出行方式下拉选择、以及点击导航步骤在地图上展示亮橙色折线的高亮缓动动画等最新特性）与 **`feature/backend-rbac`** 分支（及 `feature/frontend-redesign` 分支）保持完全同步与一致。

---

## 🛠️ 快速开始

### 1. 安装依赖
```bash
npm install
```

### 2. 启动开发服务器（本地调试）
```bash
npm run dev
```
启动后可在浏览器访问：`http://localhost:3000`。
*本地运行时，Vite 代理默认会指向宿主机的 `http://localhost:8080` (Gateway-Service) 网关。*

### 3. 构建打包（部署准备）
在需要进行 Docker 全量部署或生产发布前，必须在本地执行打包命令：
```bash
npm run build
```
打包产物将输出在 `web-ui/dist` 目录下，供 Nginx 进行静态托管。

---

## 📁 核心目录结构说明

* `src/views/tool/index.vue` - 工具 Agent 主入口。
* `src/views/tool/components/` - 拆分出的子代理卡片组件（`RouteAgent`, `ScheduleAgent`, `MeetingAgent`）。
* `src/components/MapContainer.vue` - 封装的高德地图组件（内含步骤路径动画高亮 Overlay 逻辑）。
* `src/views/admin/UserManagement.vue` - 用户角色与状态管理后台。
