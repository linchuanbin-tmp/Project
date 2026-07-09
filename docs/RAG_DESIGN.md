# RAG Agent Design

## Goal

The RAG Agent will turn the current document library and approval workflow into a secure enterprise knowledge question-answering subsystem. It must retrieve only documents the current user is allowed to access, generate grounded answers from retrieved chunks, and return citations for auditability.

## Current Project Context

The platform already has these RAG-adjacent foundations:

- `sys_document` stores document title, content, department scope, and security level.
- `sys_user` stores department and clearance level.
- `sys_notification` supports `RAG_APPLY` approval records.
- The document UI supports restricted document viewing and temporary access requests.
- Copilot and dashboard routing already classify some prompts as RAG intent.

The missing pieces are the actual RAG backend, vector index, embedding pipeline, retrieval service, grounded answer generation, and task-service integration.

## Target Architecture

```text
web-ui
  -> gateway-service
    -> rag-agent:8085
       -> MySQL: documents, permissions, chunks, query logs
       -> Redis: future cache/session helpers
       -> Milvus: vector search over document chunks
       -> LLM/Embedding provider: embeddings and grounded answers
```

The first implementation should create `rag-agent` as a Spring Boot microservice. The vector database target is Milvus, with an optional embedding worker or configurable remote embedding endpoint.

## Main Backend Components

- `RagQueryController`: accepts question-answering requests.
- `RagIndexController`: rebuilds all indexes or a single document index.
- `RagPermissionService`: resolves accessible document IDs for the current user.
- `DocumentChunker`: splits documents into retrieval-friendly chunks.
- `EmbeddingService`: generates vectors for chunks and questions.
- `MilvusVectorStoreService`: writes, deletes, and searches chunk vectors.
- `RagRetrievalService`: performs permission-aware vector retrieval.
- `RagGenerationService`: builds prompts and calls the LLM.
- `RagQueryLogService`: records query audit data.

## Core Data Model

Existing table:

- `sys_document`: source of document metadata and content.

New tables:

- `rag_document_chunk`: maps source documents to chunks and vector IDs.
- `rag_query_log`: stores user query, answer, retrieved documents, blocked documents, latency, and status.
- `rag_index_task`: tracks rebuild and single-document indexing tasks.

## Milvus Collection

Collection name:

```text
rag_document_chunks
```

Suggested fields:

- `chunk_id`: unique chunk identifier.
- `document_id`: source `sys_document.id`.
- `dept_id`: document department scope.
- `security_level`: document security level.
- `content`: chunk text for citation display.
- `embedding`: float vector.

Suggested index:

- Metric: `COSINE`.
- Index type: `HNSW` or `IVF_FLAT`.
- Dimension: configured by embedding model, for example `768`.

## Permission Rules

RAG retrieval must follow the same security model as document viewing:

1. Admin users can access all documents.
2. Department admins and standard users can access global documents and documents from their own department.
3. A user can access a document only when `user.clearance_level >= document.security_level`.
4. If clearance is insufficient, a passed `RAG_APPLY` approval grants temporary access to the specific document.
5. Retrieval must apply permissions before vector search and re-check permissions after vector search.

This double-check prevents a vector search result from leaking restricted content if one layer is misconfigured.

## Query Flow

```text
1. Receive question through POST /rag/query.
2. Resolve current user from X-User-Name and roles from X-User-Roles.
3. Compute accessible document IDs from MySQL and approval records.
4. Generate embedding for the user question.
5. Search Milvus with allowed document filtering.
6. Re-check retrieved chunks against permissions.
7. Build a grounded prompt from top chunks.
8. Call the LLM to produce an answer.
9. Return answer and citations.
10. Write rag_query_log for audit.
```

## Indexing Flow

```text
1. Read document from sys_document.
2. Split content by Markdown headings and paragraphs.
3. Apply chunk size and overlap.
4. Generate content hash to avoid duplicate indexing.
5. Generate embedding for each chunk.
6. Store chunk metadata in MySQL.
7. Upsert chunk vectors into Milvus.
8. Record index task status.
```

## Gateway Integration

`gateway-service` will route:

```text
/api/rag/** -> rag-agent:8085
```

The gateway keeps the existing JWT validation and forwards user identity headers to `rag-agent`.

## Task Service Integration

`task-service` currently treats `RAG` tasks as offline. It should later call:

```text
POST ${RAG_SERVICE_URI}/rag/query
```

and stream progress:

```text
20% permission validation
40% question embedding
60% vector retrieval
80% answer generation
100% completed
```

## Frontend Integration

The placeholder `web-ui/src/views/rag/index.vue` should become a RAG workspace with:

- Question input.
- Generated answer.
- Citation cards.
- Security level indicators.
- Restricted content messages.
- Loading, error, and empty states.

Copilot should route RAG intent to:

```text
/app/rag?query=<question>
```

## Main Risks

- Permission leakage through vector retrieval.
- MySQL chunk metadata becoming inconsistent with Milvus vectors.
- Poor chunking causing weak retrieval quality.
- LLM hallucination when retrieved context is insufficient.
- Docker complexity from Milvus, etcd, and minio.

## First Milestone

The first milestone creates the foundation without changing existing business behavior:

- Add design and TODO documents.
- Add `rag-agent` Spring Boot skeleton.
- Add `/rag/health`.
- Add the module to the Maven root project.
- Add gateway route for `/api/rag/**`.
- Add RAG environment variables.
