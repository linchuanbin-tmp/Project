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
            timeout=45.0,  # 45s timeout — client times out before Java's 60s
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
        log.error("❌ LLM调用失败: %s", result["error"])
        return jsonify({
            "sql": "",
            "method": result["method"],
            "error": result["error"],
        }), 500

    log.info("✅ 推理成功，返回SQL (%d chars)", len(result["sql"]))
    return jsonify({
        "sql": result["sql"],
        "method": result["method"],
    })


@app.route("/route", methods=["POST"])
def route_intent():
    """意图路由接口：分析用户输入的提问类型"""
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"code": 400, "message": "request body required", "data": None}), 400

    question = data.get("question", "").strip()
    if not question:
        return jsonify({"code": 400, "message": "question required", "data": None}), 400

    log.info("📩 收到分类意图请求: %s", question[:100])

    q_lower = question.lower()
    # Check greetings first for keyword fallback
    if any(w in q_lower for w in ["你好", "您好", "hello", "hi", "hey", "who are you", "你是谁", "是谁", "助手", "小助手", "copilot"]):
        return jsonify({
            "code": 200,
            "message": "success",
            "data": {
                "intent": "CHAT",
                "reply": "你好！我是你的智能助理 Copilot，随时可以帮您处理 SQL 查询、会议室预订、日程冲突检测或知识库检索。请问今天有什么我可以帮您的？",
                "method": "KEYWORD"
            }
        })

    if not DEEPSEEK_API_KEY:
        log.warning("⚠️ API Key not set, fallback to simple keyword routing")
        intent = "RAG"
        if any(w in q_lower for w in ["select", "show", "table", "查询", "统计", "余额", "账单", "交易", "账户"]):
            intent = "CODE"
        elif any(w in q_lower for w in ["会议室", "预订", "日程", "时间冲突", "发邮件", "安排"]):
            # Check if booking details are missing
            has_date = any(w in q_lower for w in ["今", "明", "后", "下午", "上午", "点", "分", "time", "date", "today", "tomorrow", "pm", "am"])
            has_capacity = any(w in q_lower for w in ["人", "个", "位", "pax", "capacity", "people"])
            if not (has_date and has_capacity):
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "intent": "CLARIFY",
                        "reply": "好的，请问您需要预定哪一天的会议室？预计有多少人参加？",
                        "method": "KEYWORD_CLARIFY"
                    }
                })
            intent = "TOOL"
        elif any(w in q_lower for w in ["规划", "路线", "地图", "驾车"]):
            # Check if start and end are missing
            if "从" not in q_lower or "到" not in q_lower:
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "intent": "CLARIFY",
                        "reply": "请提供您的路线起点和终点（例如：从香港大学到香港国际机场）。",
                        "method": "KEYWORD_CLARIFY"
                    }
                })
            intent = "TOOL"
        elif any(w in q_lower for w in ["设置", "settings", "个人中心", "密码", "password", "profile"]):
            intent = "SETTINGS"
        return jsonify({
            "code": 200,
            "message": "success",
            "data": {"intent": intent, "method": "KEYWORD"}
        })

    try:
        from openai import OpenAI
        client = OpenAI(
            api_key=DEEPSEEK_API_KEY,
            base_url=DEEPSEEK_BASE_URL,
        )

        system_prompt = """你是一个智能意图分类器。请根据用户的输入，将其归类为以下六类之一：
- CODE: 如果用户想要查询数据库、生成 SQL、统计或查看报表数据。
- TOOL: 如果用户想要进行业务操作，例如订会议室（需包含日期/时间/人数）、日程冲突检测（需包含人员/时间范围）、路径规划（需包含起点和终点）。
- RAG: 如果用户在询问规章制度、文档内容、概念定义或政策规程。
- SETTINGS: 如果用户想要修改密码、查看个人资料、更改系统设置或配置。
- CLARIFY: 如果用户表达了想订会议室或规划路线的意图，但缺少核心关键参数（例如：订会议室没说日期/时间/人数；规划路线没有提供起点或终点）。
- CHAT: 如果用户在说你好、打招呼、进行简单问候、问你是谁，或者进行与上述业务无关的日常闲聊对话。

请只输出对应的大写单词：CODE、TOOL、RAG、SETTINGS、CLARIFY 或 CHAT。绝对不要包含任何其他解释、前导词或 markdown 标记。"""

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
        elif "SETTINGS" in intent:
            intent = "SETTINGS"
        elif "CLARIFY" in intent:
            intent = "CLARIFY"
        elif "CHAT" in intent:
            intent = "CHAT"
        else:
            intent = "RAG"

        log.info("✅ 意图分类结果: %s", intent)

        if intent == "CHAT":
            try:
                response_chat = client.chat.completions.create(
                    model=DEEPSEEK_MODEL,
                    messages=[
                        {"role": "system", "content": "你是一个银行智能化办公助理 Copilot。请以友好、专业、简短的方式回复用户的打招呼或闲聊问候（通常不超过两句话）。"},
                        {"role": "user", "content": question},
                    ],
                    max_tokens=150,
                    temperature=0.7,
                )
                reply = response_chat.choices[0].message.content.strip()
                log.info("💬 闲聊回复生成成功")
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {"intent": "CHAT", "reply": reply, "method": "LLM"}
                })
            except Exception as chat_err:
                log.error("❌ 闲聊回复生成失败: %s", chat_err)
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "intent": "CHAT",
                        "reply": "你好！我是你的智能助理 Copilot，随时可以帮您处理 SQL 查询、会议室预订、日程冲突检测或知识库检索。请问今天有什么我可以帮您的？",
                        "method": "LLM_FALLBACK"
                    }
                })

        if intent == "CLARIFY":
            try:
                response_clarify = client.chat.completions.create(
                    model=DEEPSEEK_MODEL,
                    messages=[
                        {"role": "system", "content": "你是一个会议室预订与地图服务助手。用户想使用服务但缺少核心参数。请用友好、专业、简短的一句话询问用户缺少的参数（例如询问具体时间、人数，或询问起点和终点）。"},
                        {"role": "user", "content": question},
                    ],
                    max_tokens=150,
                    temperature=0.7,
                )
                reply = response_clarify.choices[0].message.content.strip()
                log.info("💬 澄清回复生成成功")
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {"intent": "CLARIFY", "reply": reply, "method": "LLM"}
                })
            except Exception as clarify_err:
                log.error("❌ 澄清回复生成失败: %s", clarify_err)
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "intent": "CLARIFY",
                        "reply": "好的，请问您需要预定哪一天的会议室、大概多少人？或者您能提供路线规划的起点和终点吗？",
                        "method": "LLM_FALLBACK"
                    }
                })

        return jsonify({
            "code": 200,
            "message": "success",
            "data": {"intent": intent, "method": "LLM"}
        })

    except Exception as e:
        log.error("❌ 意图分类失败: %s", e)
        intent = "RAG"
        if any(w in q_lower for w in ["select", "show", "table", "查询", "统计", "余额", "账单", "交易", "账户"]):
            intent = "CODE"
        elif any(w in q_lower for w in ["会议室", "预订", "日程", "时间冲突", "发邮件", "安排"]):
            # Check if booking details are missing
            has_date = any(w in q_lower for w in ["今", "明", "后", "下午", "上午", "点", "分", "time", "date", "today", "tomorrow", "pm", "am"])
            has_capacity = any(w in q_lower for w in ["人", "个", "位", "pax", "capacity", "people"])
            if not (has_date and has_capacity):
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "intent": "CLARIFY",
                        "reply": "好的，请问您需要预定哪一天的会议室？预计有多少人参加？",
                        "method": "FALLBACK_CLARIFY"
                    }
                })
            intent = "TOOL"
        elif any(w in q_lower for w in ["规划", "路线", "地图", "驾车"]):
            if "从" not in q_lower or "到" not in q_lower:
                return jsonify({
                    "code": 200,
                    "message": "success",
                    "data": {
                        "intent": "CLARIFY",
                        "reply": "请提供您的路线起点和终点（例如：从香港大学到香港国际机场）。",
                        "method": "FALLBACK_CLARIFY"
                    }
                })
            intent = "TOOL"
        elif any(w in q_lower for w in ["设置", "settings", "个人中心", "密码", "password", "profile"]):
            intent = "SETTINGS"
        return jsonify({
            "code": 200,
            "message": "success",
            "data": {"intent": intent, "method": "FALLBACK", "error": str(e)}
        })


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
