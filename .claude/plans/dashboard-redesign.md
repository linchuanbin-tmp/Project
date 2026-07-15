# Dashboard Redesign Plan

## 当前问题

| 问题 | 详情 |
|------|------|
| Quick Access 卡片 | 三个入口卡片跟侧边栏完全重复，占用大量首屏空间（约 250px 高） |
| Service Status | 全是 hardcoded 的假数据（写死 Online/Coming Soon），无信息价值 |
| Coming Events | 只显示 2 条，卡片固定 280px 高，信息密度极低 |
| 整体 | 整个首屏只有 HITL 提醒 + 3 个导航卡片 + 2 条日程 + 5 个假服务状态，真正有用的只有 HITL 和日程 |

## 新设计方案

把 Dashboard 从"带导航卡片的欢迎页"变成**真正的运营中心**。三行布局：

### 行 1：统计卡片行 (Stat Cards Row)

四个紧凑的 KPI 统计卡片并排：

| 卡片 | 数据来源 | 说明 |
|------|----------|------|
| **今日任务** (Today's Tasks) | `GET /task/list` 前端聚合 | 按 status 统计今天提交的任务数：Running / Success / Failed |
| **即将到来的日程** (Upcoming Events) | `GET /tool/my-schedules` 过滤 | 今天+未来 7 天的日程数量 |
| **未读消息** (Unread Messages) | `GET /user/notification/unread-count` | 已有的 API，直接显示 |
| **待审批** (Pending Reviews) | `GET /user/notification/list?status=2` | 已有，仅 admin/deptAdmin 显示 |

每个卡片：图标 + 数字（大字）+ 标签（小字），点击可跳转到对应页面。

### 行 2：日程时间线 + 最近任务

两栏布局：

**左栏（2/3 宽）：Upcoming Schedule Timeline**
- 替代当前的 Coming Events 卡片
- 时间线样式，按天分组显示未来 7 天的日程
- 每条显示：时间、主题、地点/类型、时长
- 空状态时引导用户去预订/创建日程
- 高度自适应内容（不再固定 280px）

**右栏（1/3 宽）：Recent Activity**
- 显示最近 5 条活动流：
  - 任务提交/完成（从 `/task/list` 取最近记录）
  - 显示的格式："Code Agent 任务完成"、"会议室预订成功" 等
- 每条带时间戳（"3 hours ago" 风格）
- 点击跳转到对应详情

### 行 3：快捷操作栏 (Quick Actions)

一条紧凑的按钮行，替代当前的三个大卡片：

```
[预订会议室] [提交 SQL 查询] [查询知识库] [查看我的日程]
```

每个按钮：小图标 + 文字，圆角胶囊样式，hover 有微交互。

---

## 去掉的内容

- **Quick Access 三张大卡片** — 侧边栏已有入口
- **Service Status** — 全是假的 hardcoded 数据，没有实际用途

## 保留的内容

- **HITL Pending Approvals Banner** — 对 admin 有价值，保留但缩小一些
- **Copilot Widget** — 保持不变

---

## 文件改动清单

### 前端 (web-ui)

| 文件 | 改动 |
|------|------|
| `src/views/dashboard/index.vue` | 完全重写 template + script + style |
| `src/locales/en.ts` | 新增 dashboard 相关 i18n key |
| `src/locales/zh-CN.ts` | 同上 |
| `src/locales/zh-TW.ts` | 同上 |

### 后端

**不需要改动。** 所有数据都来自已有 API，在前端聚合即可：
- `GET /task/list` → 今日任务统计
- `GET /tool/my-schedules` → 日程数据
- `GET /user/notification/unread-count` → 未读数量
- `GET /user/notification/list?status=2` → 待审批数量

### 样式风格

延续现有视觉体系：
- Element Plus 组件 + 自定义 scoped CSS
- 白色卡片、灰色描边 `#f0f0f0`、`border-radius: 14-16px`
- 字体系统一致（13-14px body，24px 标题）
- 保持现有的 `max-width: 1200px`、padding 节奏
