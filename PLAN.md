# RAG 文档扩展：支持 PDF / DOCX / PPT 文件上传

## 目标

让 Knowledge Assets Library 除了 Markdown 之外，还能上传和管理 PDF、DOCX、PPT 文件。
文件存 MinIO，解析后存纯文本，前端展示跟现在一样。

---

## 现状梳理

### 当前数据流
1. 前端 Create Document 弹窗 → 标题 + Markdown 正文文本
2. POST `/user/document/create` → 写入 `sys_document` 表（title + content longtext）
3. 创建成功后异步调 RAG `/rag/index/document/{id}` 
4. RAG 从 `sys_document` 读 content → MarkdownDocumentChunker 分块 → embedding → Milvus

### 当前表结构
```sql
sys_document (id, title, content text, dept_id, security_level, create_time)
```

### 已有基础设施
- MinIO 已在 docker-compose.yml 运行（Milvus 在用，应用层未用）
- `sys_document.content` 存 longtext，可复用存解析后的纯文本

---

## 改动方案

### 第一阶段：后端 - 支持文件上传 + 解析

#### 1. `sys_document` 表新增字段
```sql
ALTER TABLE sys_document ADD COLUMN file_type VARCHAR(20) DEFAULT 'MARKDOWN' COMMENT 'MARKDOWN/PDF/DOCX/PPT';
ALTER TABLE sys_document ADD COLUMN file_size BIGINT DEFAULT NULL COMMENT '原文件大小(bytes)';
ALTER TABLE sys_document ADD COLUMN minio_object_key VARCHAR(500) DEFAULT NULL COMMENT 'MinIO object key';
ALTER TABLE sys_document ADD COLUMN parse_status VARCHAR(20) DEFAULT NULL COMMENT 'PENDING/DONE/FAILED';
```
- `file_type` = 'MARKDOWN' 时行为跟现在完全一样（向后兼容）
- 新格式：上传原文件 → MinIO → 解析 → content 字段存纯文本 → 索引

#### 2. MinIO 集成（user-service）
- 新增 `MinioStorageService`：上传 / 下载 / 删除
- 配置 `minio.endpoint`, `minio.access-key`, `minio.secret-key`, `minio.bucket`

#### 3. 文件解析（user-service 或独立工具类）
- 引入 Apache Tika（或 PDFBox + POI）解析 PDF/DOCX/PPT → 纯文本
- 新增 `DocumentParserService`：根据 file_type 调不同解析器
- 如果不想引入重依赖，可以先用简单的库：
  - PDF: Apache PDFBox
  - DOCX: Apache POI (XWPF)
  - PPT: Apache POI (XSLF)

#### 4. DocumentController 改造
- 新增 `POST /user/document/upload`（multipart/form-data）
  - 接收文件 + title + securityLevel + deptId
  - 上传到 MinIO
  - 解析提取纯文本 → 写入 content
  - 写入 sys_document 记录
  - 触发 RAG 索引
- 现有 `POST /user/document/create` 保留（Markdown 手动输入），`file_type` 默认 'MARKDOWN'
- 新增 `GET /user/document/download/{id}` — 从 MinIO 下载原文件
- 删除文档时同步删除 MinIO 文件

#### 5. DocumentResponse 新增字段
```java
private String fileType;     // MARKDOWN / PDF / DOCX / PPT
private Long fileSize;       // 原文件大小
```

---

### 第二阶段：前端 - 上传 + 展示

#### 1. Create Document 弹窗改造
- 新增一个 tab/switch："Markdown 编辑" / "上传文件"
- 上传文件模式：
  - 文件选择器（accept=".pdf,.docx,.pptx"）
  - 自动解析文件名作为 title
  - 显示文件大小
- Markdown 模式保持现有行为

#### 2. 文档卡片改造
- 卡片 header 的图标根据 file_type 变化：
  - MARKDOWN → BookOpen（现有一致）
  - PDF → FileText（红色）
  - DOCX → FileText（蓝色）
  - PPT → Presentation（橙色）
- 卡片 footer 加一个下载按钮（非 Markdown 类型才显示）

#### 3. Zen Reader 改造
- Markdown 文档：现有行为（渲染 markdown）
- PDF/DOCX/PPT：显示解析后的纯文本（用 `<pre>` 样式），顶部显示原文件信息+下载按钮

#### 4. 文档列表页不感知变化
- 卡片显示逻辑不变（title + content 摘要 + security badge）

---

## 不做的

- ❌ 不改 RAG indexing 链路（chunker、embedding、Milvus）——文件解析后的纯文本走跟现在一样的 `content` 字段
- ❌ 不新建 `rag_source_document` 表——复用 `sys_document`，加字段即可
- ❌ 不做文件预览（PDF.js 等）——先只做下载 + 纯文本展示
- ❌ 不做 chunk 重新解析——现有 reindex 机制够用

---

## 文件改动清单

| 文件 | 改动 |
|------|------|
| `docker/init/agent_platform_backup_utf8.sql` | sys_document 加 4 列 |
| `user-service/.../entity/SysDocument.java` | 加 4 字段 |
| `user-service/.../dto/DocumentResponse.java` | 加 fileType, fileSize |
| `user-service/.../controller/DocumentController.java` | 加 upload/download 端点 |
| `user-service/.../service/impl/SysDocumentServiceImpl.java` | 支持文件上传流程 |
| `user-service/.../service/MinioStorageService.java` | **新建** |
| `user-service/.../service/DocumentParserService.java` | **新建** |
| `user-service/pom.xml` | 加 MinIO SDK + PDFBox + POI |
| `user-service/.../resources/application.yml` | MinIO 配置 |
| `web-ui/src/views/document/index.vue` | 弹窗改造 + 卡片图标 + 下载按钮 |
| `web-ui/src/api/department.ts` | 加 upload/download API |
| `web-ui/src/locales/*.ts` | 新增 i18n key |
