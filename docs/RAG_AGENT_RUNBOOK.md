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
