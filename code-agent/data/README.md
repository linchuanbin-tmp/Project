# Code Agent Text-to-SQL Dataset

## Structure

```
data/
├── text2sql_dataset.json    # 120 条问答-SQL对，100% MySQL 可执行
├── train_simple.py          # 微调脚本（PyTorch 原生训练循环）
├── eval_quick.py            # 执行准确率评测（MySQL 结果集比较）
├── show_results.py          # 逐条对比展示
├── infer_server.py          # Flask 推理服务器（端口 8090）
└── README.md
```

## Dataset Format (Spider-compatible)

```json
{
  "db_id": "a",
  "question": "List all customers",
  "query": "SELECT * FROM bank_customer"
}
```

## Tables Covered (3 core banking tables)

| Table | Rows | Description |
|-------|------|-------------|
| bank_customer | 5 | Customer profiles with risk levels |
| bank_account | 7 | Bank accounts (savings/checking/fixed) |
| bank_transaction | 10 | Transaction records (deposits/withdrawals/transfers) |

## Model

- **Base**: `cssupport/t5-small-awesome-text-to-sql` (60M params, pre-trained on Spider + WikiSQL)
- **Fine-tuned**: `./fine-tuned-t5-banking/` (8 epochs, 3e-5 LR, 120 examples)
- **Accuracy**: 75% exact match on test set, 100% SQL executable

## Quick Start

```bash
# Training
python train_simple.py

# Evaluation
python eval_quick.py

# Inference Server
python infer_server.py
```

## Query Types

- SELECT * (simple retrieval)
- COUNT (aggregation)
- WHERE = (exact match)
- WHERE > / < / BETWEEN (range)
- ORDER BY + LIMIT (ranking)
- SUM / AVG / MAX / MIN (aggregates)
- JOIN (multi-table)
- GROUP BY (grouping)
- LIKE (pattern matching)
- Date range queries
- DISTINCT / NOT IN
- Subqueries

## Train/Test Split

80 train / 20 test. Use `train.py` to load and split.
