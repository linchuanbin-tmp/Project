# BankAgent -- Local Deployment Guide

This guide explains how to run the BankAgent platform on your local machine for evaluation. All API keys are pre-configured in `.env`.

## Prerequisites

- **Docker Desktop** installed and running
- **Node.js** (for building the frontend; a pre-built `web-ui/dist/` is also included)
- 16 GB RAM recommended (15 containers including Milvus vector database)

## Quick Start

**1. Build the frontend:**

```bash
cd web-ui
npm install && npm run build
cd ..
```

If you do not have Node.js, skip this step. The repository includes a pre-built `web-ui/dist/` folder.

**2. Start the platform:**

```bash
docker compose up --build -d
```

The first startup takes 15-30 minutes. Docker pulls images, builds Java services from source, and downloads the BGE-M3 embedding model (~2 GB) inside the worker container.

Monitor progress:

```bash
docker compose logs -f
```

**3. Verify all services are healthy:**

```bash
docker compose ps
```

You should see 15 containers, all `healthy` or `Up`:

| Container | Purpose |
|-----------|---------|
| agent_nginx | Reverse proxy (:80) |
| agent_certbot | SSL certificate renewal |
| agent_gateway | API gateway (:8080) |
| agent_user_service | Authentication and RBAC (:8081) |
| agent_task_service | Async task execution (:8082) |
| agent_tool_agent | Tool orchestration (:8083) |
| agent_code_agent | SQL generation (:8084) |
| agent_code_agent_python | Python LLM inference (:8090) |
| agent_rag_agent | RAG knowledge base (:8085) |
| agent_rag_worker | Python embedding worker (:8091) |
| agent_mysql | Primary database (:3306) |
| agent_redis | Cache and sessions (:6379) |
| agent_minio | Object storage (:9000) |
| agent_milvus | Vector database (:19530) |
| agent_etcd | Milvus metadata (:2379) |

**4. Open the platform:**

Visit `http://localhost` in a browser.

## Test Accounts

| Account | Password | Role |
|---------|----------|------|
| `admin` | `123456` | System Administrator |
| `credit_mgr` | `123456` | Credit Department Manager |
| `credit_staff` | `123456` | Credit Department Staff |
| `compliance_mgr` | `123456` | Compliance Department Manager |
| `compliance_staff` | `123456` | Compliance Department Staff |

## Managing the Platform

```bash
# View logs
docker compose logs -f [service-name]

# Restart a service
docker compose restart [service-name]

# Stop all services (data preserved)
docker compose down

# Stop and remove all data
docker compose down -v

# Restart
docker compose up -d
```

## Troubleshooting

**Port conflicts.** If ports 80, 3306, 6379, 9000, or 19530 are in use, stop the conflicting services or change ports in `.env`.

**Milvus fails to start.** It depends on etcd and MinIO being healthy first:

```bash
docker compose restart etcd minio
sleep 10
docker compose restart milvus
```

**RAG Worker model download fails.** Set `RAG_EMBEDDING_PROVIDER=mock` in `.env` and restart. Real embeddings are recommended for evaluation.

**Not enough RAM.** Comment out `rag-worker` and `rag-agent` services in `docker-compose.yml` and the rest of the platform will run without them.
