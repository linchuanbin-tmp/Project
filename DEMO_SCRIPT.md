# BankAgent 演示视频录制脚本（4-5 分钟）

> 录制前准备：
> 1. 确保 `docker compose up -d` 所有服务已启动且 healthy
> 2. 浏览器开无痕窗口，1920×1080，隐藏书签栏
> 3. 提前登录一次预热所有 AI 调用（LLM 推理首次较慢）
> 4. 三个账号准备好：`admin` / `credit_mgr` / `credit_staff`，密码均为 `123456`
> 5. 建议 OBS / Screen Studio 录制，后期加字幕

---

## 🎬 时间线总览

| 段落 | 内容 | 时长 |
|------|------|------|
| 1 | 登录 + 三级角色对比 | 0:30 |
| 2 | Dashboard + 全局 Copilot | 0:25 |
| 3 | RAG 知识库问答 ⭐ | 1:00 |
| 4 | Code Agent 自然语言查数 ⭐ | 0:45 |
| 5 | Tool Agent 智能工具 | 0:20 |
| 6 | HITL 人机协同审批 ⭐ | 0:55 |
| 7 | 系统管理员 + 收尾 | 0:25 |
| **合计** | | **4:20** |

---

## 📝 详细分镜脚本

---

### 第一段：登录 + 三级角色对比（0:30）

> **旁白**：
> "大家好，这是 BankAgent——面向企业银行业务的智能多 Agent 协作平台。系统采用 Spring Cloud 微服务架构，集成了 RAG 检索增强生成、Text-to-SQL 自然语言查询、以及人机协同审批工作流。我们先看登录——系统内置三级 RBAC 角色，不同角色看到的界面和权限完全不同。"

**操作步骤**：

1. 打开 `http://localhost`（或 `https://bankagent.online`），展示登录页——BankAgent 品牌 Logo、"Sign in" 标题
2. 输入 `credit_staff` / `123456`，点击 **Continue** 登录
3. 登录后光标划过左侧导航栏，展示**普通员工**的菜单：
   - **Workspace**：Dashboard / My Tasks / My Schedules / Messages
   - **Agent Services**：Tool Agent / Code Agent / RAG Agent
   - **Knowledge Base**：Documents
   - ⚠️ **没有 Management 分组**——普通员工无管理权限
4. 鼠标悬停在左下角用户信息区：显示 `Credit Staff` / `Employee`

> ⏱️ 时间节点：0:30

---

### 第二段：Dashboard + 全局 Copilot（0:25）

> **旁白**：
> "登录后进入统一 Dashboard，聚合展示当前用户的任务统计、日程和未读消息。另外注意右下角——这里有一个全局 Copilot 悬浮球，点击后可以在任何页面唤起 AI 助手进行提问。"

**操作步骤**：

1. 展示 Dashboard 页面：标题 "Dashboard"，副标题 "Welcome back, Credit Staff"
2. 快速扫过三张统计卡片：Today's Tasks / Upcoming Events / Unread Messages
3. 指向右侧日历组件 "Upcoming Schedule"（July 2026）
4. **鼠标移到右下角**，指向 **Copilot 悬浮球**（聊天气泡图标）
5. 点击悬浮球，展开 Copilot 面板，展示内嵌的 AI 对话界面
6. 输入一个简单问题（如"今天的任务有哪些？"），展示 Copilot 快速响应
7. 关闭 Copilot 面板

> **旁白**：
> "这个 Copilot 是全局的——无论你在哪个页面，都可以随时唤起它进行 AI 问答，不需要切换到专门的 Agent 页面。"

> ⏱️ 时间节点：0:55

---

### 第三段：RAG 知识库问答 ⭐ 重点（1:00）

> **旁白**：
> "现在进入核心功能——RAG 知识库问答。系统底层使用 Milvus 向量数据库，同时每次查询自动注入用户部门和密级进行权限过滤，确保信贷部员工无法检索合规部的涉密文档。"

**操作步骤**：

1. 点击侧边栏 **RAG Agent**，展示页面标题和说明文案
2. 指向右侧面板，逐项展示：
   - **RAG Readiness**：Embedding 模型 `local-bge-m3`，维度 `1024`，向量库 `milvus`，状态 `Ready`
   - **Accessible Documents**：列出当前用户可访问的文档（5 篇），每篇显示标题、密级标签、文档类型
   - **Index Tasks**：索引任务执行历史
3. 切回左侧对话区，在输入框 "Ask about accessible enterprise documents..." 输入：
   > "信贷审批的标准流程是什么？"
4. 点击 Send，等待 AI 生成回答
5. **指向引用来源**：每条回答下方标注来源文档名、页码/段落
6. 再问一个问题：
   > "不良贷款的五级分类标准"
7. 展示 AI 综合多个文档检索后生成的回答，再次指向引用链接

> **旁白**：
> "注意看，每段回答都附带了可点击的引用来源——这是 RAG 区别于普通聊天机器人的关键：AI 的结论有据可查，满足金融行业对可审计性的硬性要求。右侧面板还可以实时查看 RAG 系统的运行状态和可访问文档列表。"

> ⏱️ 时间节点：1:55

---

### 第四段：Code Agent 自然语言查数 ⭐ 重点（0:45）

> **旁白**：
> "下一个亮点是 Code Agent——也就是 Text-to-SQL 引擎。用户用自然语言描述需求，系统自动生成 SQL 并经过五层安全审计白名单校验后才能执行。"

**操作步骤**：

1. 点击侧边栏 **Code Agent**，展示 "SQL Agent" 标题和说明
2. 指向右侧 **Available Schema** 面板：列出所有已白名单的数据表（sys_user、sys_department 等），底部标明 "Only SELECT queries covering these whitelisted tables are permitted"
3. 在左侧输入框中输入：
   > "List all high-risk customers"
4. 点击 **Generate SQL Query**，等待 AI 返回生成的 SQL
5. **展示 SQL 代码**高亮显示在终端风格的编辑器中，用户可手动修改
6. 点击 **Run Query** 执行
7. 展示查询结果表格

> **旁白**：
> "系统内置了五层白名单校验——禁止 DROP、DELETE、无 WHERE 的 UPDATE 等危险操作。如果检测到高危 SQL，比如有人尝试删表，系统不会直接报错拒绝，而是触发 HITL 审批流，将 SQL 推送给管理员审核——这正是我们接下来要展示的。"

> ⏱️ 时间节点：2:40

---

### 第五段：Tool Agent 智能工具（0:20）

> **旁白**：
> "Tool Agent 集成了日常办公工具，包括会议室预订、日程管理和路径规划，所有工具都接入了大模型，支持自然语言交互。"

**操作步骤**：

1. 点击侧边栏 **Tool Agent**
2. 展示三个功能 Tab：Meeting Rooms / Schedule / Route Planner
3. 切换到 **Route Planner**，输入起点和终点（如"北京西站 → 中国银行总部"），展示高德地图渲染的路线
4. 快速切换到 **Schedule** Tab，展示本周日程视图

> ⏱️ 时间节点：3:00

---

### 第六段：HITL 人机协同审批 ⭐ 重点（0:55）

> **旁白**：
> "现在展示系统最具技术深度的设计——人机协同 Human-in-the-Loop。我们切换到部门管理员账号，看看同一套系统在他眼中有什么不同。"

**操作步骤**：

1. 点击左下角用户头像 → **Logout**，回到登录页
2. 登录 `credit_mgr` / `123456`（部门管理员）
3. **展示角色差异**：
   - 侧边栏多出了 **Management** 分组 → **My Department**（部门管理入口）
   - 左下角用户信息显示 `Credit Manager` / `Dept Admin`
   - Dashboard 标题：`Welcome back, Credit Manager`
4. **重点展示** Dashboard 顶部的 **Pending Approvals** 卡片：`3 HITL request(s) awaiting your review`，旁边有 **Review** 按钮
5. 指向第四张统计卡片：**Pending Reviews: 3**（普通员工没有这张卡片）
6. 点击 **Review** 按钮或侧边栏 **Messages**，进入消息中心
7. 展示待审批通知列表——**RAG 越权申请**（类型 `RAG_APPLY`，状态 `Pending`）
8. 点击通知展开详情：申请人、申请原因、目标文档密级 + **[Approve]** / **[Deny]** 按钮
9. 点击 **Approve**，通知状态变为 `Approved`

> **旁白**：
> "注意对比——部门管理员的侧边栏多了 Management 分组和 My Department 入口，Dashboard 多了待审批提醒卡片和 Pending Reviews 统计。这是 RBAC 权限模型在 UI 层的直接体现。
>
> 刚才演示的是 RAG 越权审批：当普通员工试图查阅超出自身密级的文档时，系统不会直接拒绝，而是生成审批通知推送给部门管理员。管理员批准后，后端在 Redis 写入 2 小时 TTL 的临时授权 Token——兼顾了安全性和业务灵活性。同样的 HITL 机制也用于高危 SQL 的审核放行。"

> ⏱️ 时间节点：3:55

---

### 第七段：系统管理员 + 收尾（0:25）

> **旁白**：
> "最后我们切换到系统管理员账号，看看最高权限视角下的完整管理后台。"

**操作步骤**：

1. 退出 `credit_mgr`，登录 `admin` / `123456`
2. **展示角色差异**：
   - 侧边栏 **Management** 分组下有 **4 个菜单项**：
     - **Dept Management**（部门管理）
     - **User Management**（用户管理）
     - **Resource Management**（资源管理）
     - **Task Center**（任务中心）
   - 对比：部门管理员只有 1 个（My Department），普通员工 0 个
   - 左下角用户信息显示 `Administrator` / `Administrator`
3. 点击 **User Management**，展示用户列表、角色分配界面
4. 点击左下角齿轮图标 **Settings**：
   - 展示 **语言切换**：English / 简体中文 / 繁體中文 即时切换
   - 展示 **AI Provider** 配置区：可配置多个大模型的 API Key、Base URL、模型名称

> **旁白**：
> "三级 RBAC 在 UI 层的体现非常清晰：普通员工只有基础工作区，部门管理员多了本部门管理入口，系统管理员则拥有完整的平台管控能力——部门管理、用户管理、资源管理和全局任务中心。
>
> 以上就是 BankAgent 的全部核心功能演示。系统从底层架构到业务设计，全面贯彻了企业级安全理念——三级 RBAC 角色体系、部门与密级隔离、RAG 元数据过滤、SQL AST 白名单审计、以及完整的人机协同审批工作流——在 AI 增强效率的同时，绝不突破安全底线。感谢观看。"

> ⏱️ 时间节点：4:20

---

## 🎯 录制 Checklist

- [ ] 浏览器无痕窗口，无多余标签
- [ ] 鼠标移动平滑，不要画圈晃来晃去
- [ ] 每个功能的输入文本提前复制好，不要现场输入
- [ ] 等待 AI 回复时用旁白填补，不要沉默
- [ ] **提前跑一遍**所有 AI 调用，确认 API Key 有效、网络畅通
- [ ] 关闭系统通知、微信/钉钉弹窗
- [ ] 后期剪辑：加字幕、剪掉等待过长片段（比如 LLM 推理 > 5 秒的部分可加速）

---

## 📋 备用素材（如需要补镜头）

| 场景 | 操作要点 |
|------|----------|
| Documents 知识库 | 展示文档列表、文件类型图标（PDF/DOCX/Markdown）、安全密级标签（Level 1-3） |
| My Tasks 任务中心 | 展示任务创建、审批流程、状态变更 |
| 用户管理 | admin 创建新用户、分配角色和部门 |
| 邮件验证 | 注册新账号 → 收到验证邮件 → 完成注册 |
| Copilot 悬浮球 | 右下角全局 AI 助手浮窗，快速提问 |

---

## 🎤 旁白英文版（如需要英文配音）

```
"Hi everyone, this is BankAgent — an intelligent multi-agent platform for enterprise banking.
Built on Spring Cloud microservices, it integrates RAG retrieval-augmented generation,
Text-to-SQL natural language querying, and Human-in-the-Loop approval workflows.

Let's start with login. The system enforces a three-tier RBAC model:
System Admin, Department Manager, and Employee — each with strictly scoped permissions.

Here's the RAG Agent. It performs semantic search via Milvus vector database, while
automatically injecting the user's department and clearance level into every query —
ensuring a Credit Department employee can never retrieve Compliance Department documents.
Every answer includes clickable citations for full auditability.

Now the Code Agent. Type a question in plain English, and it generates executable SQL.
Behind the scenes, a five-layer whitelist engine validates every query against the schema
before execution. If a dangerous operation like DELETE is detected, the system doesn't
just block it — it triggers a HITL approval workflow for manual review.

This is the real highlight — Human-in-the-Loop. When an employee requests a document
beyond their clearance, the system routes an approval request to their department manager.
Once approved, a time-limited access token is stored in Redis for temporary access.

Finally, the admin panel provides full user management, RBAC configuration, and
multi-provider AI settings with internationalization support.

BankAgent: enterprise AI that enhances productivity without compromising security.
Thank you."
```
