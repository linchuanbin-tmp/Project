# SQL Generator (Text-to-SQL) Integration Guide

This document details the architecture, configuration, security features, API endpoints, and deployment options for the **SQL Generator (Text-to-SQL) Agent** microservice.

---

## 1. Architectural Overview

The SQL Generator system enables users to query bank database structures using natural language. It is designed to run asynchronously with a strict **Human-in-the-Loop** verification workflow to prevent unauthorized execution of dangerous operations.

```
                  [ Web Client UI ( VUE ) ]
                             │
            1. Write Prompt  │  2. Preview & Edit (Human-in-the-loop)
                             ▼
                    [ API Gateway (8080) ]
                             │ (JWT verified & routed)
                             ▼
                    [ Code Agent (8084) ]
                   /                     \
        (Generate SQL)                 (Execute SQL)
                 /                         \
                ▼                           ▼
    [ Python Infer Service (8090) ]    [ 5-Layer Whitelist Check ]
                │                                   │ (Passes validation)
                ▼                                   ▼
          [ DeepSeek API ]                  [ MySQL Database ]
```

### Components and Ports
* **Gateway Service (`gateway-service`)**: Runs on port `8080`. Routes external `/api/code/**` requests to the Code Agent, passing parsed JWT credentials (`X-User-*` headers).
* **Code Agent (`code-agent`)**: Java Spring Boot microservice running on port `8084`. Handles validation, execution, and connects to the Python inference server.
* **Inference Server (`code-agent-python`)**: Python Flask service running on port `8090`. Receives prompts, builds schema context dynamically from MySQL, and calls the DeepSeek LLM to output optimized SQL.
* **Database (`mysql`)**: Houses the simulation schema (e.g. `bank_customer`, `bank_account`, `bank_transaction` etc.).
* **Cache (`redis`)**: Cache storage for metadata schema to accelerate dynamic prompt assemblies.

---

## 2. Human-in-the-Loop (HITL) Workflow

To guarantee database safety, automatic code execution is disabled. The workflow is split into three explicit steps:

1. **Prompt Generation (Step 1)**:
   * The user enters a plain text prompt (e.g. *"Show all customers with medium risk"*).
   * Web client calls `/api/code/generate`. The backend routes this to the Python Inference Server, returning only the generated SQL string.
2. **Review & Manual Modification (Step 2)**:
   * The generated SQL is rendered in a terminal-like text editor in the UI.
   * The user reviews the query and can manually edit the SQL string directly in the text editor (e.g. adding constraints or altering column lists).
3. **Verified Execution (Step 3)**:
   * The user clicks "Run Query" to execute the finalized query.
   * Web client calls `/api/code/execute` with the reviewed SQL string.
   * **Important**: The Code Agent runs the SQL through the **5-Layer Security Whitelist** in the backend before running it on the MySQL instance, ensuring safety even after manual user edits.

---

## 3. 5-Layer SQL Safety Whitelist

Every query sent for execution is processed by the `SqlValidationService` class to ensure read-only safety.

| Layer | Rule | Check / Description |
|:---:|---|---|
| **1** | **Operation Restriction** | Only `SELECT` statements are permitted. Operations starting with `UPDATE`, `INSERT`, `DELETE`, `DROP`, `ALTER`, etc. are rejected. |
| **2** | **Forbidden Keywords** | Scans and blocks injection keywords such as `UNION`, `INTO`, `LOAD_FILE`, `OUTFILE`, `DUMPFILE`, and administrative functions. |
| **3** | **Table Whitelist** | Compares query tables against the active schema whitelist. Only whitelisted bank tables (`bank_customer`, `bank_account`, `bank_transaction`, etc.) are allowed. |
| **4** | **Column Whitelist** | Verifies that all requested columns in the SELECT clause and WHERE constraints actually exist in the table definitions to block hidden field probes. |
| **5** | **Complexity Validation** | Limits the number of joins (maximum 3 tables per query) and conditions (maximum 10 WHERE statements) to prevent Denial of Service (DoS). |

---

## 4. Configuration and Environment Variables

Configurations are parameterised in the Code Agent `application.yml` for seamless Docker Compose orchestration.

### Configuration Properties

```yaml
code-agent:
  onnx:
    enabled: true  # Enables LLM-based SQL generation
    server-url: ${CODE_AGENT_PYTHON_URL:http://localhost:8090/infer}
  whitelist:
    allowed-operations:
      - SELECT
    forbidden-keywords:
      - DROP
      - DELETE
      - INSERT
      - UPDATE
      - ALTER
      - TRUNCATE
      - CREATE
      - EXEC
      - EXECUTE
      - UNION
      - INTO
      - LOAD_FILE
      - OUTFILE
      - DUMPFILE
    max-tables-per-query: 3
    max-conditions: 10
  metadata:
    cache-ttl: 3600
    cache-prefix: "code_agent:metadata:"
```

### Supported Environment Variables

| Env Variable | Default Value | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/agent_platform` | MySQL database connection URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `zzm20030718` | Database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `CODE_AGENT_PYTHON_URL` | `http://localhost:8090/infer` | Flask inference service API endpoint |
| `CODE_AGENT_ONNX_ENABLED` | `true` | Enables Python LLM service calls |
| `DEEPSEEK_API_KEY` | — | DeepSeek API key for the Python service |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | Base endpoint URL for LLM API calls |
| `DEEPSEEK_MODEL` | `deepseek-chat` | LLM model selection identifier |

---

## 5. API Reference

All requests must route through Gateway (`8080`) prefixing `/api` and passing a valid JWT Bearer Token.

### 5.1 Generate SQL Only
Generates SQL code without validation or execution.
* **Endpoint**: `POST /api/code/generate`
* **Request Body**:
  ```json
  {
    "question": "Calculate the average balance per account type"
  }
  ```
* **Response Body**:
  ```json
  {
    "success": true,
    "sql": "SELECT account_type, AVG(balance) FROM bank_account GROUP BY account_type",
    "question": "Calculate the average balance per account type",
    "inferenceMethod": "LLM"
  }
  ```

### 5.2 Execute SQL Directly (HITL)
Executes manually reviewed and edited SQL statements.
* **Endpoint**: `POST /api/code/execute`
* **Request Body**:
  ```json
  {
    "sql": "SELECT account_type, AVG(balance) FROM bank_account GROUP BY account_type"
  }
  ```
* **Response Body**:
  ```json
  {
    "success": true,
    "columns": ["account_type", "AVG(balance)"],
    "rows": [
      { "account_type": "SAVINGS", "AVG(balance)": 15024.50 },
      { "account_type": "CHECKING", "AVG(balance)": 5420.10 }
    ],
    "elapsedMs": 4,
    "rowCount": 2
  }
  ```

### 5.3 Fetch Active Schema Metadata
Retrieves list of active cached tables loaded in memory.
* **Endpoint**: `GET /api/code/metadata`
* **Response Body**:
  ```json
  {
    "tableCount": 3,
    "tableNames": ["bank_customer", "bank_account", "bank_transaction"],
    "source": "Redis/MySQL",
    "timestamp": 1782245921000
  }
  ```

---

## 6. One-Click Docker Setup

### docker-compose.yml configuration

```yaml
  code-agent-python:
    build:
      context: ./code-agent/data
      dockerfile: Dockerfile
    container_name: agent_code_agent_python
    restart: unless-stopped
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - DEEPSEEK_BASE_URL=${DEEPSEEK_BASE_URL}
      - DEEPSEEK_MODEL=${DEEPSEEK_MODEL}
      - DB_HOST=${DB_HOST}
      - DB_PORT=${DB_PORT}
      - DB_USER=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
      - DB_NAME=${DB_NAME}
    ports:
      - "8090:8090"
    depends_on:
      mysql:
        condition: service_healthy

  code-agent:
    build:
      context: .
      dockerfile: code-agent/Dockerfile
    container_name: agent_code_agent
    restart: unless-stopped
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
      - SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - SPRING_DATA_REDIS_HOST=${REDIS_HOST}
      - SPRING_DATA_REDIS_PORT=${REDIS_PORT}
      - CODE_AGENT_PYTHON_URL=http://code-agent-python:8090/infer
      - CODE_AGENT_ONNX_ENABLED=${CODE_AGENT_ONNX_ENABLED:-true}
    ports:
      - "8084:8084"
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      code-agent-python:
        condition: service_started
```
