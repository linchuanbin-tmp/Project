"""
Text-to-SQL Inference Server — DeepSeek API 驱动
================================================
架构：自然语言 → DeepSeek API（携带完整表 metadata）→ SQL
Java 后端通过 HTTP POST /infer 调用，返回 {"sql": "...", "method": "DEEPSEEK-V3"}

环境变量：
  DEEPSEEK_API_KEY    DeepSeek API Key（必需）
  DEEPSEEK_BASE_URL   API 地址（默认 https://api.deepseek.com）
  DEEPSEEK_MODEL      模型名（默认 deepseek-chat）
  DB_HOST / DB_USER / DB_PASSWORD / DB_NAME  MySQL 连接信息
"""

import os
import json
import logging
from flask import Flask, request, jsonify

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

# ============================================================
# 配置（从 ds_config.json 读取，环境变量优先级更高）
# ============================================================
def _load_config():
    """加载 DeepSeek 配置：环境变量 > ds_config.json > 默认值"""
    config = {
        "api_key": os.environ.get("DEEPSEEK_API_KEY", ""),
        "base_url": os.environ.get("DEEPSEEK_BASE_URL", ""),
        "model": os.environ.get("DEEPSEEK_MODEL", ""),
    }
    config_path = os.path.join(os.path.dirname(__file__), "ds_config.json")
    try:
        with open(config_path, "r", encoding="utf-8") as f:
            file_cfg = json.load(f)
            if not config["api_key"]:
                config["api_key"] = file_cfg.get("api_key", "")
            if not config["base_url"]:
                config["base_url"] = file_cfg.get("base_url", "https://api.deepseek.com")
            if not config["model"]:
                config["model"] = file_cfg.get("model", "deepseek-chat")
            log.info("📄 从 ds_config.json 加载配置")
    except FileNotFoundError:
        log.warning("⚠️ ds_config.json 未找到，使用环境变量或默认值")
    except Exception as e:
        log.warning("⚠️ 读取 ds_config.json 失败: %s", e)
    # 最终默认值
    if not config["base_url"]:
        config["base_url"] = "https://api.deepseek.com"
    if not config["model"]:
        config["model"] = "deepseek-chat"
    return config

_cfg = _load_config()
DEEPSEEK_API_KEY = _cfg["api_key"]
DEEPSEEK_BASE_URL = _cfg["base_url"]
DEEPSEEK_MODEL = _cfg["model"]

MAX_TOKENS = 1024
TEMPERATURE = 0.1  # Text-to-SQL 需要确定性输出

# ============================================================
# 表 Schema 上下文（优先从 MySQL 动态加载，失败则用硬编码兜底）
# ============================================================

HARDCODED_SCHEMA = """
CREATE TABLE bank_customer (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    customer_no VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(50)  NOT NULL,
    id_card     VARCHAR(18),
    phone       VARCHAR(15),
    risk_level  VARCHAR(10)  DEFAULT 'LOW',
    status      INT          DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    deleted     INT          DEFAULT 0
);

CREATE TABLE bank_account (
    id           BIGINT         PRIMARY KEY AUTO_INCREMENT,
    account_no   VARCHAR(30)    NOT NULL UNIQUE,
    customer_id  BIGINT         NOT NULL,
    account_type VARCHAR(20)    DEFAULT 'SAVINGS',
    balance      DECIMAL(18,2)  DEFAULT 0.00,
    currency     VARCHAR(5)     DEFAULT 'CNY',
    open_date    DATE,
    status       INT            DEFAULT 1,
    create_time  DATETIME,
    deleted      INT            DEFAULT 0
);

CREATE TABLE bank_transaction (
    id                   BIGINT         PRIMARY KEY AUTO_INCREMENT,
    txn_no               VARCHAR(40)    NOT NULL,
    account_id           BIGINT         NOT NULL,
    txn_type             VARCHAR(20)    NOT NULL,
    amount               DECIMAL(18,2)  NOT NULL,
    balance_after        DECIMAL(18,2)  DEFAULT 0.00,
    counterparty_account VARCHAR(30),
    remark               VARCHAR(100),
    txn_time             DATETIME       NOT NULL,
    create_time          DATETIME
);

CREATE TABLE bank_department (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    dept_name   VARCHAR(50)  NOT NULL,
    manager_id  BIGINT,
    floor       INT,
    create_time DATETIME,
    deleted     INT          DEFAULT 0
);

CREATE TABLE bank_employee (
    id         BIGINT         PRIMARY KEY AUTO_INCREMENT,
    emp_no     VARCHAR(20)    NOT NULL UNIQUE,
    name       VARCHAR(50)    NOT NULL,
    dept_id    BIGINT         NOT NULL,
    position   VARCHAR(50),
    salary     DECIMAL(12,2),
    hire_date  DATE,
    phone      VARCHAR(15),
    status     INT            DEFAULT 1,
    create_time DATETIME,
    deleted    INT            DEFAULT 0
);

CREATE TABLE bank_loan (
    id            BIGINT         PRIMARY KEY AUTO_INCREMENT,
    loan_no       VARCHAR(30)    NOT NULL UNIQUE,
    customer_id   BIGINT         NOT NULL,
    emp_id        BIGINT,
    loan_type     VARCHAR(30),
    amount        DECIMAL(14,2)  NOT NULL,
    interest_rate DECIMAL(5,2),
    term_months   INT,
    start_date    DATE,
    status        VARCHAR(20)    DEFAULT 'ACTIVE',
    create_time   DATETIME,
    deleted       INT            DEFAULT 0
);
"""


# ============================================================
# 动态加载 MySQL 表结构
# ============================================================

def _load_schema_from_mysql() -> str:
    """从 MySQL information_schema 动态读取完整表结构作为 prompt 上下文"""
    try:
        import pymysql
        conn = pymysql.connect(
            host=os.environ.get("DB_HOST", "localhost"),
            port=int(os.environ.get("DB_PORT", "3306")),
            user=os.environ.get("DB_USER", "root"),
            password=os.environ.get("DB_PASSWORD", ""),
            database=os.environ.get("DB_NAME", "agent_platform"),
            charset="utf8mb4",
            connect_timeout=5,
        )
        with conn.cursor() as cur:
            db_name = conn.db.decode() if isinstance(conn.db, bytes) else conn.db

            # 获取所有用户表
            cur.execute("""
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = %s AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
            """, (db_name,))
            tables = cur.fetchall()

            if not tables:
                conn.close()
                return ""

            lines = ["-- ========================================",
                     f"-- 数据库: {db_name} (动态加载)",
                     "-- ========================================\n"]

            for table_name, table_comment in tables:
                # 获取列信息
                cur.execute("""
                    SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY,
                           COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
                    ORDER BY ORDINAL_POSITION
                """, (db_name, table_name))
                columns = cur.fetchall()

                lines.append(f"CREATE TABLE {table_name} (")
                col_defs = []
                for col in columns:
                    col_name, col_type, nullable, key, default, extra, comment = col
                    parts = [f"    {col_name} {col_type}"]
                    if nullable == "NO":
                        parts.append("NOT NULL")
                    if default is not None:
                        parts.append(f"DEFAULT {default}")
                    if extra and extra != "DEFAULT_GENERATED":
                        parts.append(extra)
                    if comment:
                        parts.append(f"COMMENT '{comment}'")
                    col_defs.append(" ".join(parts))

                # 提取主键
                cur.execute("""
                    SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                    WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
                      AND CONSTRAINT_NAME = 'PRIMARY'
                    ORDER BY ORDINAL_POSITION
                """, (db_name, table_name))
                pk_cols = [r[0] for r in cur.fetchall()]
                if pk_cols:
                    col_defs.append(f"    PRIMARY KEY ({', '.join(pk_cols)})")

                lines.append(",\n".join(col_defs))
                comment_suffix = f" COMMENT='{table_comment}'" if table_comment else ""
                lines.append(f"){comment_suffix};\n")

        conn.close()
        schema = "\n".join(lines)
        log.info("✅ 从 MySQL 动态加载了 %d 张表结构", len(tables))
        return schema

    except Exception as e:
        log.warning("⚠️ 无法连接 MySQL (%s)，使用硬编码 schema", e)
        return ""


# ============================================================
# 加载 Schema（优先 MySQL 动态加载）
# ============================================================
DYNAMIC_SCHEMA = _load_schema_from_mysql()
SCHEMA_CONTEXT = DYNAMIC_SCHEMA if DYNAMIC_SCHEMA else HARDCODED_SCHEMA
log.info("📋 Schema 上下文已加载 (%d 字符)", len(SCHEMA_CONTEXT))


# ============================================================
# DeepSeek API 调用
# ============================================================

def _build_system_prompt() -> str:
    return f"""你是一个 MySQL SQL 生成器。根据用户问题，只输出一条可执行的 SELECT 语句。
不要输出任何解释、注释或 markdown 标记。

数据库表结构：
{SCHEMA_CONTEXT}"""


def call_llm(question: str) -> dict:
    """调用 LLM API 生成 SQL"""
    if not DEEPSEEK_API_KEY:
        log.error("❌ API Key 未设置！")
        return {"sql": "", "method": "LLM", "error": "API Key not set"}

    try:
        from openai import OpenAI

        client = OpenAI(
            api_key=DEEPSEEK_API_KEY,
            base_url=DEEPSEEK_BASE_URL,
        )

        system_prompt = _build_system_prompt()
        user_message = question

        log.info("🚀 调用 LLM API...")
        log.debug("System prompt length: %d chars", len(system_prompt))

        response = client.chat.completions.create(
            model=DEEPSEEK_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message},
            ],
            max_tokens=MAX_TOKENS,
            temperature=TEMPERATURE,
        )

        sql = response.choices[0].message.content.strip()

        # 清理可能的 markdown 代码块标记
        if sql.startswith("```"):
            lines = sql.split("\n")
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            sql = "\n".join(lines).strip()

        log.info("✅ 生成 SQL (%d chars): %s", len(sql), sql[:120])
        return {"sql": sql, "method": "LLM"}

    except ImportError:
        log.error("❌ openai 库未安装，请执行: pip install openai")
        return {"sql": "", "method": "LLM", "error": "openai package not installed"}
    except Exception as e:
        log.error("❌ LLM API 调用失败: %s", e)
        return {"sql": "", "method": "LLM", "error": str(e)}


# ============================================================
# Flask 服务
# ============================================================
app = Flask(__name__)


@app.route("/infer", methods=["POST"])
def infer():
    """核心推理接口：自然语言 → SQL"""
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"error": "request body required"}), 400

    question = data.get("question", "").strip()
    if not question:
        return jsonify({"error": "question required"}), 400

    log.info("📩 收到推理请求: %s", question[:100])

    result = call_llm(question)

    if result.get("error"):
        return jsonify({
            "sql": "",
            "method": result["method"],
            "error": result["error"],
        }), 500

    return jsonify({
        "sql": result["sql"],
        "method": result["method"],
    })


@app.route("/route", methods=["POST"])
def route_intent():
    """意图路由接口：分析用户输入的提问类型"""
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"error": "request body required"}), 400

    question = data.get("question", "").strip()
    if not question:
        return jsonify({"error": "question required"}), 400

    log.info("📩 收到分类意图请求: %s", question[:100])

    if not DEEPSEEK_API_KEY:
        log.warning("⚠️ API Key not set, fallback to simple keyword routing")
        intent = "RAG"
        q_lower = question.lower()
        if any(w in q_lower for w in ["select", "show", "table", "查询", "统计", "余额", "账单", "交易", "账户"]):
            intent = "CODE"
        elif any(w in q_lower for w in ["会议室", "预订", "日程", "时间冲突", "发邮件", "安排"]):
            intent = "TOOL"
        return jsonify({"intent": intent, "method": "KEYWORD"})

    try:
        from openai import OpenAI
        client = OpenAI(
            api_key=DEEPSEEK_API_KEY,
            base_url=DEEPSEEK_BASE_URL,
        )

        system_prompt = """你是一个意图分类器。请根据用户的输入，将其归类为以下三类之一：
- CODE: 如果用户想要查询数据库、生成 SQL、统计或查看报表数据。
- TOOL: 如果用户想要进行某些业务操作、日程安排、发邮件、订会议室、解决日程冲突、路径规划。
- RAG: 如果用户在询问规章制度、文档内容、概念定义或政策规程。

请只输出大写单词：CODE、TOOL 或 RAG。绝对不要包含任何其他解释、前导词或 markdown 标记。"""

        response = client.chat.completions.create(
            model=DEEPSEEK_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": question},
            ],
            max_tokens=10,
            temperature=0.1,
        )

        intent = response.choices[0].message.content.strip().upper()
        if "CODE" in intent:
            intent = "CODE"
        elif "TOOL" in intent:
            intent = "TOOL"
        else:
            intent = "RAG"

        log.info("✅ 意图分类结果: %s", intent)
        return jsonify({"intent": intent, "method": "LLM"})

    except Exception as e:
        log.error("❌ 意图分类失败: %s", e)
        intent = "RAG"
        q_lower = question.lower()
        if any(w in q_lower for w in ["select", "show", "table", "查询", "统计", "余额", "账单", "交易", "账户"]):
            intent = "CODE"
        elif any(w in q_lower for w in ["会议室", "预订", "日程", "时间冲突", "发邮件", "安排"]):
            intent = "TOOL"
        return jsonify({"intent": intent, "method": "FALLBACK", "error": str(e)})


@app.route("/health")
def health():
    """健康检查"""
    has_api_key = bool(DEEPSEEK_API_KEY)
    return jsonify({
        "status": "UP" if has_api_key else "NO_API_KEY",
        "model": DEEPSEEK_MODEL,
        "schema_source": "mysql" if DYNAMIC_SCHEMA else "hardcoded",
        "schema_tables": SCHEMA_CONTEXT.count("CREATE TABLE"),
    })


@app.route("/schema")
def get_schema():
    """查看当前使用的 schema 上下文（调试用）"""
    return jsonify({"schema": SCHEMA_CONTEXT, "length": len(SCHEMA_CONTEXT)})


if __name__ == "__main__":
    log.info("=" * 55)
    log.info("🚀 Text-to-SQL Inference Server")
    log.info("   Model:  %s", DEEPSEEK_MODEL)
    log.info("   Schema: %d tables (%s)", SCHEMA_CONTEXT.count("CREATE TABLE"),
             "MySQL dynamic" if DYNAMIC_SCHEMA else "hardcoded")
    log.info("   Port:   8090")
    log.info("=" * 55)

    app.run(host="0.0.0.0", port=8090, debug=False)
