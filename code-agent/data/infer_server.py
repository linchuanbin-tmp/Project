"""
Text-to-SQL Inference Server — Unified LLM Config
==================================================
架构：自然语言 → LLM API（携带完整表 metadata）→ SQL
Java 后端通过 HTTP POST /infer 调用，返回 {"sql": "...", "method": "LLM"}

配置优先级：
  1. user-service /user/config/ai-provider/internal API（统一配置中心）
  2. 环境变量 DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL
  3. ds_config.json 文件
  4. 默认值

环境变量：
  USER_SERVICE_URL   user-service 地址（默认 http://user-service:8081）
  DEEPSEEK_API_KEY    LLM API Key（兜底）
  DEEPSEEK_BASE_URL   LLM API 地址（兜底）
  DEEPSEEK_MODEL      LLM 模型名（兜底）
  DB_HOST / DB_USER / DB_PASSWORD / DB_NAME  MySQL 连接信息
"""

import os
import json
import logging
import urllib.request
from flask import Flask, request, jsonify

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

USER_SERVICE_URL = os.environ.get("USER_SERVICE_URL", "http://user-service:8081")

# ============================================================
# 配置（统一配置中心 > 环境变量 > ds_config.json > 默认值）
# ============================================================
def _load_config():
    """加载 LLM 配置，优先从 user-service 统一配置中心获取"""
    config = {
        "api_key": "",
        "base_url": "",
        "model": "",
    }

    # 1. 优先从 user-service 统一配置中心获取
    try:
        url = f"{USER_SERVICE_URL}/user/config/ai-provider/internal"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            if data.get("code") == 200 and data.get("data"):
                cfg = data["data"]
                if cfg.get("apiKey"):
                    config["api_key"] = cfg["apiKey"]
                if cfg.get("baseUrl"):
                    config["base_url"] = cfg["baseUrl"]
                if cfg.get("model"):
                    config["model"] = cfg["model"]
                log.info("✅ 从 user-service 统一配置中心加载 LLM 配置")
    except Exception as e:
        log.warning("⚠️ 无法从 user-service 获取配置: %s，使用环境变量兜底", e)

    # 2. 环境变量兜底
    if not config["api_key"]:
        config["api_key"] = os.environ.get("DEEPSEEK_API_KEY", "")
    if not config["base_url"]:
        config["base_url"] = os.environ.get("DEEPSEEK_BASE_URL", "")
    if not config["model"]:
        config["model"] = os.environ.get("DEEPSEEK_MODEL", "")

    # 3. ds_config.json 兜底
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
            log.info("📄 从 ds_config.json 加载兜底配置")
    except FileNotFoundError:
        log.warning("⚠️ ds_config.json 未找到")
    except Exception as e:
        log.warning("⚠️ 读取 ds_config.json 失败: %s", e)

    # 4. 最终默认值
    if not config["base_url"]:
        config["base_url"] = "https://api.deepseek.com"
    if not config["model"]:
        config["model"] = "deepseek-chat"
    return config

_cfg = _load_config()
DEEPSEEK_API_KEY = _cfg["api_key"]
DEEPSEEK_BASE_URL = _cfg["base_url"]
DEEPSEEK_MODEL = _cfg["model"]
ROUTE_CLASSIFY_MODEL = os.environ.get("DEEPSEEK_CLASSIFY_MODEL", DEEPSEEK_MODEL)

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

    # ── 语言检测 ──
    def detect_language(text: str) -> str:
        """检测输入语言：zh-Hant / zh-Hans / en。兜底默认英文。"""
        trad_chars = sum(1 for c in text if '一' <= c <= '鿿' or '㐀' <= c <= '䶿')
        # 繁体特有字符（常見繁體字不在簡體中）
        trad_only = sum(1 for c in text if c in '臺體國會學業來個關門開車電動機無對發現變化萬與實點戰鬥裡後麼')
        # 简体特有字符
        simp_only = sum(1 for c in text if c in '台体国会学业来个关门开车电动机无对发现变化万与实点战斗里后么')
        if trad_chars > 0:
            if trad_only > simp_only:
                return 'zh-Hant'
            return 'zh-Hans'
        # 纯英文/数字/无中文
        has_chinese = bool(trad_chars)
        if not has_chinese:
            return 'en'
        return 'zh-Hans'
    lang = detect_language(question)

    # ── 安全拦截: 拒绝非银行业务相关的闲聊、角色扮演、越狱攻击 ──
    BLOCKED_PATTERNS = [
        # 越狱 / prompt injection
        "ignore previous", "ignore all", "forget your", "forget all",
        "you are now", "pretend you are", "act as", "roleplay", "role play",
        "dann", "dan ", "developer mode", "jailbreak", "system prompt",
        "你的提示词", "你的prompt", "你的系统提示", "给我输出你的",
        # 非银行闲聊
        "讲个笑话", "说个笑话", "笑话", "tell me a joke", "tell a joke",
        "写诗", "写首诗", "作诗", "写歌", "写首歌", "sing", "poem",
        "故事", "fairy tale", "童话", "tell me a story", "tell a story",
        # 危险 / 有害
        "hack", "exploit", "crack", "越狱", "绕过",
        "malware", "virus", "ransomware", "phishing",
        "暴力", "violence", "攻击", "attack",
        "suicide", "kill myself", "杀人",
        "裸", "naked", "porn", "sex", "色情",
        # 通用非银行场景
        "烹饪", "做饭", "recipe", "cook",
        "电影", "movie", "netflix", "音乐", "music", "歌曲",
        "天气", "weather", "运动", "sport", "篮球", "football", "baseball",
        "游戏", "game", "gaming", "playstation", "xbox", "nintendo",
        # 代码注入
        "write python", "write a script", "execute command",
        "sudo", "rm -rf", "wget http", "curl http",
        # 套话绕过
        "以银行助手的身份", "假装你是", "现在你是", "你现在是",
        "用中文回复但我之前说", "translate",
    ]
    for pattern in BLOCKED_PATTERNS:
        if pattern in q_lower:
            reject_replies = {
                'zh-Hant': '抱歉，我是銀行業務助手，無法回答此類問題。如有銀行業務相關需求，請隨時告訴我。',
                'zh-Hans': '抱歉，我是银行业务助手，无法回答此类问题。如有银行业务相关需求，请随时告诉我。',
                'en': "I'm sorry, I'm a banking assistant and cannot help with that. Please let me know if you have any banking-related inquiries."
            }
            return jsonify({"code": 200, "message": "success",
                "data": {"intent": "CHAT", "reply": reject_replies.get(lang, reject_replies['en']), "method": "SAFETY_BLOCK"}})

    # Check greetings first for keyword fallback
    if any(w in q_lower for w in ["你好", "您好", "hello", "hi", "hey", "who are you", "你是谁", "是谁", "助手", "小助手", "copilot", "agent"]):
        greeting_replies = {
            'zh-Hant': '你好！我是你的 BankAgent Copilot，隨時可以幫您處理 SQL 查詢、會議室預訂、日程衝突檢測或知識庫檢索。請問今天有什麼我可以幫您的？',
            'zh-Hans': '你好！我是你的 BankAgent Copilot，随时可以帮您处理 SQL 查询、会议室预订、日程冲突检测或知识库检索。请问今天有什么我可以帮您的？',
            'en': "Hello! I'm your BankAgent Copilot, ready to help with SQL queries, meeting room booking, schedule conflict detection, or knowledge base search. What can I assist you with today?"
        }
        return jsonify({
            "code": 200,
            "message": "success",
            "data": {
                "intent": "CHAT",
                "reply": greeting_replies.get(lang, greeting_replies['en']),
                "method": "KEYWORD"
            }
        })

    if not DEEPSEEK_API_KEY:
        log.warning("⚠️ API Key not set, fallback to simple keyword routing")
        intent = "RAG"
        if any(w in q_lower for w in ["select", "show", "table", "查询", "统计", "余额", "账单", "交易", "账户"]):
            intent = "CODE"
        elif any(w in q_lower for w in ["会议室", "预订", "日程", "时间冲突", "发邮件", "安排"]):
            # Booking intent detected — check for route keywords
            has_route = any(w in q_lower for w in ["从", "到", "路线", "地图", "规划路线", "驾车", "公交", "步行", "起点", "终点"])
            if not has_route:
                # It's a booking request — check if time and capacity are present
                has_date = any(w in q_lower for w in ["今", "明", "后", "下午", "上午", "点", "分", "time", "date", "today", "tomorrow", "pm", "am"])
                has_capacity = any(w in q_lower for w in ["人", "个", "位", "pax", "capacity", "people"])
                if not (has_date and has_capacity):
                    clarify = {'zh-Hant': '好的，請問您需要預定哪一天的會議室？預計有多少人參加？',
                               'zh-Hans': '好的，请问您需要预定哪一天的会议室？预计有多少人参加？',
                               'en': 'Sure, which date and time would you like to book the meeting room for? And how many attendees?'}
                    return jsonify({
                        "code": 200, "message": "success",
                        "data": {"intent": "CLARIFY", "reply": clarify.get(lang, clarify['en']), "method": "KEYWORD_CLARIFY"}
                    })
            intent = "TOOL"
        elif any(w in q_lower for w in ["规划", "路线", "地图", "驾车", "公交", "步行"]):
            # Route planning intent
            if "从" not in q_lower or "到" not in q_lower:
                route_clarify = {'zh-Hant': '請提供您的路線起點和終點（例如：從香港大學到香港國際機場）。',
                                  'zh-Hans': '请提供您的路线起点和终点（例如：从香港大学到香港国际机场）。',
                                  'en': 'Please provide your starting point and destination (e.g., from HKU to Hong Kong International Airport).'}
                return jsonify({
                    "code": 200, "message": "success",
                    "data": {"intent": "CLARIFY", "reply": route_clarify.get(lang, route_clarify['en']), "method": "KEYWORD_CLARIFY"}
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

        system_prompt = """你是智能意图分类器。只输出一个单词：CODE/TOOL/RAG/SETTINGS/CLARIFY/CHAT。
- CODE: 查询数据库、生成SQL、统计数据。
- TOOL: 用户想订会议室（提到时间、人数、会议室等）或做日程冲突检测或做路径规划（提到起点/终点、路线、驾车等）。如果用户说要"预订会议室"或者给了时间和人数，并且没有问到路线相关词（从/到/路线/地图/规划/起点/终点/出行/交通），就判定为TOOL。
- RAG: 问规章制度、文档、概念定义、政策、指南。
- SETTINGS: 改密码、个人资料、系统配置。
- CLARIFY: 用户表达了使用会议室/路线服务的意图（有"预订""会议室""日程""路线""规划""出行""交通""地图"等词），但缺少必要参数。会议室需要时间和人数；路线需要起点和终点。如果信息完整直接判TOOL。
- CHAT: 打招呼、闲聊。如果用户要求讲笑话、写诗、讲故事、角色扮演等非银行办公相关的内容，也归为CHAT（由CHAT安全防护层处理拒绝）。"""

        response = client.chat.completions.create(
            model=ROUTE_CLASSIFY_MODEL,
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
                        {"role": "system", "content": f"你是银行智能化办公助理 BankAgent。请以友好、专业、简短的方式回复用户的打招呼或闲聊问候（通常不超过两句话）。如果用户输入是中文（简体/繁体），用相应的中文回复。如果是英文，用英文回复。用户使用的语言：{lang}。不要讲笑话、不要写诗、不要角色扮演、只回答与银行办公相关的问题。如果用户要求非银行相关的内容，礼貌地拒绝并引导回银行事务。"},
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
                        "reply": "你好！我是你的 BankAgent Copilot，随时可以帮您处理 SQL 查询、会议室预订、日程冲突检测或知识库检索。请问今天有什么我可以帮您的？",
                        "method": "LLM_FALLBACK"
                    }
                })

        if intent == "CLARIFY":
            try:
                response_clarify = client.chat.completions.create(
                    model=DEEPSEEK_MODEL,
                    messages=[
                        {"role": "system", "content": f"你是会议室预订与地图服务助手。用户想使用服务但缺少核心参数。\n- 如果用户表达了预订会议室的意图：只问缺失的参数（日期时间、人数），绝对不要问地点、设备、起点终点。\n- 如果用户表达了路线规划的意图：只问缺失的参数（起点、终点），绝对不要问人数、会议室。\n请根据用户已经提供的信息，只问真正缺失的内容。用友好、简短的一句话询问。用户使用的语言：{lang}，请用对应语言回复。"},
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
        clarify_fb = {'zh-Hant': '好的，請問您需要預定哪一天的會議室？預計有多少人參加？',
                       'zh-Hans': '好的，请问您需要预定哪一天的会议室？预计有多少人参加？',
                       'en': 'Sure, which date and time would you like to book the meeting room for? And how many attendees?'}
        route_clarify_fb = {'zh-Hant': '請提供您的路線起點和終點（例如：從香港大學到香港國際機場）。',
                             'zh-Hans': '请提供您的路线起点和终点（例如：从香港大学到香港国际机场）。',
                             'en': 'Please provide your starting point and destination (e.g., from HKU to Hong Kong International Airport).'}
        if any(w in q_lower for w in ["select", "show", "table", "查询", "统计", "余额", "账单", "交易", "账户"]):
            intent = "CODE"
        elif any(w in q_lower for w in ["会议室", "预订", "日程", "时间冲突", "发邮件", "安排"]):
            has_route = any(w in q_lower for w in ["从", "到", "路线", "地图", "规划路线", "驾车", "公交", "步行", "起点", "终点"])
            if not has_route:
                has_date = any(w in q_lower for w in ["今", "明", "后", "下午", "上午", "点", "分", "time", "date", "today", "tomorrow", "pm", "am"])
                has_capacity = any(w in q_lower for w in ["人", "个", "位", "pax", "capacity", "people"])
                if not (has_date and has_capacity):
                    return jsonify({
                        "code": 200, "message": "success",
                        "data": {"intent": "CLARIFY", "reply": clarify_fb.get(lang, clarify_fb['en']), "method": "FALLBACK_CLARIFY"}
                    })
            intent = "TOOL"
        elif any(w in q_lower for w in ["规划", "路线", "地图", "驾车", "公交", "步行"]):
            if "从" not in q_lower or "到" not in q_lower:
                return jsonify({
                    "code": 200, "message": "success",
                    "data": {"intent": "CLARIFY", "reply": route_clarify_fb.get(lang, route_clarify_fb['en']), "method": "FALLBACK_CLARIFY"}
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
