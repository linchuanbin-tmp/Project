"""
Execution Accuracy — LLM 评估
==============================
对比 LLM 生成的 SQL 和标准答案在 MySQL 中的执行结果。
"""

import json
import os
import sys

DATASET_PATH = "text2sql_dataset.json"

# ============================================================
# Schema 上下文（与 infer_server.py 保持一致）
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
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP   COMMENT '更新时间',
    deleted     INT          DEFAULT 0                   COMMENT '逻辑删除'
) COMMENT='银行客户表';

CREATE TABLE bank_account (
    id           BIGINT         PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    account_no   VARCHAR(30)    NOT NULL UNIQUE            COMMENT '账号',
    customer_id  BIGINT         NOT NULL                   COMMENT '客户ID → bank_customer.id',
    account_type VARCHAR(20)    DEFAULT 'SAVINGS'          COMMENT '账户类型: SAVINGS/CHECKING/FIXED',
    balance      DECIMAL(18,2)  DEFAULT 0.00               COMMENT '余额(元)',
    currency     VARCHAR(5)     DEFAULT 'CNY'              COMMENT '币种',
    open_date    DATE                                      COMMENT '开户日期',
    status       INT            DEFAULT 1                  COMMENT '状态: 1=正常 0=销户',
    create_time  DATETIME       DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted      INT            DEFAULT 0                  COMMENT '逻辑删除'
) COMMENT='银行账户表';

CREATE TABLE bank_transaction (
    id                   BIGINT         PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    txn_no               VARCHAR(40)    NOT NULL                   COMMENT '交易流水号',
    account_id           BIGINT         NOT NULL                   COMMENT '账户ID → bank_account.id',
    txn_type             VARCHAR(20)    NOT NULL                   COMMENT '交易类型: DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT',
    amount               DECIMAL(18,2)  NOT NULL                   COMMENT '交易金额(元)',
    balance_after        DECIMAL(18,2)  DEFAULT 0.00               COMMENT '交易后余额',
    counterparty_account VARCHAR(30)                               COMMENT '对方账号',
    remark               VARCHAR(100)                              COMMENT '备注',
    txn_time             DATETIME       NOT NULL                   COMMENT '交易时间',
    create_time          DATETIME       DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间'
) COMMENT='交易流水表';

CREATE TABLE bank_department (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    dept_name   VARCHAR(50)  NOT NULL                   COMMENT '部门名称',
    manager_id  BIGINT                                  COMMENT '负责人ID',
    floor       INT                                     COMMENT '办公楼层',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted     INT          DEFAULT 0                  COMMENT '逻辑删除'
) COMMENT='银行部门表';

CREATE TABLE bank_employee (
    id         BIGINT         PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    emp_no     VARCHAR(20)    NOT NULL UNIQUE            COMMENT '员工编号',
    name       VARCHAR(50)    NOT NULL                   COMMENT '姓名',
    dept_id    BIGINT         NOT NULL                   COMMENT '部门ID',
    position   VARCHAR(50)                               COMMENT '职位',
    salary     DECIMAL(12,2)                             COMMENT '月薪',
    hire_date  DATE                                     COMMENT '入职日期',
    phone      VARCHAR(15)                               COMMENT '电话',
    status     INT            DEFAULT 1                  COMMENT '状态: 1=在职 0=离职',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted    INT            DEFAULT 0                  COMMENT '逻辑删除'
) COMMENT='银行员工表';

CREATE TABLE bank_loan (
    id            BIGINT         PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    loan_no       VARCHAR(30)    NOT NULL UNIQUE            COMMENT '贷款编号',
    customer_id   BIGINT         NOT NULL                   COMMENT '客户ID',
    emp_id        BIGINT                                    COMMENT '审批员工ID',
    loan_type     VARCHAR(30)                               COMMENT '贷款类型: HOUSING/AUTO/PERSONAL/BUSINESS',
    amount        DECIMAL(14,2)  NOT NULL                   COMMENT '贷款金额',
    interest_rate DECIMAL(5,2)                              COMMENT '年利率(%)',
    term_months   INT                                       COMMENT '期限(月)',
    start_date    DATE                                      COMMENT '起贷日期',
    status        VARCHAR(20)    DEFAULT 'ACTIVE'           COMMENT '状态: ACTIVE/PAID_OFF/DEFAULTED',
    create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted       INT            DEFAULT 0                  COMMENT '逻辑删除'
) COMMENT='银行贷款表';
"""

SYSTEM_PROMPT = f"""你是一位顶级的 MySQL Text-to-SQL 专家。

## 数据库表结构
{SCHEMA_CONTEXT}

## 规则
1. 只生成 SELECT 查询
2. 使用 MySQL 8.0 语法，表名列名加反引号
3. 只输出纯 SQL，不要 markdown 标记、注释或解释"""


# ============================================================
# DeepSeek API 调用
# ============================================================
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
    # 清理 markdown 代码块
    if sql.startswith("```"):
        lines = sql.split("\n")
        lines = lines[1:] if lines[0].startswith("```") else lines
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        sql = "\n".join(lines).strip()
    return sql


# ============================================================
# MySQL 连接
# ============================================================
print("Loading MySQL...")
try:
    import pymysql
    conn = pymysql.connect(host="localhost", user="root", password="zzm20030718",
                           database="agent_platform", charset="utf8mb4")
    print("✅ MySQL 已连接")
except Exception as e:
    conn = None
    print(f"⚠️ 无 MySQL ({e})，仅做字符串匹配")


def run(sql):
    if conn is None:
        return None
    try:
        with conn.cursor() as c:
            c.execute(sql)
            return tuple(sorted(c.fetchall()))
    except Exception:
        return None


# ============================================================
# 加载数据集
# ============================================================
with open(DATASET_PATH, "r", encoding="utf-8") as f:
    data = json.load(f)
test = data[int(len(data) * 0.8):]

print(f"\n🧪 评估 {len(test)} 条测试用例（DeepSeek API）...\n")

exact = exec_ok = errs = 0
for i, item in enumerate(test):
    question = item["question"]
    gold = item["query"].strip()

    # 调用 LLM API
    pred = call_llm(question)

    # 字符串精确匹配
    e = pred.lower() == gold.lower()
    if e:
        exact += 1

    # MySQL 执行结果匹配
    gr, pr = run(gold), run(pred)
    if pr is None:
        errs += 1
        s = "ERR"
    elif gr == pr:
        exec_ok += 1
        s = "OK"
    else:
        s = "DIFF"

    # 打印前 15 条
    if i < 15:
        tag = "MATCH" if e else s
        print(f"[{i+1:3d}] {tag:6s} | {question[:65]}")
        if not e and s == "DIFF":
            print(f"       P: {pred[:85]}")

# ============================================================
# 结果汇总
# ============================================================
print(f"\n{'='*55}")
print(f"Exact Match:     {exact}/{len(test)} = {exact/len(test)*100:.1f}%")
if conn:
    print(f"Execution Match: {exec_ok}/{len(test)} = {exec_ok/len(test)*100:.1f}%")
    print(f"SQL Errors:      {errs}/{len(test)}")
    print(f"Any Correct:     {exact+exec_ok}/{len(test)} = {(exact+exec_ok)/len(test)*100:.1f}%")
    conn.close()
