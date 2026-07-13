import request from '@utils/request'

export interface RagQueryRequest {
  question: string
  topK?: number
}

export interface RagCitation {
  documentId: number
  chunkId: number
  chunkIndex: number
  score?: number
  snippet?: string
}

export interface RagChunk {
  vectorId?: string
  documentId: number
  chunkId: number
  chunkIndex: number
  deptId?: number
  securityLevel?: number
  score?: number
  chunkText?: string
}

export interface RagQueryResponse {
  traceId: string
  status: string
  answer: string
  citations: RagCitation[]
  chunks: RagChunk[]
  retrievedDocumentIds: number[]
  blockedDocumentIds: number[]
  topK: number
  latencyMs: number
  message?: string
}

export interface AccessibleDocument {
  documentId: number
  title: string
  deptId?: number
  securityLevel?: number
  accessReason: string
}

export interface RagPermissionSnapshot {
  userId: number
  username: string
  deptId?: number
  clearanceLevel: number
  roles: string[]
  allowedDocumentIds: number[]
  accessibleDocuments: AccessibleDocument[]
}

export interface RagIndexTask {
  id: number
  documentId?: number
  taskType: string
  status: string
  message?: string
  createTime?: string
  updateTime?: string
}

export interface RagIndexResponse {
  taskId: number
  documentId?: number
  taskType: string
  status: string
  chunkCount: number
  message?: string
}

export interface RagDocumentIndexStatus {
  documentId: number
  title: string
  deptId?: number
  securityLevel?: number
  indexed: boolean
  chunkCount: number
  documentCreateTime?: string
  lastIndexedAt?: string
  firstVectorId?: string
  latestContentHash?: string
}

export interface RagDocumentChunkDetail {
  chunkId: number
  documentId: number
  chunkIndex: number
  chunkText: string
  tokenCount?: number
  vectorId?: string
  securityLevel?: number
  deptId?: number
  contentHash?: string
  createTime?: string
  updateTime?: string
}

export const queryRag = (data: RagQueryRequest) =>
  request.post<any, RagQueryResponse>('/rag/query', data)

export const getAccessibleDocuments = () =>
  request.get<any, RagPermissionSnapshot>('/rag/permissions/documents')

export const getRagIndexTasks = (limit = 10) =>
  request.get<any, RagIndexTask[]>('/rag/index/tasks', { params: { limit } })

export const rebuildRagIndex = () =>
  request.post<any, RagIndexResponse>('/rag/index/rebuild')

export const indexRagDocument = (documentId: number) =>
  request.post<any, RagIndexResponse>(`/rag/index/document/${documentId}`)

export const deleteRagDocumentIndex = (documentId: number) =>
  request.delete<any, RagIndexResponse>(`/rag/index/document/${documentId}`)

export const getRagDocumentIndexStatus = () =>
  request.get<any, RagDocumentIndexStatus[]>('/rag/index/documents/status')

export const getRagDocumentChunks = (documentId: number) =>
  request.get<any, RagDocumentChunkDetail[]>(`/rag/index/document/${documentId}/chunks`)
