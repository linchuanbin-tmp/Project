# Code Agent Text-to-SQL Inference

## 架构

```
自然语言 → LLM API (携带完整表 metadata) → SQL → 白名单校验 → MySQL 执行
```

不再使用本地 T5-small 模型，全部推理由 LLM API 完成。

## 文件结构

```
data/
├── text2sql_dataset.json      # 120 条问答-SQL 对（训练评估用）
├── infer_server.py            # Flask 推理服务器（LLM API，端口 8090）
├── train_simple.py            # 本地模型训练脚本（实验性）
├── distill_labels.py          # 知识蒸馏：用 API 批量生成训练标签
├── eval_quick.py              # 执行准确率评测
├── show_results.py            # 逐条对比展示
├── ds_config.json             # LLM API 配置文件
└── README.md
```

## 快速启动

### 1. 配置 API Key

编辑 `data/ds_config.json`，填入你的 API Key：

```json
{
  "api_key": "sk-xxxxxxxxxxxxxxxx",
  "base_url": "https://api.deepseek.com",
  "model": "deepseek-chat"
}
```

> 💡 **优先级**：环境变量 > `ds_config.json`。两者兼容。
>
> ⚠️ `ds_config.json` 已在 `.gitignore` 中，不会被提交到 Git。

### 2. 安装依赖

```bash
pip install flask openai pymysql
```

### 3. 启动服务

```bash
python infer_server.py
# 输出: 🚀 Text-to-SQL Inference Server
#       Port: 8090
```

### 4. 测试接口

```bash
curl -X POST http://localhost:8090/infer \
  -H "Content-Type: application/json" \
  -d '{"question": "查询余额大于50000的账户"}'

# 返回:
# {"method": "LLM", "sql": "SELECT * FROM `bank_account` WHERE `balance` > 50000"}
```

```bash
# 健康检查
curl http://localhost:8090/health
# {"schema_source": "mysql", "schema_tables": 6, "status": "UP"}

# 查看当前 Schema 上下文
curl http://localhost:8090/schema
```

## Schema 上下文加载策略

1. **优先** 从 MySQL `information_schema` 动态读取所有表的完整 DDL（含注释）
2. **兜底** 若 MySQL 不可用，使用硬编码的 6 张表 schema

这样无论数据库如何变更，prompt 中的 metadata 始终与实际表结构同步。

## Prompt 设计

System prompt 包含三部分：
1. **角色设定**：顶级 MySQL Text-to-SQL 专家
2. **完整表结构**：所有 CREATE TABLE 语句（含列注释、外键关系）
3. **生成规则**：10 条 SQL 规范约束

这种设计让 LLM 能「看见」完整的数据库 schema，生成的 SQL 准确率远超本地小模型。

## 评估

```bash
# 仍需评估？运行 eval_quick.py 即可
python eval_quick.py
```

## 本地模型训练（可选）

推理服务默认用 LLM API。如需本地备选模型：

```bash
# 1. 知识蒸馏（用 API 生成高质量标签）
python distill_labels.py       # → 生成 distilled_dataset.json

# 2. 训练 flan-t5-base（自动使用蒸馏数据）
python train_simple.py         # → 模型保存到 ./fine-tuned-flan-t5/
```

| 模型 | 参数 | 大小 |
|------|:--:|:--:|
| **flan-t5-base** (新) | 220M | ~990MB |
| ~~t5-small~~ (已删除) | 60M | ~230MB |
