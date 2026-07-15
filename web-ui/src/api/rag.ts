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
  fileType?: string
  parseStatus?: string
  hasStoredFile?: boolean
  indexed: boolean
  chunkCount: number
  documentCreateTime?: string
  lastIndexedAt?: string
  firstVectorId?: string
  latestContentHash?: string
  embeddingProfile?: string
  embeddingModel?: string
  vectorCollection?: string
  indexStatus?: string
}

export interface RagDocumentChunkDetail {
  chunkId: number
  documentId: number
  chunkIndex: number
  chunkText: string
  tokenCount?: number
  vectorId?: string
  embeddingProfile?: string
  embeddingModel?: string
  vectorCollection?: string
  indexStatus?: string
  securityLevel?: number
  deptId?: number
  contentHash?: string
  createTime?: string
  updateTime?: string
}

export interface RagAccessRequest {
  documentId: number
  reason?: string
}

export interface RagAccessRequestResponse {
  documentId: number
  notificationId?: number
  receiverId?: number
  status: string
  message?: string
}

export interface RagHealthResponse {
  status: string
  service: string
  vectorStore: string
  milvusCollection?: string
  vectorMetric?: string
  vectorIndex?: string
  embeddingProvider: string
  embeddingDim: number
  embeddingActualDim?: number
  embeddingReady?: boolean
  embeddingProbed?: boolean
  embeddingMessage?: string
  embeddingTimeoutMs?: number
  embeddingEndpointConfigured?: boolean
  embeddingApiKeyConfigured?: boolean
  embeddingModel?: string
  llmProvider?: string
  llmModel?: string
  chunkSizeTokens?: number
  chunkOverlapTokens?: number
}

export interface RagEmbeddingProfile {
  id: string
  label: string
  provider: string
  endpoint?: string
  endpointConfigured?: boolean
  apiKeyConfigured?: boolean
  model: string
  dimension: number
  timeoutMs?: number
  collectionName: string
  active?: boolean
  indexStatus?: string
  indexMessage?: string
}

export interface RagEmbeddingActivationResponse {
  activeProfileId: string
  previousProfileId?: string
  indexStatus: string
  rebuildRequired: boolean
  message?: string
}

export interface RagEmbeddingReadiness {
  provider: string
  ready: boolean
  probed: boolean
  message?: string
  dimension?: number
  actualDimension?: number
  model?: string
  endpointConfigured?: boolean
  apiKeyConfigured?: boolean
  timeoutMs?: number
}

export const getRagHealth = () =>
  request.get<any, RagHealthResponse>('/rag/health')

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

export const reprocessRagDocument = (documentId: number) =>
  request.post<any, RagIndexResponse>(`/rag/index/document/${documentId}/reprocess`)

export const deleteRagDocumentIndex = (documentId: number) =>
  request.delete<any, RagIndexResponse>(`/rag/index/document/${documentId}`)

export const getRagDocumentIndexStatus = () =>
  request.get<any, RagDocumentIndexStatus[]>('/rag/index/documents/status')

export const getRagDocumentChunks = (documentId: number) =>
  request.get<any, RagDocumentChunkDetail[]>(`/rag/index/document/${documentId}/chunks`)

export const requestRagDocumentAccess = (data: RagAccessRequest) =>
  request.post<any, RagAccessRequestResponse>('/rag/access-request', data)

export const getRagEmbeddingProfiles = () =>
  request.get<any, RagEmbeddingProfile[]>('/rag/embedding/profiles')

export const getActiveRagEmbeddingProfile = () =>
  request.get<any, RagEmbeddingProfile>('/rag/embedding/active')

export const testRagEmbeddingProfile = (profileId: string) =>
  request.post<any, RagEmbeddingReadiness>(`/rag/embedding/profiles/${profileId}/test`)

export const activateRagEmbeddingProfile = (profileId: string) =>
  request.post<any, RagEmbeddingActivationResponse>(`/rag/embedding/profiles/${profileId}/activate`)
