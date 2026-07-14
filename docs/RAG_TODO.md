# RAG Agent TODO

## Milestone 1: Foundation

- [x] Create RAG design document.
- [x] Create RAG implementation TODO document.
- [x] Create `rag-agent` Spring Boot module.
- [x] Add `/rag/health`.
- [x] Add `rag-agent` to root Maven modules.
- [x] Add gateway route for `/api/rag/**`.
- [x] Add RAG environment variables to `.env.example`.

## Milestone 2: Data Model

- [x] Add `docker/init/patch_rag_tables.sql`.
- [x] Add `rag_document_chunk`.
- [x] Add `rag_query_log`.
- [x] Add `rag_index_task`.
- [x] Add entity and mapper classes.

## Milestone 3: Permission Layer

- [x] Resolve current user from gateway headers.
- [x] Load user department, clearance level, and roles.
- [x] Compute accessible document IDs.
- [x] Support approved `RAG_APPLY` temporary access.
- [ ] Add permission unit tests.

## Milestone 4: Chunking and Indexing

- [x] Implement `DocumentChunker`.
- [x] Implement single-document indexing.
- [x] Implement full index rebuild.
- [x] Store chunk metadata in MySQL.
- [x] Track indexing tasks.

## Milestone 5: Milvus and Embeddings

- [x] Add Milvus, etcd, and minio to Docker Compose.
- [x] Add Milvus collection initializer.
- [x] Add vector upsert/delete/search service.
- [x] Add configurable embedding client.
- [x] Generate embeddings for chunks and questions.

## Milestone 6: Query and Generation

- [x] Implement `/rag/query`.
- [x] Retrieve top chunks with permission filtering.
- [x] Build grounded prompt.
- [x] Call configured LLM.
- [x] Return answer and citations.
- [x] Write query audit logs.

## Milestone 7: Platform Integration

- [x] Connect `task-service` `RAG` tasks to `rag-agent`.
- [x] Stream RAG task progress through WebSocket.
- [x] Replace RAG placeholder page with a real workspace.
- [x] Add `web-ui/src/api/rag.ts`.
- [x] Route Copilot RAG intent to `/app/rag?query=...`.

## Milestone 8: Validation

- [ ] Admin can retrieve high-security documents.
- [ ] Standard user cannot retrieve another department's documents.
- [ ] Low-clearance user cannot retrieve high-security documents.
- [ ] Approved `RAG_APPLY` grants access to a specific document.
- [ ] Updated documents can be re-indexed.
- [ ] Missing Milvus or LLM dependencies return clear errors.

## Milestone 9: Runbook and Verification

- [x] Add a local RAG Agent runbook.
- [x] Add a PowerShell verification script for health, rebuild, index status, chunks, permission queries, and optional access requests.
- [x] Document the frontend demo flow for Documents, RAG Workspace, blocked documents, and RAG_APPLY approval.

## Milestone 10: Embedding Provider Hardening

- [x] Default local embedding and LLM providers to mock.
- [x] Add embedding timeout configuration.
- [x] Add optional embedding API key and model configuration for real providers.
- [x] Support direct and OpenAI-like embedding response shapes.
- [x] Add clear embedding errors for missing endpoint, empty vector, non-numeric values, HTTP failures, timeouts, and dimension mismatch.
- [x] Expose embedding, LLM, vector store, chunking, and Milvus configuration in `/rag/health`.
- [x] Document how to switch from mock embedding to a real embedding service.
