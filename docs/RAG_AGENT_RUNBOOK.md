# RAG Agent Runbook

This runbook explains how to start, verify, and demo the RAG Agent locally.

## 1. Required Services

Start the infrastructure first:

```powershell
docker compose up -d mysql redis etcd minio milvus
```

Then start these application services with the latest code:

```text
user-service     8081
task-service     8082
tool-agent       8083
rag-agent        8085
gateway-service  8080 or 18080 if 8080 is occupied
web-ui           3000
```

For local development, keep mock providers unless real model credentials are ready:

```text
RAG_EMBEDDING_PROVIDER=mock
RAG_LLM_PROVIDER=mock
MILVUS_HOST=localhost
MILVUS_PORT=19530
```

Health output should show the active model wiring:

```text
embeddingProvider
embeddingDim
embeddingEndpointConfigured
embeddingApiKeyConfigured
embeddingModel
embeddingTimeoutMs
llmProvider
llmApiKeyConfigured
llmTimeoutMs
llmTemperature
llmMaxTokens
milvusCollection
vectorMetric
vectorIndex
```

## 2. Frontend Demo Flow

Login:

```text
admin / 123456
credit_mgr / 123456
credit_staff / 123456
compliance_staff / 123456
```

Check the Documents page:

```text
1. Each document card shows a RAG chunk count badge.
2. Admin and department admins can reindex a document.
3. Admin and department admins can inspect generated chunks.
4. The document reader shows Indexed / Not indexed, chunk count, and last indexed time.
```

Check the RAG Agent page:

```text
1. Ask: loan limits and credit authorization metrics
2. admin should retrieve high-security documents.
3. credit_staff should see blocked documents.
4. Click Request Access on a blocked document.
5. Approve the generated RAG_APPLY notification as credit_mgr or admin.
6. Query again as the requester to verify RAG_APPLY_APPROVED access.
```

## 3. One-Command Verification

Run the script from the project root:

```powershell
.\scripts\test-rag-agent.ps1
```

Use a custom RAG base URL:

```powershell
.\scripts\test-rag-agent.ps1 -RagBaseUrl "http://localhost:8085"
```

Skip full rebuild if the index already exists:

```powershell
.\scripts\test-rag-agent.ps1 -SkipRebuild
```

Create a real RAG_APPLY notification during the test:

```powershell
.\scripts\test-rag-agent.ps1 -SubmitAccessRequest
```

## 4. What The Script Verifies

```text
1. /rag/health returns UP.
2. /rag/index/rebuild can rebuild document chunks.
3. /rag/index/documents/status shows indexed documents.
4. /rag/index/document/{id}/chunks returns chunk metadata.
5. /rag/query works for admin, credit_mgr, credit_staff, and compliance_staff.
6. Lower-permission users get blockedDocumentIds.
7. Optional: /rag/access-request creates or detects a RAG_APPLY request.
```

## 5. Useful Manual Commands

Health:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/rag/health" -Method Get
```

Rebuild:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/rag/index/rebuild" -Method Post
```

Index status:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/rag/index/documents/status" -Method Get
```

Chunk inspection:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/rag/index/document/13/chunks" -Method Get
```

Query as credit_staff:

```powershell
$body = @{
  question = "loan limits and credit authorization metrics"
  topK = 5
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8085/rag/query" `
  -Method Post `
  -Headers @{
    "Content-Type" = "application/json"
    "X-User-Name" = "credit_staff"
    "X-User-Roles" = "ROLE_USER"
  } `
  -Body $body
```

## 6. Expected Result

The RAG Agent is healthy when:

```text
health.status = UP
rebuild.status = SUCCESS
chunkCount > 0
admin query status = SUCCESS
credit_staff query includes blockedDocumentIds
Documents page shows chunk counts
RAG page can submit access requests for blocked documents
```

## 7. Common Problems

Port 8085 already used:

```powershell
Get-NetTCPConnection -LocalPort 8085 | Select-Object LocalAddress,LocalPort,State,OwningProcess
Get-Process -Id <PID>
Stop-Process -Id <PID>
```

Docker is not running:

```text
Start Docker Desktop, then rerun docker compose ps.
```

No chunks returned:

```text
Run /rag/index/rebuild first, then check rag_document_chunk count.
```

No blocked documents:

```text
Use a low-clearance account such as credit_staff and ask about confidential credit rules.
```

## 8. Switching To Real Embedding

The default local setup uses mock embedding so every developer can run the RAG pipeline without model credentials. To switch to a real embedding service, update `.env`:

```text
RAG_EMBEDDING_PROVIDER=http
RAG_EMBEDDING_ENDPOINT=http://localhost:8091/embed
RAG_EMBEDDING_API_KEY=
RAG_EMBEDDING_MODEL=
RAG_EMBEDDING_DIM=768
RAG_EMBEDDING_TIMEOUT_MS=10000
```

If the embedding provider requires authentication, set `RAG_EMBEDDING_API_KEY`. If it expects a model field in the request body, set `RAG_EMBEDDING_MODEL`.

The embedding endpoint can return either a direct embedding:

```json
{
  "embedding": [0.1, 0.2, 0.3]
}
```

or an OpenAI-like response:

```json
{
  "data": [
    {
      "embedding": [0.1, 0.2, 0.3]
    }
  ]
}
```

RAG Agent sends a JSON body containing:

```json
{
  "input": "text to embed",
  "text": "text to embed",
  "model": "optional model from RAG_EMBEDDING_MODEL"
}
```

If `RAG_EMBEDDING_API_KEY` is set, RAG Agent sends it as a Bearer token.

After changing embedding provider or dimension, rebuild the index:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/rag/index/rebuild" -Method Post
```

If the service returns the wrong vector length, RAG Agent will fail clearly:

```text
Embedding dimension mismatch: expected 768, got 1024
```

If `RAG_EMBEDDING_PROVIDER=http` but no endpoint is configured, RAG Agent will fail clearly:

```text
RAG embedding endpoint is not configured.
```

### Qwen Cloud / DashScope text-embedding-v4

For Qwen Cloud / DashScope, use the built-in provider instead of the local
worker:

```text
RAG_EMBEDDING_PROVIDER=qwen
RAG_EMBEDDING_ENDPOINT=
RAG_EMBEDDING_API_KEY=your_dashscope_api_key
RAG_EMBEDDING_MODEL=text-embedding-v4
RAG_EMBEDDING_DIM=1024
RAG_EMBEDDING_TIMEOUT_MS=30000
RAG_MILVUS_COLLECTION=rag_document_chunks_qwen_v4_1024
```

`qwen` and `dashscope` are equivalent provider names. If
`RAG_EMBEDDING_ENDPOINT` is empty, RAG Agent uses the default DashScope text
embedding endpoint.

`text-embedding-v4` supports custom dimensions. Start with `1024` so it matches
the local BGE-M3 setup. If you change the dimension, use a new Milvus collection
name or rebuild the existing collection before indexing documents again.

Verify the Qwen embedding API before starting RAG Agent:

```powershell
$env:RAG_EMBEDDING_API_KEY="your_dashscope_api_key"
.\scripts\test-qwen-embedding.ps1
```

### Local BGE-M3 Worker

For local development without a third-party embedding API, run the Python worker in `rag-worker`.

Install dependencies in the project virtual environment:

```powershell
cd "D:\github\Intelligent Multi-Agent System for Enterprise Operations\Project"
.\.venv-rag\Scripts\Activate.ps1
$env:HF_DOWNLOAD="1"
python -m pip install -r rag-worker\requirements.txt
```

Start the worker:

```powershell
python rag-worker\app.py
```

The first startup downloads `BAAI/bge-m3` and caches it under the Hugging Face cache. BGE-M3 returns 1024-dimensional vectors.

Notes:

- Keep the worker terminal open while using RAG.
- `GET /health` does not load the model. The model loads lazily on the first `/embed` request.
- `HF_TOKEN` is optional. Set it only if Hugging Face rate limits or slows downloads.
- Windows symlink cache warnings from `huggingface_hub` are harmless; caching still works, but may use more disk space.

Use this local Java configuration:

```text
RAG_EMBEDDING_PROVIDER=http
RAG_EMBEDDING_ENDPOINT=http://localhost:8091/embed
RAG_EMBEDDING_API_KEY=
RAG_EMBEDDING_MODEL=BAAI/bge-m3
RAG_EMBEDDING_DIM=1024
RAG_EMBEDDING_TIMEOUT_MS=30000
RAG_MILVUS_COLLECTION=rag_document_chunks_bge_m3
```

Use this Docker Compose configuration:

```text
RAG_EMBEDDING_PROVIDER=http
RAG_EMBEDDING_ENDPOINT=http://rag-worker:8091/embed
RAG_EMBEDDING_API_KEY=
RAG_EMBEDDING_MODEL=BAAI/bge-m3
RAG_EMBEDDING_DIM=1024
RAG_EMBEDDING_TIMEOUT_MS=30000
RAG_MILVUS_COLLECTION=rag_document_chunks_bge_m3
```

Start the worker through Docker Compose only when needed:

```powershell
docker compose --profile rag-worker up -d rag-worker
```

Verify the worker and RAG readiness:

```powershell
.\scripts\test-rag-embedding.ps1
```

## 9. Switching To Real LLM

The default local setup uses mock LLM so the full RAG pipeline can run without paid or private model credentials. To switch to a real OpenAI-compatible chat completion service, update `.env`:

```text
RAG_LLM_PROVIDER=http
RAG_LLM_API_KEY=your_real_api_key
RAG_LLM_BASE_URL=https://your-provider.example.com/v1
RAG_LLM_MODEL=your-chat-model
RAG_LLM_TIMEOUT_MS=30000
RAG_LLM_TEMPERATURE=0.2
RAG_LLM_MAX_TOKENS=1200
```

`RAG_LLM_API_KEY` is optional for local services that do not require authentication. If it is set, RAG Agent sends it as a Bearer token.

RAG Agent sends a chat-completions style request:

```json
{
  "model": "your-chat-model",
  "messages": [
    {
      "role": "system",
      "content": "You are an enterprise RAG assistant. Answer only from the supplied context and cite sources."
    },
    {
      "role": "user",
      "content": "permission-safe RAG prompt"
    }
  ],
  "temperature": 0.2,
  "max_tokens": 1200
}
```

Supported response shapes include OpenAI-like responses:

```json
{
  "choices": [
    {
      "message": {
        "content": "answer text"
      }
    }
  ]
}
```

and simpler local responses:

```json
{
  "answer": "answer text"
}
```

or:

```json
{
  "text": "answer text"
}
```

If LLM generation fails after retrieval succeeds, RAG Agent returns:

```text
status = LLM_FALLBACK
answer = retrieved permission-safe snippets
citations = still returned
chunks = still returned
```

This lets the frontend still show grounded retrieval evidence while making the generation problem clear.

## 10. Knowledge Base And Upload Storage

The first knowledge-base storage layer is now split into three places:

- MySQL `rag_knowledge_base`: knowledge base metadata, owner, department scope, default security level, document/chunk counters.
- MySQL `rag_source_document`: uploaded source document metadata, file hash, MinIO object key, parser status, index status, and optional parsed text cache.
- MinIO `rag-documents`: original uploaded files, including PDF, Word, PowerPoint, text, and other binary files.

Current upload behavior:

- PDF, Word, PowerPoint, plain text, Markdown, CSV, JSON, SQL, and other Tika-supported files are parsed during upload.
- Original files are always stored in MinIO before parsing, so upload audit data remains available even when parsing or indexing fails.
- Parsed text is written to `rag_source_document.parsed_text`.
- Parsed documents are mirrored into `sys_document`, so the existing `/rag/index/document/{documentId}` flow stays compatible during the migration.
- After parsing, RAG Agent automatically triggers chunking, embedding generation, and Milvus upsert through the existing index service.
- If parsing fails, the document returns `parser_status=PARSE_FAIL` and `index_status=PARSE_PENDING`.
- If indexing fails, the document keeps parsed text and returns `parser_status=PARSED` and `index_status=INDEX_FAIL`.
- Successful documents return `parser_status=PARSED` and `index_status=INDEXED`.

Useful local configuration:

```text
RAG_STORAGE_PROVIDER=minio
RAG_MINIO_ENDPOINT=http://localhost:9000
RAG_DOCUMENT_BUCKET=rag-documents
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

Use Docker Compose configuration when `rag-agent` runs inside Docker:

```text
RAG_STORAGE_PROVIDER=minio
RAG_MINIO_ENDPOINT=http://minio:9000
RAG_DOCUMENT_BUCKET=rag-documents
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

Core APIs:

```http
GET    /rag/kb
POST   /rag/kb
PUT    /rag/kb/{kbId}
DELETE /rag/kb/{kbId}
GET    /rag/kb/{kbId}/documents
POST   /rag/kb/{kbId}/documents/upload
POST   /rag/kb/{kbId}/documents/{documentId}/reprocess
DELETE /rag/kb/{kbId}/documents/{documentId}
```

Create a knowledge base:

```powershell
$kbBody = @{
  name = "Credit Knowledge Base"
  description = "Credit department policy and operating documents"
  deptId = 1
  visibility = "DEPARTMENT"
  securityLevel = 2
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8085/rag/kb" `
  -Method Post `
  -Headers @{
    "Content-Type" = "application/json"
    "X-User-Name" = "admin"
  } `
  -Body $kbBody
```

Upload documents:

```powershell
curl.exe -X POST "http://localhost:8085/rag/kb/1/documents/upload" `
  -H "X-User-Name: admin" `
  -F "files=@docs/RAG_AGENT_RUNBOOK.md" `
  -F "deptId=1" `
  -F "securityLevel=2"
```

Reprocess a stored document without uploading it again:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8085/rag/kb/1/documents/1/reprocess" `
  -Method Post `
  -Headers @{ "X-User-Name" = "admin" }
```

Use reprocess when:

- The embedding worker or Milvus was unavailable during upload.
- A document is stuck in `INDEX_FAIL`.
- Parser settings or dependencies changed and the original file should be parsed again from MinIO.
