# RAG Evaluation Results

This document summarizes the evaluation evidence for the RAG Agent. The table can be regenerated with:

```powershell
.\scripts\test-rag-evaluation.ps1 -SkipRebuild
```

Run without `-SkipRebuild` when documents, embedding profiles, or Milvus collections have changed:

```powershell
.\scripts\test-rag-evaluation.ps1
```

## Evaluation Matrix

| Area | Test Case | Expected Behavior | Evidence / Measurement |
| --- | --- | --- | --- |
| Service readiness | RAG health check | `/rag/health` returns `UP`, active embedding profile, Milvus collection, embedding dimension, and index status. | Verified by `scripts/test-rag-evaluation.ps1`; report records active profile and collection. |
| Embedding profile isolation | Active profile check | `/rag/embedding/active` returns a ready profile with a dedicated collection, such as `local-bge-m3 -> rag_document_chunks_bge_m3` or `qwen-v4 -> rag_document_chunks_qwen_v4_1024`. | The evaluation script records profile id, model, collection, and index status. |
| Index rebuild | Active profile rebuild | `/rag/index/rebuild` finishes with `SUCCESS` and a positive `chunkCount`. | Rebuild result is recorded in the generated Markdown table. |
| Document index status | Indexed document coverage | `/rag/index/documents/status` shows at least one indexed document and chunk counts for indexed documents. | The script records indexed/total document counts. |
| Citation correctness | Citation-to-chunk consistency | Every citation returned by `/rag/query` must correspond to a returned chunk by `documentId`, `chunkId`, and `chunkIndex`. | The evaluation script compares citation keys against returned chunk keys. |
| Permission leakage | Low-clearance users | Retrieved chunks must not exceed the user's department and clearance scope. | The script checks `deptId` and `securityLevel` for `credit_staff` and `compliance_staff`. |
| No-context behavior | Insufficient accessible context | If no permission-safe context is available, the RAG Agent returns `NO_CONTEXT` instead of hallucinating. | Covered by controlled status checks: `SUCCESS`, `NO_CONTEXT`, or `LLM_FALLBACK` are accepted; unexpected failures are reported. |
| Temporary RAG access | Approved RAG_APPLY requests | RAG access approval is temporary and expires after 24 hours. | Backend checks approved notification `updateTime/createTime + accessTtlHours`; frontend approval card shows the TTL. |
| Latency | Query response time | Query latency should be recorded for representative RAG questions. | The evaluation script records average and maximum latency from `latencyMs`. |
| LLM fallback | LLM unavailable | If LLM generation fails, the system returns permission-safe retrieved context with `LLM_FALLBACK`. | Covered by backend status handling; can be manually verified by disabling the configured LLM provider. |

## Notes for Final Report

- The current permission design uses pre-computed accessible document scope, Milvus retrieval-time document filtering, and Java post-retrieval verification.
- The RAG Agent should not be described as using unrestricted vector search followed only by UI-side filtering.
- Citation UI displays document title, document ID, chunk index, score, and snippet.
- RAG_APPLY is a temporary 24-hour approval, not a permanent permission grant.
