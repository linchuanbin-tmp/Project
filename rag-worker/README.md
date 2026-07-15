# RAG Worker

Local embedding worker for `rag-agent`. It exposes a small HTTP API around
`sentence-transformers` and defaults to `BAAI/bge-m3`.

## Local Run

```powershell
cd "D:\github\Intelligent Multi-Agent System for Enterprise Operations\Project"
.\.venv-rag\Scripts\Activate.ps1
$env:HF_DOWNLOAD="1"
python -m pip install -r rag-worker\requirements.txt
.\scripts\start-rag-worker.ps1
```

First startup downloads `BAAI/bge-m3` into the Hugging Face cache. The model
dimension is `1024`.

## Health Check

```powershell
Invoke-RestMethod -Uri "http://localhost:8091/health" -Method Get
```

## Embed Check

```powershell
$body = @{
  input = "这是一个 RAG embedding 测试"
  model = "BAAI/bge-m3"
} | ConvertTo-Json

$res = Invoke-RestMethod `
  -Uri "http://localhost:8091/embed" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body

$res.embedding.Count
```

Expected:

```text
1024
```

## RAG Agent Configuration

For local Java runs:

```env
RAG_EMBEDDING_PROVIDER=http
RAG_EMBEDDING_ENDPOINT=http://localhost:8091/embed
RAG_EMBEDDING_API_KEY=
RAG_EMBEDDING_MODEL=BAAI/bge-m3
RAG_EMBEDDING_DIM=1024
RAG_EMBEDDING_TIMEOUT_MS=30000
RAG_MILVUS_COLLECTION=rag_document_chunks_bge_m3
```

For Docker Compose runs with the `rag-worker` profile:

```env
RAG_EMBEDDING_PROVIDER=http
RAG_EMBEDDING_ENDPOINT=http://rag-worker:8091/embed
RAG_EMBEDDING_API_KEY=
RAG_EMBEDDING_MODEL=BAAI/bge-m3
RAG_EMBEDDING_DIM=1024
RAG_EMBEDDING_TIMEOUT_MS=30000
RAG_MILVUS_COLLECTION=rag_document_chunks_bge_m3
```
