# Code Agent — 银行业务 Text-to-SQL 模块

基于微调 T5-small 模型的自然语言转 SQL 系统，覆盖银行客户、账户、交易 3 张核心表。Java 后端通过 HTTP 调用 Python 推理服务，生成的 SQL 经白名单校验后直接执行并返回结果。

---

## 一、项目概述

```
用户输入: "查询余额大于50000的账户"
    ↓
Java Code Agent (8084) → Python T5 推理 (8090) → SQL 生成
    ↓
白名单校验 (5层) → MySQL 执行 → JSON 结果返回
```

### 核心指标

| 指标 | 数值 |
|------|:---:|
| 模型参数 | 60M (t5-small) |
| 训练数据 | 120 条问答-SQL 对 |
| 测试准确率 | **75%** |
| SQL 可执行率 | **100%** |
| 覆盖数据表 | 3 张（bank_customer, bank_account, bank_transaction） |

---

## 二、环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | Spring Boot 运行环境 |
| Maven | 3.9+ | 后端构建 |
| MySQL | 8.0+ | 业务数据库 |
| Python | 3.11 | 模型推理 |
| PyTorch | 2.3+ (CPU) | 模型运行 |
| transformers | 4.40+ | 模型加载 |

> ⚠️ Redis 非必须——元数据缓存失败时会自动降级到 MySQL 直读。

---

## 三、快速启动

### 3.1 准备数据库

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS agent_platform CHARACTER SET utf8mb4"

# 2. 导入表结构和测试数据
mysql -u root -p agent_platform < src/main/resources/banking_data.sql
```

### 3.2 启动 Python 推理服务

```bash
cd data
conda activate codeagent          # 或你的 Python 环境
python infer_server.py            # 启动在 http://localhost:8090
```

输出应显示：
```
Model loaded. Starting server on :8090
 * Running on http://127.0.0.1:8090
```

### 3.3 启动 Java 后端

```bash
# 在项目根目录
mvnw.cmd spring-boot:run          # Windows
# 或
./mvnw spring-boot:run             # Linux/macOS
```

输出应显示：
```
Started CodeAgentApplication in 4.398 seconds
? 缓存预热完成！已加载 9 张表的元数据
```

### 3.4 测试接口

```bash
curl -X POST http://localhost:8084/api/code/query \
  -H "Content-Type: application/json" \
  -d '{"question": "查询所有客户"}'
```

返回：
```json
{
  "success": true,
  "sql": "SELECT * FROM bank_customer",
  "columns": ["id", "customer_no", "name", ...],
  "rows": [{"id": 1, "name": "张三", ...}, ...],
  "rowCount": 5,
  "inferenceMethod": "T5-SMALL-TEXT2SQL",
  "whitelistPassed": true
}
```

---

## 四、项目结构

```
code-agent/
├── pom.xml                              # Spring Boot 3.2.0 配置
├── .gitignore                           # 排除模型权重 & 编译产物
├── mvnw.cmd                             # Maven Wrapper
│
├── data/                                # Python 训练 & 推理
│   ├── text2sql_dataset.json            # 120 条训练数据
│   ├── train_simple.py                  # 微调脚本（PyTorch 原生训练循环）
│   ├── infer_server.py                  # Flask 推理服务器 (port 8090)
│   ├── eval_quick.py                    # 准确率评测（MySQL 执行对比）
│   ├── show_results.py                  # 逐条结果对比
│   └── README.md
│
└── src/
    ├── main/java/com/agent/code/
    │   ├── CodeAgentApplication.java    # Spring Boot 启动类
    │   ├── config/
    │   │   ├── CodeAgentProperties.java # 配置属性绑定
    │   │   ├── MetadataCacheManager.java# 缓存预热调度
    │   │   └── OnnxConfig.java          # 推理模式切换
    │   ├── controller/
    │   │   └── CodeAgentController.java # REST API (7 个端点)
    │   ├── dto/
    │   │   ├── CodeGenerationRequest.java
    │   │   ├── CodeGenerationResponse.java
    │   │   └── MetadataCacheResponse.java
    │   ├── entity/
    │   │   ├── TableMetadata.java       # 表元数据
    │   │   └── ColumnMetadata.java      # 列元数据
    │   └── service/
    │       ├── CodeGenerationService.java       # 接口
    │       ├── OnnxCodeGenerationService.java   # LLM 推理实现
    │       ├── TemplateCodeGenerationService.java # 模板引擎（fallback）
    │       ├── SqlValidationService.java        # 5层白名单
    │       ├── SqlExecutionService.java         # SQL 执行
    │       └── MetadataCacheService.java        # 元数据缓存
    ├── main/resources/
    │   ├── application.yml              # 应用配置
    │   ├── banking_data.sql             # 核心 3 表 DDL + 测试数据
    │   ├── hr_loan_data.sql             # 扩展表（预留，不在模型覆盖范围）
    │   └── models/README.md
    └── test/java/com/agent/code/
        ├── SqlValidationServiceTest.java      # 白名单测试 (21 cases)
        └── TemplateCodeGenerationServiceTest.java # 模板引擎测试 (15 cases)
```

---

## 五、API 接口

基路径：`http://localhost:8084/api/code`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/query` | 一键生成 SQL + 执行（推荐） |
| POST | `/generate` | 仅生成 SQL，不执行 |
| POST | `/validate` | 校验 SQL 是否通过白名单 |
| GET | `/metadata` | 查看缓存的表结构 |
| POST | `/metadata/refresh` | 手动刷新元数据缓存 |
| GET | `/health` | 健康检查 |

### 请求示例

```json
// POST /api/code/query
{ "question": "统计每种交易类型的笔数" }

// POST /api/code/validate
{ "sql": "SELECT * FROM bank_customer" }
```

---

## 六、模型训练

### 6.1 数据准备

数据集格式（Spider 兼容）：

```json
{
  "db_id": "a",
  "question": "List all customers",
  "query": "SELECT * FROM bank_customer"
}
```

### 6.2 训练命令

```bash
cd data
python train_simple.py
```

| 超参数 | 值 |
|--------|:--:|
| Epochs | 8 |
| Learning Rate | 3e-5 |
| Batch Size | 4 |
| 训练集/测试集 | 96 / 24 (8:2) |
| 基础模型 | cssupport/t5-small-awesome-text-to-sql |

训练完成后模型保存在 `data/fine-tuned-t5-banking/`，推理服务器自动加载。

### 6.3 评测

```bash
python eval_quick.py    # MySQL 执行准确率
python show_results.py  # 逐条对比
```

---

## 七、SQL 白名单（5 层防护）

| 层级 | 规则 | 示例 |
|:--:|------|------|
| 1 | 仅允许 `SELECT` | 拒绝 UPDATE/DELETE/DROP |
| 2 | 禁止危险关键字 | 拒绝 UNION/INSERT/EXEC |
| 3 | 表名白名单 | 仅允许 information_schema 中存在的表 |
| 4 | 列名白名单 | 仅允许表中实际存在的列 |
| 5 | 复杂度限制 | 拒绝过深嵌套子查询 |

---

## 八、架构设计

```mermaid
graph TD
    A[用户请求] -->|POST /api/code/query| B[CodeAgentController]
    B --> C{推理模式}
    C -->|onnx.enabled=true| D[OnnxCodeGenerationService]
    C -->|false| E[TemplateCodeGenerationService]
    D -->|HTTP POST| F[Python:8090]
    F --> G[T5-small 模型]
    G --> H[SQL]
    E --> H
    H --> I[SqlValidationService]
    I -->|通过| J[SqlExecutionService]
    I -->|拒绝| K[返回错误]
    J --> L[MySQL]
    L --> M[JSON 结果]
```

---

## 九、合并到主工程

本模块可独立运行，也可作为 `linchuanbin-tmp/Project` 的子模块合并。合并时注意：

1. **Spring Boot 版本**：主工程用 3.1.8，本模块用 3.2.0，建议统一
2. **端口**：8084，与现有服务（8080/8081/8082/8083）无冲突
3. **数据库**：共用 `agent_platform`，执行 `banking_data.sql` 即可
4. **模型权重**：已 gitignore，需在新环境重新训练或拷贝

---

## 十、已知限制

| 限制 | 说明 |
|------|------|
| 仅 3 张表 | bank_customer / bank_account / bank_transaction |
| 仅 SELECT | 不支持 INSERT/UPDATE/DELETE |
| t5-small 上限 | 512 token 上下文窗口 |
| 训练数据量 | 120 条，复杂 JOIN 语句准确率较低 |
| CPU 推理 | 单次推理约 100-300ms |
