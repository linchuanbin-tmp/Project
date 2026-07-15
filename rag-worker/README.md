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

Notes:

- Keep the worker terminal open while using RAG.
- `/health` does not load the model. The model loads lazily on the first
  `/embed` request.
- `HF_TOKEN` is optional. Set it only if Hugging Face rate limits or slows
  downloads.
- Windows may show a Hugging Face symlink cache warning. It is harmless; caching
  still works, but may use more disk space.

## Health Check

```powershell
Invoke-RestMethod -Uri "http://localhost:8091/health" -Method Get
```

Before the first `/embed`, `modelLoaded` may be `False`. That is expected.

## Embed Check

```powershell
$body = @{
  input = "This is a RAG embedding test"
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
