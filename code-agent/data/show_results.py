"""
Show all test cases side by side — LLM
=======================================
逐条对比 LLM 生成的 SQL 与标准答案在 MySQL 中的执行结果。
"""

import json
import os
import sys
import pymysql

DATASET_PATH = 'text2sql_dataset.json'

# ============================================================
# Schema 上下文
# ============================================================
SCHEMA_CONTEXT = """
CREATE TABLE bank_customer (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    customer_no VARCHAR(20)  NOT NULL UNIQUE             COMMENT '客户编号',
    name        VARCHAR(50)  NOT NULL                    COMMENT '客户姓名',
    id_card     VARCHAR(18)                              COMMENT '身份证号',
    phone       VARCHAR(15)                              COMMENT '手机号',
    risk_level  VARCHAR(10)  DEFAULT 'LOW'               COMMENT '风险等级: LOW/MEDIUM/HIGH',
    status      INT          DEFAULT 1                   COMMENT '状态: 1=正常 0=冻结',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     INT          DEFAULT 0
) COMMENT='银行客户表';

CREATE TABLE bank_account (
    id           BIGINT         PRIMARY KEY AUTO_INCREMENT,
    account_no   VARCHAR(30)    NOT NULL UNIQUE            COMMENT '账号',
    customer_id  BIGINT         NOT NULL                   COMMENT '客户ID',
    account_type VARCHAR(20)    DEFAULT 'SAVINGS'          COMMENT '账户类型: SAVINGS/CHECKING/FIXED',
    balance      DECIMAL(18,2)  DEFAULT 0.00               COMMENT '余额(元)',
    currency     VARCHAR(5)     DEFAULT 'CNY'              COMMENT '币种',
    open_date    DATE                                      COMMENT '开户日期',
    status       INT            DEFAULT 1                  COMMENT '状态',
    create_time  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    deleted      INT            DEFAULT 0
) COMMENT='银行账户表';

CREATE TABLE bank_transaction (
    id                   BIGINT         PRIMARY KEY AUTO_INCREMENT,
    txn_no               VARCHAR(40)    NOT NULL                   COMMENT '交易流水号',
    account_id           BIGINT         NOT NULL                   COMMENT '账户ID',
    txn_type             VARCHAR(20)    NOT NULL                   COMMENT '交易类型: DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT',
    amount               DECIMAL(18,2)  NOT NULL                   COMMENT '交易金额',
    balance_after        DECIMAL(18,2)  DEFAULT 0.00               COMMENT '交易后余额',
    counterparty_account VARCHAR(30)                               COMMENT '对方账号',
    remark               VARCHAR(100)                              COMMENT '备注',
    txn_time             DATETIME       NOT NULL                   COMMENT '交易时间',
    create_time          DATETIME       DEFAULT CURRENT_TIMESTAMP
) COMMENT='交易流水表';

CREATE TABLE bank_department (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    dept_name   VARCHAR(50)  NOT NULL COMMENT '部门名称',
    manager_id  BIGINT,
    floor       INT,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     INT          DEFAULT 0
) COMMENT='银行部门表';

CREATE TABLE bank_employee (
    id         BIGINT         PRIMARY KEY AUTO_INCREMENT,
    emp_no     VARCHAR(20)    NOT NULL UNIQUE COMMENT '员工编号',
    name       VARCHAR(50)    NOT NULL            COMMENT '姓名',
    dept_id    BIGINT         NOT NULL,
    position   VARCHAR(50)                        COMMENT '职位',
    salary     DECIMAL(12,2)                      COMMENT '月薪',
    hire_date  DATE,
    phone      VARCHAR(15),
    status     INT            DEFAULT 1,
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    deleted    INT            DEFAULT 0
) COMMENT='银行员工表';

CREATE TABLE bank_loan (
    id            BIGINT         PRIMARY KEY AUTO_INCREMENT,
    loan_no       VARCHAR(30)    NOT NULL UNIQUE COMMENT '贷款编号',
    customer_id   BIGINT         NOT NULL,
    emp_id        BIGINT,
    loan_type     VARCHAR(30)                    COMMENT '贷款类型: HOUSING/AUTO/PERSONAL/BUSINESS',
    amount        DECIMAL(14,2)  NOT NULL        COMMENT '贷款金额',
    interest_rate DECIMAL(5,2)                   COMMENT '年利率(%)',
    term_months   INT,
    start_date    DATE,
    status        VARCHAR(20)    DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/PAID_OFF/DEFAULTED',
    create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP,
    deleted       INT            DEFAULT 0
) COMMENT='银行贷款表';
"""

SYSTEM_PROMPT = f"""你是一位顶级的 MySQL Text-to-SQL 专家。

## 数据库表结构
{SCHEMA_CONTEXT}

## 规则
1. 只生成 SELECT 查询
2. 使用 MySQL 8.0 语法，表名列名加反引号
3. 只输出纯 SQL，不要 markdown 标记、注释或解释"""


def _load_config():
    """加载 DeepSeek 配置"""
    config_path = os.path.join(os.path.dirname(__file__), "ds_config.json")
    try:
        with open(config_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        print("❌ 未找到 ds_config.json，请在 data/ 目录下创建该文件")
        sys.exit(1)


def call_llm(question: str) -> str:
    cfg = _load_config()
    api_key = os.environ.get("DEEPSEEK_API_KEY") or cfg.get("api_key", "")
    base_url = os.environ.get("DEEPSEEK_BASE_URL") or cfg.get("base_url", "https://api.deepseek.com")
    model = os.environ.get("DEEPSEEK_MODEL") or cfg.get("model", "deepseek-chat")

    if not api_key:
        print("❌ 请在 ds_config.json 中填写 api_key")
        sys.exit(1)

    from openai import OpenAI
    client = OpenAI(api_key=api_key, base_url=base_url)

    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": f"请将以下问题转换为 SQL：\n\n{question}"},
        ],
        max_tokens=512,
        temperature=0.1,
    )
    sql = response.choices[0].message.content.strip()
    if sql.startswith("```"):
        lines = sql.split("\n")
        lines = lines[1:] if lines[0].startswith("```") else lines
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        sql = "\n".join(lines).strip()
    return sql


# ============================================================
# 加载数据 & MySQL 连接
# ============================================================
with open(DATASET_PATH, 'r', encoding='utf-8') as f:
    data = json.load(f)

conn = pymysql.connect(host='localhost', user='root', password='zzm20030718',
                       database='agent_platform', charset='utf8mb4')


def run(sql):
    try:
        with conn.cursor() as c:
            c.execute(sql)
            return tuple(sorted(c.fetchall()))
    except:
        return None


# ============================================================
# 逐条评估
# ============================================================
print(f"🧪 逐条对比 {len(data)} 条\n")

ok = 0
for i, item in enumerate(data):
    question = item['question']
    gold = item['query'].strip()

    pred = call_llm(question)

    gr = run(gold)
    pr = run(pred)
    match = gr == pr
    if match:
        ok += 1
    tag = 'MATCH' if match else ('ERR' if pr is None else 'DIFF')

    q = question[:50] + '...' if len(question) > 50 else question
    g = gold[:85] + '...' if len(gold) > 85 else gold
    p = pred[:85] + '...' if len(pred) > 85 else pred

    print(f'[{i+1:3d}] {tag:6s} | {q}')
    print(f'       G: {g}')
    print(f'       P: {p}')
    print()

print('=' * 60)
print(f'RESULTS: {ok}/{len(data)} = {ok/len(data)*100:.1f}%')
conn.close()
