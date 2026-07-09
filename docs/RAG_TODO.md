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

- [ ] Add `docker/init/patch_rag_tables.sql`.
- [ ] Add `rag_document_chunk`.
- [ ] Add `rag_query_log`.
- [ ] Add `rag_index_task`.
- [ ] Add entity and mapper classes.

## Milestone 3: Permission Layer

- [ ] Resolve current user from gateway headers.
- [ ] Load user department, clearance level, and roles.
- [ ] Compute accessible document IDs.
- [ ] Support approved `RAG_APPLY` temporary access.
- [ ] Add permission unit tests.

## Milestone 4: Chunking and Indexing

- [ ] Implement `DocumentChunker`.
- [ ] Implement single-document indexing.
- [ ] Implement full index rebuild.
- [ ] Store chunk metadata in MySQL.
- [ ] Track indexing tasks.

## Milestone 5: Milvus and Embeddings

- [ ] Add Milvus, etcd, and minio to Docker Compose.
- [ ] Add Milvus collection initializer.
- [ ] Add vector upsert/delete/search service.
- [ ] Add configurable embedding client.
- [ ] Generate embeddings for chunks and questions.

## Milestone 6: Query and Generation

- [ ] Implement `/rag/query`.
- [ ] Retrieve top chunks with permission filtering.
- [ ] Build grounded prompt.
- [ ] Call configured LLM.
- [ ] Return answer and citations.
- [ ] Write query audit logs.

## Milestone 7: Platform Integration

- [ ] Connect `task-service` `RAG` tasks to `rag-agent`.
- [ ] Stream RAG task progress through WebSocket.
- [ ] Replace RAG placeholder page with a real workspace.
- [ ] Add `web-ui/src/api/rag.ts`.
- [ ] Route Copilot RAG intent to `/app/rag?query=...`.

## Milestone 8: Validation

- [ ] Admin can retrieve high-security documents.
- [ ] Standard user cannot retrieve another department's documents.
- [ ] Low-clearance user cannot retrieve high-security documents.
- [ ] Approved `RAG_APPLY` grants access to a specific document.
- [ ] Updated documents can be re-indexed.
- [ ] Missing Milvus or LLM dependencies return clear errors.
