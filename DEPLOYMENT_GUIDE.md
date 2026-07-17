# BankAgent -- Local Deployment Guide

This guide explains how to deploy and run the BankAgent platform on a local machine (macOS or Windows) for capstone evaluation. All API keys are pre-configured in `.env` -- no external account registration is needed.

---

## Prerequisites

| Software | Version | Required For |
|----------|---------|-------------|
| Docker Desktop | Latest stable | Runs all services in containers |
| Node.js + npm | 18.16+ | Frontend build (`npm run build`) |

Hardware: 16 GB RAM recommended. The full stack runs 15 containers including Milvus vector database, multiple Java services, and a Python embedding worker.

Make sure Docker Desktop is running before proceeding.

---

## Step 1: Build the Frontend

The Nginx container serves pre-built static files from `web-ui/dist/`. This must be built before starting the platform:

**macOS / Linux:**
```bash
cd web-ui
npm install
npm run build
cd ..
```

**Windows (Command Prompt or PowerShell):**
```cmd
cd web-ui
npm install
npm run build
cd ..
```

---

## Step 2: Start the Platform

From the project root directory:

```bash
docker compose up --build -d
```

This will pull base images, build the Java service containers from source, and download the BGE-M3 embedding model. The first startup takes approximately **15-30 minutes** depending on network speed.

To watch progress:

```bash
docker compose logs -f
```

---

## Step 3: Verify the Deployment

Wait until all containers are running, then check status:

```bash
docker compose ps
```

All containers should show `healthy` or `Up`:

| Container | Purpose | Port |
|-----------|---------|------|
| agent_nginx | Frontend static files + reverse proxy | 80 |
| agent_certbot | SSL certificate auto-renewal (production only) | -- |
| agent_gateway | API gateway with JWT auth and rate limiting | 8080 |
| agent_user_service | Authentication, RBAC, AI provider configuration | 8081 |
| agent_task_service | Asynchronous task execution with WebSocket | 8082 |
| agent_tool_agent | Meeting rooms, schedules, route planning via LLM | 8083 |
| agent_code_agent | Text-to-SQL generation with whitelist validation | 8084 |
| agent_code_agent_python | Python LLM inference server | 8090 |
| agent_rag_agent | RAG knowledge base Q&A | 8085 |
| agent_rag_worker | BGE-M3 embedding model worker | 8091 |
| agent_mysql | Relational database | 3306 |
| agent_redis | Cache, sessions, rate limiting | 6379 |
| agent_minio | Object storage for documents | 9000 |
| agent_milvus | Vector database for RAG embeddings | 19530 |
| agent_etcd | Milvus metadata coordination | 2379 |

---

## Step 4: Open the Platform

Visit **http://localhost** in a browser.

---

## Test Accounts

| Account | Password | Role |
|---------|----------|------|
| `admin` | `123456` | System Administrator |
| `credit_mgr` | `123456` | Credit Department Manager |
| `credit_staff` | `123456` | Credit Department Staff |
| `compliance_mgr` | `123456` | Compliance Department Manager |
| `compliance_staff` | `123456` | Compliance Department Staff |

---

## Exploring the Platform

After logging in as `admin`, the sidebar provides access to:

- **Dashboard** -- Unified overview with Copilot AI assistant for intent routing
- **Tool Agent** -- Meeting room booking, schedule management, route planning (AMap)
- **SQL Agent** -- Natural language to SQL generation with sandboxed execution
- **RAG Agent** -- Knowledge base Q&A with permission-aware document retrieval
- **Documents** -- Upload and manage documents (PDF, Word, PPT) for the knowledge base
- **My Tasks** -- Asynchronous task history with issue reporting
- **Notifications** -- System notifications and HITL approval workflows
- **Organization** -- Department structure and user management
- **Resource Management** -- Meeting room administration
- **Settings** -- AI provider configuration (supports DeepSeek, Xunfei, Ollama)

### Testing the RAG Pipeline

1. Go to **Documents** and upload a PDF, Word, or PPT file
2. After upload, open the document's **RAG Info** panel and click **Reindex**
3. Wait for parsing, chunking, and vectorization to complete
4. Go to **RAG Agent** and ask questions about the uploaded document

### Testing the SQL Agent

1. Go to **SQL Agent**
2. Ask a natural language question, e.g. "Show all customers from Credit Department"
3. The system generates SQL, validates it against the whitelist, and executes it

### Testing the Tool Agent

1. Go to **Tool Agent**
2. Try queries like "Book a meeting room for 5 people tomorrow morning"
3. The AI agent parses your intent and interacts with the database

---

## Managing the Platform

```bash
# View logs for a specific service
docker compose logs -f gateway-service
docker compose logs -f rag-agent

# Restart a single service
docker compose restart user-service

# Stop all services (data preserved in Docker volumes)
docker compose down

# Stop and remove all data (fresh start)
docker compose down -v

# Restart after stopping
docker compose up -d
```

---

## Troubleshooting

### Port conflicts

If ports 80, 3306, 6379, 9000, or 19530 are already in use:

**macOS (Homebrew):**
```bash
brew services stop mysql redis
```

**Windows:**
```cmd
net stop MySQL80
net stop Redis
```

Or change the port mappings in `.env` and `docker-compose.yml`.

### Milvus fails to start

Milvus depends on etcd and MinIO being healthy first. Restart in order:
```bash
docker compose restart etcd minio
sleep 10
docker compose restart milvus
```

### RAG Worker model download fails

If Hugging Face is inaccessible, the embedding worker will fail. Edit `.env` to switch to the Qwen Cloud API or mock mode:
```env
RAG_EMBEDDING_ACTIVE_PROFILE=qwen-v4   # Qwen DashScope cloud API (requires API key)
# or
RAG_EMBEDDING_ACTIVE_PROFILE=mock      # Random vectors (functional but low-quality retrieval)
```
Then restart: `docker compose up -d`

### Not enough RAM

If your machine has less than 16 GB RAM, reduce the footprint by commenting out `rag-worker` and `milvus` services in `docker-compose.yml`, and set `RAG_EMBEDDING_ACTIVE_PROFILE=mock` in `.env`. The rest of the platform will run without vector search capabilities.
