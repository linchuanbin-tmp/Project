# 文档资产库 — 多格式文件支持

## 概述

Knowledge Assets Library（知识资产库）从原本仅支持 Markdown 在线编辑，扩展为支持 **Markdown 手写 + PDF/DOCX/PPT 文件上传** 两种模式。

文件通过 **MinIO 对象存储** 保存原文件，MySQL 存储元数据，RAG 向量索引使用解析后的纯文本（解析由 RAG Agent 团队负责）。

---

## 数据库结构

### `sys_document` 表（新增字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `file_type` | `VARCHAR(20)` | `MARKDOWN` / `PDF` / `DOCX` / `PPT`，默认 `MARKDOWN` |
| `file_size` | `BIGINT` | 原文件字节数，Markdown 文档为 NULL |
| `minio_object_key` | `VARCHAR(500)` | MinIO 存储的 object key（UUID + 原始扩展名），Markdown 为 NULL |
| `parse_status` | `VARCHAR(20)` | `PENDING` / `DONE` / `FAILED`，供 RAG Agent 标记解析状态 |

向后兼容：现有 Markdown 文档的 `file_type` 默认 `MARKDOWN`，行为与之前完全一致。

---

## 存储架构

```
┌─────────────────────────────────────────────────────────┐
│                      前端上传                            │
│   Markdown: textarea 手写                                │
│   PDF/DOCX/PPT: multipart/form-data 文件上传             │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  user-service                            │
│                                                         │
│   POST /user/document/create   → Markdown（跟以前一样）   │
│   POST /user/document/upload   → 文件上传（新增）         │
│                                                         │
│   1. 文件 → MinioStorageService.upload() → MinIO        │
│   2. 元数据 → SysDocumentMapper.insert() → MySQL         │
│   3. 触发 RAG 索引（跟以前一样）                           │
│                                                         │
│   GET /user/document/download/{id} → 预览/下载原文件      │
│   DELETE 时同步删除 MinIO 文件                            │
└────────────────────┬────────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
   ┌────────────┐        ┌────────────┐
   │   MySQL    │        │   MinIO    │
   │ sys_document│       │ rag-documents│
   │ (元数据)    │       │ bucket     │
   │ content字段 │       │ (原文件)    │
   │ 存解析文本  │       │            │
   └────────────┘        └────────────┘
```

- **MinIO bucket**: `rag-documents`（自动创建）
- **MinIO 已在 docker-compose 中运行**（Milvus 也在用），应用层新增 SDK 直连
- **文件大小限制**：默认 100MB，系统管理员可在 Settings 页面调整（1-1024 MB）

---

## API 端点

### 新增

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/user/document/upload` | multipart 文件上传。参数：`file`（必填）、`title`（选填，默认用文件名）、`securityLevel`（必填）、`deptId`（选填） |
| `GET` | `/api/user/document/download/{id}` | 预览/下载原文件。PDF 返回 `inline`（浏览器预览），DOCX/PPT 返回 `attachment`（触发下载）。需权限校验 |
| `GET` | `/api/user/config/max-upload-size` | 管理员获取当前上传大小限制（MB） |
| `PUT` | `/api/user/config/max-upload-size` | 管理员设置上传大小限制。Body: `{ "sizeMb": 100 }` |

### 现有端点（无变化）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/user/document/create` | Markdown 文档创建 |
| `PUT` | `/api/user/document/update` | Markdown 文档编辑（仅 Markdown 可编辑） |
| `DELETE` | `/api/user/document/delete/{id}` | 删除文档（同步删除 MinIO 文件） |
| `GET` | `/api/user/document/list` | 文档列表（所有用户可见全部文档标题） |

---

## 权限模型

### 文档可见性

- **所有认证用户** 都能看到全部文档列表（标题、类型、安全级别、部门标签）
- **文档内容/文件** 受权限控制：

| 条件 | `accessible` | 能否看内容/下载 |
|------|:---:|:---:|
| 本部门文档，clearance level 足够 | `true` | ✅ |
| 全局文档（deptId=null），clearance level 足够 | `true` | ✅ |
| 本部门文档，clearance level 不够 | `false` | ❌ 需申请 |
| 其他部门文档 | `false` | ❌ 需申请 |
| 已通过 RAG_APPLY 审批 | `true` | ✅ |

### 审批路由

1. 文档属于某部门 → 发给该部门的 ROLE_DEPT_ADMIN 或 ROLE_ADMIN
2. 全局文档或找不到部门管理员 → 发给任一 ROLE_ADMIN
3. 连管理员都没有 → 提示用户联系系统管理员

### 编辑/删除权限

- 仅 Markdown 文档可编辑（文件类型不可修改内容）
- 管理员/部门管理员可删除任意文档

---

## 前端交互

### 创建文档弹窗

左右布局（800px 宽）：

**左侧面板**：文件类型选择卡片（Markdown / PDF / DOCX / PPT）、标题输入、安全级别、目标部门

**右侧区域**：
- Markdown 模式 → 大号 textarea
- 文件模式 → 拖拽上传区域（支持点击选择或拖拽）

### 文档卡片

- 图标按文件类型区分：青色 BookOpen（Markdown）、红色 FileText（PDF）、蓝色 FileText（DOCX）、橙色 MonitorPlay（PPT）
- 标签行：`[部门名] [安全级别]`
- 按钮行（管理员）：`[📊 RAG信息] [✏️ 编辑(Markdown)] [🗑 删除]`
- 底栏：`[👁 Preview(仅PDF)] [⬇ Download(DOCX/PPT)] [阅读(Markdown)]`

### 文件预览

- **PDF**：浏览器原生在线预览（后端返回 `Content-Disposition: inline`）
- **DOCX/PPT**：触发下载（后端返回 `Content-Disposition: attachment`）

---

## 关键文件

### 后端

| 文件 | 角色 |
|------|------|
| `user-service/.../service/MinioStorageService.java` | MinIO 上传/下载/删除，自动建 bucket |
| `user-service/.../controller/DocumentController.java` | upload/download 端点 |
| `user-service/.../service/impl/SysDocumentServiceImpl.java` | 文件上传流程，删除时清理 MinIO |
| `user-service/.../entity/SysDocument.java` | 实体新增 file_type 等 4 字段 |
| `user-service/.../dto/DocumentResponse.java` | 返回 fileType、fileSize |
| `user-service/.../controller/SystemConfigController.java` | 管理员可调上传大小限制 |
| `user-service/pom.xml` | 新增 minio SDK 依赖 |
| `user-service/.../application.yml` | MinIO 连接 + multipart 100MB 配置 |
| `rag-agent/.../entity/SysDocument.java` | 同样新增字段（两个服务共用表） |
| `docker/init/agent_platform_backup_utf8.sql` | DDL 新增 4 列 |

### 前端

| 文件 | 角色 |
|------|------|
| `web-ui/src/views/document/index.vue` | 文档列表首页（上传弹窗、卡片、预览/下载、类型图标） |
| `web-ui/src/api/department.ts` | `uploadDocument()`、`getDocumentDownloadUrl()` |
| `web-ui/src/views/settings/index.vue` | 管理员设置上传大小限制 |
| `web-ui/src/locales/*.ts` | i18n 新增约 30 个 key |

---

## RAG Agent 对接说明

文件上传后 `content` 字段暂时为空字符串，`parse_status` 为 `PENDING`。

RAG Agent 团队需要在索引时：
1. 从 MinIO 下载原文件（`minio_object_key`）
2. 解析 PDF/DOCX/PPT 提取纯文本
3. 将文本写入 `sys_document.content`
4. 更新 `parse_status` 为 `DONE` 或 `FAILED`
5. 后续 chunk → embedding → Milvus 流程与 Markdown 一致

MinIO 连接信息：`minio:9000`，凭证从 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` 环境变量获取。
