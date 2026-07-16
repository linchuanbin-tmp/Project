# BankAgent -- Deployment Guide for Capstone Evaluation

This guide explains how to deploy and run the BankAgent platform on a local machine (macOS or Windows). All API keys are pre-configured -- no external account registration is needed.

---

## Prerequisites

You need **Docker Desktop** installed. The entire platform runs inside Docker containers, so no other dependencies (Java, Maven, Python, Node.js, MySQL) need to be installed manually.

| Software | Download Link |
|----------|--------------|
| Docker Desktop | https://www.docker.com/products/docker-desktop/ |

After installation, start Docker Desktop and wait for the engine to be ready.

**Hardware requirements**: at least 16 GB RAM recommended. The full stack runs 10+ containers including Milvus vector database, multiple Java services, and a Python embedding worker.

---

## Step 1: Prepare Environment Configuration

Open a terminal (Terminal on macOS, Command Prompt or PowerShell on Windows) in the project root directory. Copy the environment template to `.env`:

```bash
cp .env.example .env
```

All API keys are already filled in. No editing is needed unless you want to change default passwords or ports.

---

## Step 2: Build the Frontend

The frontend needs to be built once before the first run. This requires Node.js:

```bash
cd web-ui
npm install
npm run build
cd ..
```

The build output goes to `web-ui/dist/`, which is served directly by Nginx inside Docker.

> If you do not have Node.js installed, the frontend dist is already included in the repository (`web-ui/dist/`). You may skip this step, but the bundled dist may be out of date.

---

## Step 3: Start the Platform

```bash
docker compose up --build -d
```

This command will:
1. Pull required Docker images (MySQL, Redis, MinIO, Milvus, etcd, Nginx)
2. Build the Java service containers from source (takes 5-10 minutes the first time)
3. Build the Python embedding worker container (downloads the BGE-M3 model from Hugging Face, ~2 GB)

**The first startup takes approximately 15-30 minutes** depending on your network speed, mostly for downloading Docker images and the BGE-M3 embedding model.

To monitor progress:

```bash
docker compose logs -f
```

---

## Step 4: Verify the Deployment

Check that all containers are healthy:

```bash
docker compose ps
```

You should see all services with `healthy` or `Up` status:

| Container | Purpose | Expected Status |
|-----------|---------|-----------------|
| agent_mysql | MySQL database | healthy |
| agent_redis | Redis cache | healthy |
| agent_etcd | Milvus metadata | healthy |
| agent_minio | Object storage | healthy |
| agent_milvus | Vector database | healthy (may take 1-2 min) |
| agent_rag_worker | Embedding model | healthy (may take 2-3 min on first run) |
| agent_user_service | User/auth service | Up |
| agent_task_service | Task execution | Up |
| agent_tool_agent | Tool orchestration | Up |
| agent_code_agent | SQL generation | Up |
| agent_code_agent_python | Python inference | Up |
| agent_rag_agent | RAG knowledge base | Up |
| agent_gateway | API gateway | Up |
| agent_nginx | Reverse proxy | Up |
| agent_certbot | SSL renewal | Up |

Once all services are up, open a browser and visit:

```
http://localhost
```

### Test Accounts

| Account | Password | Role |
|---------|----------|------|
| `admin` | `123456` | Global Administrator |
| `credit_mgr` | `123456` | Credit Department Manager |
| `credit_staff` | `123456` | Credit Department Employee |
| `compliance_mgr` | `123456` | Compliance Department Manager |
| `compliance_staff` | `123456` | Compliance Department Employee |

---

## Exploring the Platform

After logging in as `admin`, the main navigation provides access to:

- **Dashboard** -- Unified overview and Copilot AI assistant
- **Tool Agent** -- Meeting room booking, schedule management, route planning (Amap)
- **SQL Agent** -- Natural language to SQL generation with sandboxed execution
- **RAG Agent** -- Knowledge base Q&A with document retrieval
- **Documents** -- Upload and manage documents (PDF, Word, PPT) for the knowledge base
- **My Tasks** -- View asynchronous task history and report issues
- **Notifications** -- System notifications and approval workflows
- **Organization** -- Department and user management
- **Resource Management** -- Meeting room administration
- **Settings** -- AI provider configuration and personal settings

### Testing the RAG Pipeline

1. Navigate to **Documents** and upload a PDF, Word, or PPT file
2. After upload, open the document's RAG Info panel and click **Reindex**
3. Wait for parsing, chunking, and vectorization to complete
4. Go to **RAG Agent** and ask questions about the uploaded document

### Testing the SQL Agent

1. Go to **SQL Agent**
2. Ask a natural language question, e.g., "Show all customers from Credit Department"
3. The system generates a SQL query, validates it against the whitelist, and executes it in a sandbox

### Testing the Tool Agent

1. Go to **Tool Agent**
2. Try queries like "Book a meeting room for 5 people tomorrow morning" or "Find available meeting rooms"
3. The AI agent parses your intent and interacts with the meeting room database

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

If ports 80, 3306, 6379, 9000, or 19530 are already in use on your machine, stop the conflicting services first:

**macOS (Homebrew)**:
```bash
brew services stop mysql
brew services stop redis
```

**Windows**:
```cmd
net stop MySQL80
net stop Redis
```

Or modify the port mappings in `.env` (e.g., `MYSQL_EXPOSED_PORT=3307`) and `docker-compose.yml` accordingly.

### Milvus fails to start

Milvus depends on etcd and MinIO being healthy first. If it fails, restart in order:

```bash
docker compose restart etcd minio
sleep 10
docker compose restart milvus
```

### RAG Worker model download fails

If Hugging Face is inaccessible from your network, the embedding worker will fail. You can switch to mock embeddings by editing `.env`:

```env
RAG_EMBEDDING_PROVIDER=mock
```

Then restart: `docker compose up -d`

Note: mock mode produces random vectors, so RAG retrieval quality will be poor. Real embeddings via BGE-M3 are strongly recommended for evaluation.

### Not enough RAM

If your machine has less than 16 GB RAM, you can run a reduced set by disabling some containers. Edit `docker-compose.yml` and comment out the `rag-worker` and `rag-agent` services, then start without them.

---

## Architecture Overview

```
Browser (http://localhost:80)
  -> Nginx (static files + reverse proxy)
    -> Gateway Service (:8080) -- JWT auth, rate limiting
      -> User Service (:8081)     -- Auth, departments, documents
      -> Task Service (:8082)     -- Async task execution
      -> Tool Agent (:8083)       -- Meeting rooms, schedules, routes
      -> Code Agent (:8084)       -- Text-to-SQL generation
      -> RAG Agent (:8085)        -- Knowledge base Q&A

Infrastructure (Docker):
  MySQL (:3306)    -- Primary database
  Redis (:6379)    -- Cache, sessions, rate limiting
  MinIO (:9000)    -- Object storage for documents
  Milvus (:19530)  -- Vector database for RAG embeddings
  etcd (:2379)     -- Milvus metadata coordination

Python Workers:
  Embedding Worker (:8091)  -- BGE-M3 sentence embeddings
  Inference Server (:8090)  -- LLM-powered text-to-SQL
```

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend Framework | Spring Boot 3.1.8 / Spring Cloud Gateway 2022.0.4 |
| ORM | MyBatis-Plus 3.5.5 |
| Database | MySQL 8.0.33 |
| Cache | Redis 7 |
| Object Storage | MinIO |
| Vector Database | Milvus 2.6.15 |
| Frontend | Vue 3.5 + Vite 8 + Element Plus 2.13 |
| Embedding Model | BAAI/bge-m3 (1024-dim) via sentence-transformers |
| LLM Provider | Xunfei Maas API (DeepSeek V3.2) |
| Containerization | Docker Compose |
