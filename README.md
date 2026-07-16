# BankAgent -- Intelligent Agent Platform

This is an intelligent multi-agent platform for enterprise banking scenarios. It features a microservices architecture with Spring Boot backend services, Python-based AI inference workers, and a Vue 3 single-page frontend. The platform supports tool-based AI agents, RAG (Retrieval-Augmented Generation) knowledge-base Q&A, text-to-SQL code generation, and collaborative task execution with human-in-the-loop approval workflows.

---

## Live Deployment

A live deployment is available at: **https://bankagent.online**

This instance is hosted on a DigitalOcean droplet (2 vCPU / 8 GB RAM) provided through the GitHub Student Developer Pack. Due to limited resources, the server struggles to run all services (MySQL, Redis, MinIO, Milvus, etcd, plus 5 Java microservices, 2 Python workers, and Nginx) simultaneously. Performance degradation and occasional service unavailability may occur.

**Local deployment is strongly recommended for a smooth experience.** See the Quick Start section below.

> Note: This server is provisioned through the GitHub Student Pack and will expire on **31 July 2026**. The domain and deployment will become unavailable after that date.

---

## Prerequisites

The recommended way to run the system is via Docker Compose, which handles all infrastructure dependencies automatically.

| Dependency | Version | Purpose |
|------------|---------|---------|
| Docker Desktop | Latest stable | Container runtime for all services |
| Docker Compose | v2+ | Multi-container orchestration |
| Node.js | 18.16+ (LTS) | Frontend build (for `npm run build`) |
| npm | 9.5+ | Frontend package management |

For local development (running services outside Docker), additional dependencies are:

| Dependency | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Spring Boot 3.x runtime |
| Maven | 3.9+ | Backend build |
| Python | 3.10+ | RAG Embedding Worker |

---

## Quick Start (Docker Deployment)

This is the simplest way to run the entire platform. All infrastructure (MySQL, Redis, MinIO, Milvus, etcd) and backend services are started in Docker containers.

### Step 1: Configure environment

```bash
cp .env.example .env
```

Edit `.env` and fill in the required API keys (DeepSeek, Resend, AMap). See the comments in `.env.example` for details on each variable.

### Step 2: Build the frontend

```bash
cd web-ui
npm install
npm run build
cd ..
```

This produces the static assets in `web-ui/dist/` which are served by Nginx.

### Step 3: Start all services

```bash
docker compose up --build -d
```

The first startup will pull images, build service containers, and download the BGE-M3 embedding model from Hugging Face (approximately 2 GB). This may take 10-20 minutes depending on network speed.

### Step 4: Access the platform

Open http://localhost in a browser.

**Default test accounts** (password: `123456`):

| Account | Role | Department | Clearance |
|---------|------|------------|-----------|
| `admin` | Global Administrator | -- | Level 3 (Confidential) |
| `credit_mgr` | Department Manager | Credit Department | Level 2 (Internal) |
| `credit_staff` | Employee | Credit Department | Level 1 (Public) |
| `compliance_mgr` | Department Manager | Compliance Department | Level 2 (Internal) |
| `compliance_staff` | Employee | Compliance Department | Level 1 (Public) |

---

## Project Structure

```
BankAgent/
├── gateway-service/              # API Gateway (port 8080)
│   ├── src/main/java/com/agent/gateway/
│   │   ├── config/SecurityConfig.java       # CORS, security whitelist, route auth
│   │   ├── filter/JwtAuthGlobalFilter.java  # JWT verification and rate limiting
│   │   └── GatewayServiceApplication.java   # Gateway entry point
│   └── src/main/resources/application.yml   # Route definitions and forwarding rules
│
├── user-service/                 # User Center (port 8081)
│   ├── src/main/java/com/agent/user/
│   │   ├── controller/           # Login, Department, Document, Notification, AI Provider
│   │   ├── service/impl/         # User, Department, Document, Notification services
│   │   ├── mapper/               # MyBatis-Plus data access layer
│   │   ├── utils/                # JWT utility, API key encryption
│   │   └── config/               # Security configuration
│   └── pom.xml
│
├── task-service/                 # Task Execution Center (port 8082)
│   ├── src/main/java/com/agent/task/
│   │   ├── controller/           # Task submission and collaborative audit APIs
│   │   ├── service/impl/         # Task state machine and workflow processing
│   │   └── handler/              # WebSocket session management
│   └── pom.xml
│
├── tool-agent/                   # Tool Agent (port 8083)
│   ├── src/main/java/com/agent/tool/
│   │   ├── controller/           # Meeting room, schedule, route planning APIs
│   │   ├── service/              # Tool orchestration and LLM integration
│   │   └── config/               # WebSocket configuration
│   └── pom.xml
│
├── code-agent/                   # Code/SQL Agent (port 8084)
│   ├── src/main/java/com/agent/code/   # Java: SQL whitelist validation and audit
│   └── data/                           # Python: text-to-SQL inference server (port 8090)
│
├── rag-agent/                    # RAG Knowledge Base Agent (port 8085)
│   ├── src/main/java/com/agent/rag/
│   │   ├── controller/           # RAG Q&A, indexing, knowledge base, access request APIs
│   │   ├── service/impl/         # Document parsing, chunking, embedding, Milvus retrieval
│   │   └── mapper/               # RAG tables, user, document, notification mappers
│   └── pom.xml
│
├── rag-worker/                   # Local Embedding Worker (port 8091)
│   ├── app.py                    # FastAPI embedding service (BGE-M3)
│   ├── requirements.txt          # sentence-transformers, torch, fastapi
│   └── Dockerfile
│
├── web-ui/                       # Vue 3 Frontend (port 3000 in dev)
│   ├── src/
│   │   ├── views/
│   │   │   ├── tool/index.vue          # Tool Agent (meetings/schedules/routes)
│   │   │   ├── code/index.vue          # SQL Agent (text-to-SQL with sandbox)
│   │   │   ├── rag/index.vue           # RAG Agent (vector retrieval and Q&A)
│   │   │   ├── document/index.vue      # Knowledge Assets Library
│   │   │   ├── admin/MyDepartment.vue  # Organization management
│   │   │   ├── admin/UserManagement.vue # User administration
│   │   │   ├── admin/ResourceManagement.vue # Meeting room management
│   │   │   ├── task/MyTasks.vue        # Task center
│   │   │   ├── notification/index.vue  # Notification center with approvals
│   │   │   ├── dashboard/index.vue     # Unified dashboard
│   │   │   ├── settings/index.vue      # User settings and AI provider config
│   │   │   └── login/index.vue         # Login and registration
│   │   ├── api/                  # Axios request wrappers
│   │   ├── components/           # Shared UI components
│   │   ├── locales/              # i18n (English, Chinese)
│   │   └── layouts/              # Page layout components
│   ├── vite.config.js            # Vite proxy and build configuration
│   └── package.json
│
├── docker/                       # Docker configuration
│   ├── init/                     # Database initialization SQL scripts
│   │   ├── agent_platform_backup_utf8.sql  # Schema and seed data
│   │   └── patch_rag_tables.sql            # RAG table migrations
│   ├── nginx/nginx.conf          # Nginx reverse proxy configuration
│   └── config/                   # Additional configuration files
│
├── scripts/                      # Utility scripts
│   ├── start-rag-worker.sh       # Start RAG embedding worker (Linux/macOS)
│   ├── start-rag-worker.ps1      # Start RAG embedding worker (Windows)
│   ├── test-rag-embedding.ps1    # Verify embedding service
│   ├── test-rag-agent.ps1        # Verify RAG agent retrieval pipeline
│   └── test-qwen-embedding.ps1   # Verify Qwen cloud embedding API
│
├── docker-compose.yml            # Full Docker Compose configuration
├── docker-compose.prod.yml       # Production overrides (pre-built JARs)
├── pom.xml                       # Parent Maven POM
├── README.md                     # This file
└── README_DOCKER.md              # Detailed Docker deployment guide
```

---

## Architecture

### System Diagram

```
Browser (HTTP :80)
  --> Nginx (static file hosting + reverse proxy)
    --> Gateway Service (:8080) -- JWT auth, route forwarding
      --> /api/user/**   --> User Service (:8081)
      --> /api/task/**   --> Task Service (:8082)
      --> /api/tool/**   --> Tool Agent (:8083)
      --> /api/code/**   --> Code Agent (:8084)
      --> /api/rag/**    --> RAG Agent (:8085)
      --> /ws/*          --> WebSocket routing

Infrastructure:
  MySQL (:3306)     -- Relational database
  Redis (:6379)     -- Cache, rate limiting, session management
  MinIO (:9000)     -- Object storage (documents, files)
  Milvus (:19530)   -- Vector database (RAG embeddings)
  etcd (:2379)      -- Milvus metadata store
  Python Inference Server (:8090)  -- Text-to-SQL model
  RAG Embedding Worker (:8091)     -- BGE-M3 vector embeddings
```

### Key Features

- **Unified API Gateway**: All external requests pass through the Gateway with JWT authentication and Redis-based rate limiting.
- **RBAC Security**: Hierarchical permissions (Public/Internal/Confidential) with department-scoped document access.
- **Tool Agent**: AI-powered meeting room booking, schedule management, and route planning with LLM orchestration via WebSocket.
- **Code/SQL Agent**: Natural language to SQL generation with whitelist-based SQL validation and sandboxed execution.
- **RAG Agent**: End-to-end retrieval-augmented generation pipeline -- document upload to MinIO, parsing via Apache Tika, chunking, BGE-M3 embedding, Milvus vector search, and permission-aware LLM-grounded answers.
- **Human-in-the-Loop**: Task approvals, SQL execution audits, and RAG access request workflows via a notification system with approve/reject actions.
- **AI Provider Abstraction**: Centralized LLM configuration with support for multiple providers (Xunfei Maas API, DeepSeek official API, Ollama local models).

### Database

The `agent_platform` database (MySQL 8.0, utf8mb4) houses all service data. Key tables include:

| Table | Purpose |
|-------|---------|
| `sys_user` | User accounts with roles, department membership, and security clearance |
| `sys_department` | Organization hierarchy |
| `sys_document` | Knowledge asset documents with MinIO storage |
| `sys_notification` | System notifications and approval transactions |
| `sys_config` | Dynamic system configuration (e.g., AI provider) |
| `rag_knowledge_base` | RAG knowledge base organization |
| `rag_source_document` | RAG document-to-knowledge-base mappings |
| `rag_document_chunk` | Document chunks linked to Milvus vectors |
| `rag_query_log` | RAG query tracing and audit |
| `meeting_room` | Meeting room resources |
| `meeting_schedule` | Booking records |

---

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 17 |
| Spring Boot | 3.1.8 |
| Spring Cloud Gateway | 2022.0.4 |
| MyBatis-Plus | 3.5.5 |
| MySQL | 8.0.33 |
| Redis | 7 (Alpine) |
| MinIO | RELEASE.2024-12-18 |
| Milvus | 2.6.15 |
| Vue | 3.5.31 |
| Vite | 8.0.3 |
| Element Plus | 2.13.6 |
| TypeScript | 5.1.6 |
| Python (RAG Worker) | 3.12 |
| FastAPI | 0.115+ |
| sentence-transformers | 3.0+ |

---

## Managing Services

```bash
# View all running containers
docker compose ps

# View logs for a specific service
docker compose logs -f gateway-service
docker compose logs -f rag-agent

# Stop all services (data preserved)
docker compose down

# Stop all services and reset all data volumes
docker compose down -v

# Restart a single service
docker compose restart user-service
```

---

## RAG Document Processing

1. Log in and navigate to the Knowledge Base / Documents page.
2. Upload a PDF, Word, or PPT document.
3. The raw file is stored in MinIO and the metadata in MySQL.
4. Open the document's RAG Info panel and click Reindex/Reprocess.
5. Wait for parsing, chunking, and vectorization to complete.
6. Navigate to the RAG Agent page to ask questions against the indexed documents.

Detailed documentation:

- [RAG Agent Runbook](docs/RAG_AGENT_RUNBOOK.md)
- [File Upload Storage Design](docs/file-upload-storage-design.md)
- [Docker Deployment Guide](README_DOCKER.md)

---

## Local Development

For development with hot-reload, you can run infrastructure in Docker and services locally:

```bash
# Start only infrastructure
docker compose up -d mysql redis etcd minio milvus

# Start the RAG embedding worker (optional, for real embeddings)
bash scripts/start-rag-worker.sh

# Start backend services in separate terminals
./mvnw -pl gateway-service spring-boot:run
./mvnw -pl user-service spring-boot:run
./mvnw -pl task-service spring-boot:run
./mvnw -pl tool-agent spring-boot:run
./mvnw -pl rag-agent spring-boot:run
./mvnw -f code-agent/pom.xml spring-boot:run

# Start the frontend dev server
cd web-ui && npm run dev
```

The frontend dev server runs at http://localhost:3000 with API requests proxied to the Gateway at port 8080.
