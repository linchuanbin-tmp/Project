<template>
  <div class="rag-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('rag.pageTitle') }}</h1>
        <p class="page-sub">{{ $t('rag.pageSub') }}</p>
      </div>
      <div class="header-actions">
        <el-button class="btn-refresh" :loading="refreshing" @click="refreshWorkspace">
          <RefreshCw :size="14" :class="{ spin: refreshing }" />
          Refresh
        </el-button>
        <el-button v-if="isAdmin" class="btn-primary" :loading="rebuilding" @click="handleRebuild">
          <RotateCcw :size="14" :class="{ spin: rebuilding }" />
          Rebuild Index
        </el-button>
      </div>
    </div>

    <div class="workspace-grid">
      <main class="query-workspace">
        <!-- No accessible documents banner -->
        <div v-if="!documentsLoading && !hasAccessibleDocs" class="no-access-banner">
          <ShieldAlert :size="16" />
          <span>{{ $t('rag.noAccessibleDocs') }}</span>
        </div>

        <!-- Index not ready banner -->
        <div v-if="health && !health.embeddingReady" class="index-rebuilding-banner">
          <RefreshCw :size="15" class="spin" />
          <span>Knowledge base index is being prepared. This happens automatically on first startup and should finish shortly. You can try asking questions, but results may not appear until indexing completes.</span>
        </div>

        <div class="premium-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <MessageSquareText :size="16" class="icon-sparkles" />
              Ask Knowledge Base
            </span>
          </div>

          <div class="modern-input-container">
            <textarea
              v-model="question"
              rows="4"
              placeholder="Ask about accessible enterprise documents..."
              class="modern-textarea"
              @keydown.ctrl.enter.prevent="handleQuery"
            ></textarea>
            <div class="modern-input-footer">
              <div class="footer-spacer"></div>
              <button
                :disabled="!question.trim() || querying"
                class="modern-btn-generate"
                @click="handleQuery"
              >
                <span v-if="querying">{{ $t('code.generating') }}</span>
                <span v-else>Send</span>
              </button>
            </div>
          </div>

          <div class="topk-row">
            <div class="topk-desc">
              <span class="topk-label">Documents to retrieve</span>
              <span class="topk-hint">How many relevant document chunks to search before generating an answer</span>
            </div>
            <div class="topk-control">
              <el-slider
                v-model="topK"
                :min="1"
                :max="20"
                :step="1"
                size="small"
                class="topk-slider"
              />
              <span class="topk-value">{{ topK }}</span>
            </div>
          </div>

          <div class="suggested-question-block">
            <div class="suggested-question-head">
              <span>Suggested Questions</span>
              <small>Click one to test retrieval, citations, and permission filtering</small>
            </div>
            <div class="suggested-question-list">
              <button
                v-for="item in suggestedQuestions"
                :key="item.question"
                class="suggested-question"
                :class="item.tone"
                :disabled="querying"
                @click="handleSuggestedQuestion(item.question)"
              >
                <span class="suggested-label">{{ item.label }}</span>
                <span class="suggested-text">{{ item.question }}</span>
              </button>
            </div>
          </div>
        </div>

        <div v-if="response" class="premium-card review-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <FileSearch :size="16" class="icon-database" />
              Answer
            </span>
            <div class="response-meta">
              <el-tag size="small" :type="statusTagType(response.status)">{{ response.status }}</el-tag>
              <span class="stat-text">{{ response.latencyMs || 0 }}ms</span>
            </div>
          </div>

          <div class="answer-text" v-html="renderedAnswer"></div>
          <div class="trace-row">
            <span>Trace</span>
            <code>{{ response.traceId }}</code>
          </div>
        </div>

        <div v-if="querying" class="loading-state">
          <el-skeleton :rows="5" animated />
        </div>

        <!-- Empty state: no query performed yet -->
        <div v-if="!response && !querying && hasAccessibleDocs" class="premium-card review-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <MessageSquareText :size="16" class="icon-sparkles" />
              Getting Started
            </span>
          </div>
          <div class="getting-started">
            <p>Your RAG workspace is ready. Here is how to begin:</p>
            <ol>
              <li><strong>Upload documents</strong> in the Documents page -- add PDFs, Word files, or PPTs to the knowledge base.</li>
              <li><strong>Index documents</strong> -- click the refresh icon next to each uploaded document in the sidebar.</li>
              <li><strong>Ask a question</strong> above, or click one of the suggested questions to test retrieval and security filtering.</li>
            </ol>
            <p class="gs-hint">Documents respect department and clearance-level access rules. Responses will cite source chunks so you can verify the answer.</p>
          </div>
        </div>

        <div v-if="citations.length" class="premium-card review-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Quote :size="16" class="icon-database" />
              Citations
            </span>
            <el-tag size="small">{{ citations.length }}</el-tag>
          </div>
          <div class="citation-list">
            <article v-for="citation in citations" :key="citationKey(citation)" class="citation-item">
              <div class="citation-head">
                <span>{{ citationTitle(citation) }} · Chunk {{ citation.chunkIndex }}</span>
                <el-tag size="small" effect="plain">{{ formatScore(citation.score) }}</el-tag>
              </div>
              <div class="citation-sub">Doc {{ citation.documentId }}</div>
              <p>{{ citation.snippet || 'No snippet returned.' }}</p>
            </article>
          </div>
        </div>

        <div v-if="chunks.length" class="premium-card review-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Layers3 :size="16" class="icon-database" />
              Retrieved Chunks
            </span>
            <el-tag size="small">{{ chunks.length }}</el-tag>
          </div>
          <el-collapse class="chunk-collapse">
            <el-collapse-item
              v-for="chunk in chunks"
              :key="chunk.vectorId || `${chunk.documentId}-${chunk.chunkIndex}`"
              :name="chunk.vectorId || `${chunk.documentId}-${chunk.chunkIndex}`"
            >
              <template #title>
                <div class="chunk-title">
                  <span>Doc {{ chunk.documentId }} · Chunk {{ chunk.chunkIndex }}</span>
                  <span>Level {{ chunk.securityLevel || 1 }}</span>
                </div>
              </template>
              <p class="chunk-text">{{ chunk.chunkText }}</p>
            </el-collapse-item>
          </el-collapse>
        </div>

        <div v-if="blockedDocumentIds.length" class="premium-card review-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <ShieldAlert :size="16" class="icon-database" />
              Blocked Documents
            </span>
            <el-tag size="small" type="warning">{{ blockedDocumentIds.length }}</el-tag>
          </div>
          <div class="blocked-list">
            <article v-for="documentId in blockedDocumentIds" :key="documentId" class="blocked-item">
              <div>
                <h3>{{ documentTitle(documentId) }}</h3>
                <p>
                  Doc {{ documentId }}
                  <span v-if="documentSecurityLevel(documentId)"> · Level {{ documentSecurityLevel(documentId) }}</span>
                </p>
              </div>
              <el-button
                type="warning"
                size="small"
                :loading="accessRequestingDocId === documentId"
                @click="handleRequestAccess(documentId)"
              >
                Request Access
              </el-button>
            </article>
          </div>
        </div>
      </main>

      <aside class="side-workspace">
        <div class="premium-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Activity :size="16" class="icon-sparkles" />
              RAG Readiness
            </span>
            <button class="mini-refresh-btn" :disabled="healthLoading" @click="loadHealth">
              <RefreshCw :size="13" :class="{ spin: healthLoading }" />
            </button>
          </div>

          <div v-if="healthLoading && !health" class="side-loading">
            <el-skeleton :rows="4" animated />
          </div>
          <template v-else-if="health">
            <div class="health-status-row">
              <div>
                <span class="health-label">Embedding</span>
                <strong>{{ health.embeddingProvider }}</strong>
              </div>
              <el-tag size="small" :type="health.embeddingReady ? 'success' : 'danger'">
                {{ health.embeddingReady ? 'Ready' : 'Not Ready' }}
              </el-tag>
            </div>
            <div class="health-grid">
              <div>
                <span>Expected Dim</span>
                <strong>{{ health.embeddingDim || '-' }}</strong>
              </div>
              <div>
                <span>Actual Dim</span>
                <strong>{{ health.embeddingActualDim || '-' }}</strong>
              </div>
              <div>
                <span>Vector Store</span>
                <strong>{{ health.vectorStore }}</strong>
              </div>
              <div>
                <span>LLM</span>
                <strong>{{ health.llmProvider || '-' }}</strong>
              </div>
            </div>
            <p class="health-message" :class="{ unhealthy: !health.embeddingReady }">
              {{ health.embeddingMessage || 'No readiness message returned.' }}
            </p>
          </template>
          <el-empty v-else description="Health unavailable" />
        </div>

        <div class="premium-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <ShieldCheck :size="16" class="icon-sparkles" />
              Accessible Documents
            </span>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-tag size="small">{{ accessibleDocuments.length }}</el-tag>
              <button class="mini-refresh-btn" :disabled="documentsLoading" @click="refreshWorkspace">
                <RefreshCw :size="13" :class="{ spin: documentsLoading }" />
              </button>
            </div>
          </div>

          <div v-if="documentsLoading" class="side-loading">
            <el-skeleton :rows="6" animated />
          </div>
          <el-empty v-else-if="!accessibleDocuments.length" description="No accessible documents" />
          <div v-else class="document-list">
            <article
              v-for="doc in visibleDocuments"
              :key="doc.documentId"
              class="document-item"
              @click="openDocumentReader(doc.documentId)"
            >
              <div class="document-main">
                <BookOpen :size="16" />
                <div>
                  <h3>{{ doc.title || `Document ${doc.documentId}` }}</h3>
                  <div class="doc-tags-row">
                    <span class="level-tag" :class="levelClass(doc.securityLevel)">Level {{ doc.securityLevel || 1 }}</span>
                    <span class="reason-tag" :class="reasonClass(doc.accessReason)">{{ reasonLabel(doc.accessReason) }}</span>
                    <span v-if="documentPipelineStatus(doc.documentId)" class="reason-tag" :class="pipelineClass(documentPipelineStatus(doc.documentId))">
                      {{ documentPipelineStatus(doc.documentId) }}
                    </span>
                  </div>
                </div>
              </div>
              <el-button :loading="indexingDocId === doc.documentId" size="small" @click.stop="handleIndexDocument(doc.documentId)">
                <RefreshCw :size="13" :class="{ spin: indexingDocId === doc.documentId }" />
              </el-button>
            </article>
            <button v-if="hasMoreDocs" class="view-all-link" @click="docsDialogVisible = true">
              View all {{ accessibleDocuments.length }} documents →
            </button>
          </div>
        </div>

        <div class="premium-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Clock3 :size="16" class="icon-database" />
              Index Tasks
            </span>
            <el-button size="small" :loading="tasksLoading" @click="loadTasks">
              <RefreshCw :size="13" :class="{ spin: tasksLoading }" />
            </el-button>
          </div>

          <div class="index-task-cards" v-if="indexTasks.length">
            <div v-for="task in indexTasks" :key="task.id" class="task-card-item">
              <div class="task-card-row">
                <div class="task-card-type">{{ task.taskType }}</div>
                <el-tag size="small" :type="statusTagType(task.status)">{{ task.status }}</el-tag>
              </div>
              <div class="task-card-time">Updated {{ formatTime(task.updateTime || task.createTime) }}</div>
            </div>
          </div>
          <el-empty v-else description="No index tasks" />
        </div>
      </aside>
    </div>

    <!-- ── All Documents Dialog ─────────────────────────────── -->
    <el-dialog v-model="docsDialogVisible" title="Accessible Documents" width="680px" :close-on-click-modal="true">
      <div class="docs-dialog-list">
        <article
          v-for="doc in paginatedDocs"
          :key="doc.documentId"
          class="document-item dialog-doc-item"
          @click="openDocumentReader(doc.documentId)"
        >
          <div class="document-main">
            <BookOpen :size="16" />
            <div>
              <h3>{{ doc.title || `Document ${doc.documentId}` }}</h3>
              <p>
                <span class="level-tag" :class="levelClass(doc.securityLevel)">Level {{ doc.securityLevel || 1 }}</span>
                <span class="reason-tag">{{ doc.accessReason }}</span>
                <span v-if="documentPipelineStatus(doc.documentId)" class="reason-tag" :class="pipelineClass(documentPipelineStatus(doc.documentId))">
                  {{ documentPipelineStatus(doc.documentId) }}
                </span>
              </p>
            </div>
          </div>
          <el-button :loading="indexingDocId === doc.documentId" size="small" @click.stop="handleIndexDocument(doc.documentId)">
            <RefreshCw :size="13" :class="{ spin: indexingDocId === doc.documentId }" />
          </el-button>
        </article>
      </div>
      <div style="display:flex;justify-content:center;margin-top:16px;">
        <el-pagination
          v-model:current-page="docsPage"
          :page-size="docsPageSize"
          :total="accessibleDocuments.length"
          layout="prev, pager, next"
          small
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Activity,
  BookOpen,
  Clock3,
  FileSearch,
  Layers3,
  MessageSquareText,
  Quote,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  ShieldCheck
} from 'lucide-vue-next'
import { marked } from 'marked'
import {
  getAccessibleDocuments,
  getRagHealth,
  getRagDocumentIndexStatus,
  getRagIndexTask,
  getRagIndexTasks,
  indexRagDocument,
  queryRag,
  rebuildRagIndex,
  requestRagDocumentAccess,
  type AccessibleDocument,
  type RagDocumentIndexStatus,
  type RagHealthResponse,
  type RagCitation,
  type RagChunk,
  type RagIndexTask,
  type RagQueryResponse
} from '@api/rag'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

const isAdmin = computed(() => userStore.userInfo?.roles?.includes('ROLE_ADMIN') || false)
const hasAccessibleDocs = computed(() => accessibleDocuments.value.length > 0)

const question = ref('')
const topK = ref(5)
const querying = ref(false)
const refreshing = ref(false)
const rebuilding = ref(false)
const documentsLoading = ref(false)
const tasksLoading = ref(false)
const healthLoading = ref(false)
const indexingDocId = ref<number | null>(null)
const accessRequestingDocId = ref<number | null>(null)

const response = ref<RagQueryResponse | null>(null)
const accessibleDocuments = ref<AccessibleDocument[]>([])
const documentStatusMap = ref<Record<number, RagDocumentIndexStatus>>({})
const indexTasks = ref<RagIndexTask[]>([])
const health = ref<RagHealthResponse | null>(null)

const suggestedQuestions = [
  {
    label: 'Public',
    tone: 'public',
    question: 'What are the main functions of the BankAgent platform?'
  },
  {
    label: 'Security',
    tone: 'security',
    question: 'How are security clearance levels defined in this system?'
  },
  {
    label: 'Credit',
    tone: 'credit',
    question: 'What clauses are included in the standard loan agreement template?'
  },
  {
    label: 'Compliance',
    tone: 'compliance',
    question: 'What are the AML monitoring and suspicious transaction reporting procedures?'
  },
  {
    label: 'Restricted',
    tone: 'restricted',
    question: 'What are the confidential credit risk evaluation rules for corporate accounts?'
  }
]

// ── Document list display ──
const MAX_VISIBLE_DOCS = 4
const docsDialogVisible = ref(false)
const docsPage = ref(1)
const docsPageSize = 15

const visibleDocuments = computed(() => accessibleDocuments.value.slice(0, MAX_VISIBLE_DOCS))
const hasMoreDocs = computed(() => accessibleDocuments.value.length > MAX_VISIBLE_DOCS)
const paginatedDocs = computed(() => {
  const start = (docsPage.value - 1) * docsPageSize
  return accessibleDocuments.value.slice(start, start + docsPageSize)
})

const citations = computed<RagCitation[]>(() => response.value?.citations || [])
const chunks = computed<RagChunk[]>(() => response.value?.chunks || [])
const blockedDocumentIds = computed<number[]>(() => response.value?.blockedDocumentIds || [])

const renderedAnswer = computed(() => {
  const raw = response.value?.answer
  if (!raw) return ''
  return marked.parse(raw, { breaks: true }) as string
})

// ── Query ──
const handleQuery = async () => {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('Please enter a question.')
    return
  }

  querying.value = true
  try {
    response.value = await queryRag({ question: text, topK: topK.value })
    if (response.value.status === 'SUCCESS') {
      ElMessage.success('RAG query completed.')
    } else {
      ElMessage.warning(response.value.message || response.value.status)
    }
  } finally {
    querying.value = false
  }
}

const handleSuggestedQuestion = async (text: string) => {
  question.value = text
  await handleQuery()
}

const loadAccessibleDocuments = async () => {
  documentsLoading.value = true
  try {
    const snapshot = await getAccessibleDocuments()
    accessibleDocuments.value = snapshot.accessibleDocuments || []
  } finally {
    documentsLoading.value = false
  }
}

const loadHealth = async () => {
  healthLoading.value = true
  try {
    health.value = await getRagHealth()
  } finally {
    healthLoading.value = false
  }
}

const loadDocumentIndexStatus = async () => {
  try {
    const statuses = await getRagDocumentIndexStatus()
    documentStatusMap.value = (statuses || []).reduce((acc: Record<number, RagDocumentIndexStatus>, item) => {
      acc[item.documentId] = item
      return acc
    }, {})
  } catch {
    documentStatusMap.value = {}
  }
}

const loadTasks = async () => {
  tasksLoading.value = true
  try {
    indexTasks.value = await getRagIndexTasks(12)
  } finally {
    tasksLoading.value = false
  }
}

const refreshWorkspace = async () => {
  refreshing.value = true
  try {
    await Promise.all([loadHealth(), loadAccessibleDocuments(), loadDocumentIndexStatus(), loadTasks()])
  } finally {
    refreshing.value = false
  }
}

const handleRebuild = async () => {
  rebuilding.value = true
  try {
    const result = await rebuildRagIndex()
    ElMessage.success(result.message || 'RAG index rebuild started.')
    await loadTasks()
    if (result.taskId) {
      const finalTask = await waitForIndexTask(result.taskId)
      if (finalTask.status === 'SUCCESS') {
        ElMessage.success(finalTask.message || 'RAG index rebuild completed.')
      } else if (finalTask.status === 'FAIL') {
        ElMessage.error(finalTask.message || 'RAG index rebuild failed.')
      }
      await refreshWorkspace()
    }
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to rebuild RAG index.')
  } finally {
    rebuilding.value = false
  }
}

const handleIndexDocument = async (documentId: number) => {
  indexingDocId.value = documentId
  try {
    const result = await indexRagDocument(documentId)
    ElMessage.success(result.message || `Document ${documentId} indexed.`)
    await loadTasks()
  } finally {
    indexingDocId.value = null
  }
}

const openDocumentReader = (documentId: number) => {
  router.push({ path: '/app/dept-docs', query: { id: String(documentId) } })
}

const handleRequestAccess = async (documentId: number) => {
  try {
    const { value } = await ElMessageBox.prompt(
      'Please explain why you need temporary access to this document.',
      'Request RAG Document Access',
      {
        confirmButtonText: 'Submit',
        cancelButtonText: 'Cancel',
        inputType: 'textarea',
        inputPlaceholder: 'Required for business analysis, audit review, or policy verification...',
        inputValidator: (value: string) => {
          if (!value || !value.trim()) return 'Reason is required.'
          if (value.trim().length < 8) return 'Please provide a more specific reason.'
          return true
        }
      }
    )

    accessRequestingDocId.value = documentId
    const result = await requestRagDocumentAccess({ documentId, reason: value })
    if (result.status === 'ALREADY_ACCESSIBLE') {
      ElMessage.info(result.message || 'You can already access this document.')
      await loadAccessibleDocuments()
    } else {
      ElMessage.success(result.message || 'Access request submitted.')
    }
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || 'Failed to submit access request.')
  } finally {
    accessRequestingDocId.value = null
  }
}

const documentTitle = (documentId: number) => {
  return documentStatusMap.value[documentId]?.title || `Document ${documentId}`
}

const citationTitle = (citation: RagCitation) => {
  return citation.documentTitle || documentTitle(citation.documentId)
}

const documentSecurityLevel = (documentId: number) => {
  return documentStatusMap.value[documentId]?.securityLevel
}

const documentPipelineStatus = (documentId: number) => {
  return documentStatusMap.value[documentId]?.pipelineStatus
}

const statusTagType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'RUNNING') return 'warning'
  if (status === 'QUEUED') return 'info'
  if (status === 'LLM_FALLBACK') return 'warning'
  if (status === 'NO_CONTEXT') return 'info'
  return ''
}

const formatScore = (score?: number) => {
  if (score === undefined || score === null || Number.isNaN(score)) return 'score n/a'
  return score.toFixed(3)
}

const levelClass = (level?: number) => {
  if (level === 3) return 'confidential'
  if (level === 2) return 'internal'
  return 'public'
}

const reasonLabel = (reason?: string) => {
  if (!reason) return ''
  const key = `rag.accessReasons.${reason}`
  const translated = t(key)
  return translated === key ? reason : translated
}

const reasonClass = (reason?: string) => {
  if (!reason) return ''
  if (reason === 'ROLE_ADMIN') return 'admin'
  if (reason === 'RAG_APPLY_APPROVED') return 'approved'
  if (reason === 'GLOBAL_CLEARANCE') return 'global'
  if (reason === 'DEPARTMENT_CLEARANCE') return 'dept'
  return ''
}

const pipelineClass = (status?: string) => {
  if (!status) return ''
  if (status === 'INDEXED') return 'approved'
  if (status === 'INDEXING' || status === 'PARSING' || status === 'PENDING_PARSE') return 'dept'
  if (status === 'READY_TO_INDEX') return 'global'
  if (status === 'FAILED' || status === 'EMPTY') return 'admin'
  return ''
}

const wait = (ms: number) => new Promise(resolve => window.setTimeout(resolve, ms))

const waitForIndexTask = async (taskId: number) => {
  for (let attempt = 0; attempt < 180; attempt += 1) {
    const task = await getRagIndexTask(taskId)
    if (task.status === 'SUCCESS' || task.status === 'FAIL') {
      return task
    }
    await loadTasks()
    await wait(2000)
  }
  throw new Error('RAG index rebuild is still running. Please refresh tasks later.')
}

const formatTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const citationKey = (citation: RagCitation) =>
  `${citation.documentId}-${citation.chunkId}-${citation.chunkIndex}`

onMounted(async () => {
  // Check for Copilot-passed RAG result first
  const copilotResult = localStorage.getItem('copilot_pending_rag_result')
  if (copilotResult) {
    try {
      const parsed = JSON.parse(copilotResult)
      if (parsed.result && parsed.query) {
        question.value = parsed.query
        response.value = parsed.result
        // Clear after reading so it doesn't show on next visit
        localStorage.removeItem('copilot_pending_rag_result')
        await refreshWorkspace()
        return
      }
    } catch { /* ignore corrupted data */ }
  }

  const initialQuery = route.query.query
  if (typeof initialQuery === 'string' && initialQuery.trim()) {
    question.value = initialQuery
  }
  await refreshWorkspace()
  if (question.value.trim()) {
    await handleQuery()
  }
})

</script>

<style scoped>
.rag-page {
  padding: 16px 0;
  max-width: 1200px;
  color: #111827;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 30px;
  padding-top: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 6px 0;
  letter-spacing: -0.5px;
}

.page-sub {
  font-size: 14px;
  color: #9ca3af;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-refresh {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
  display: inline-flex !important;
  align-items: center;
  gap: 6px;
}
.btn-refresh:hover { background: #f9fafb !important; border-color: #d1d5db !important; }

.btn-primary {
  background: #111827 !important;
  border: none !important;
  border-radius: 9px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
  display: inline-flex !important;
  align-items: center;
  gap: 6px;
}
.btn-primary:hover { background: #1f2937 !important; }

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 400px);
  gap: 18px;
  align-items: start;
}

.query-workspace,
.side-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* No accessible documents banner */
.no-access-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #92400e;
  line-height: 1.5;
}

.no-access-banner :deep(svg) {
  flex-shrink: 0;
  color: #f59e0b;
}

/* ── Index Rebuilding Banner ── */
.index-rebuilding-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #1e40af;
  line-height: 1.5;
  margin-bottom: 14px;
}

.index-rebuilding-banner :deep(svg) {
  flex-shrink: 0;
  color: #3b82f6;
}

/* ── Cards ── */
.premium-card {
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 14px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.card-header-simple {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.card-title-text {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.icon-sparkles { color: #4f46e5; }
.icon-database { color: #0f172a; }

/* ── Input ── */
.modern-input-container {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px 8px 10px 16px;
  transition: border-color 0.2s;
}
.modern-input-container:focus-within { border-color: #111827; }

.modern-textarea {
  width: 100%;
  border: none;
  resize: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: #0f172a;
  outline: none;
  background: transparent;
}
.modern-textarea::placeholder { color: #94a3b8; }

.modern-input-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 8px;
}
.footer-spacer { flex: 1; }

.modern-btn-generate {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 10px;
  background: #111827;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  height: 40px;
  padding: 0 24px;
}
.modern-btn-generate:hover { background: #1f2937; }
.modern-btn-generate:disabled { background: #e2e8f0; color: #94a3b8; cursor: not-allowed; }

/* ── TopK row ── */
.topk-row {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.topk-desc {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.topk-label {
  font-size: 12.5px;
  font-weight: 600;
  color: #475569;
}

.topk-hint {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
}

.topk-control {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.topk-slider {
  width: 120px;
}
.topk-slider :deep(.el-slider__runway) { height: 3px; background: #e2e8f0; }
.topk-slider :deep(.el-slider__bar) { height: 3px; background: #4f46e5; }
.topk-slider :deep(.el-slider__button) {
  width: 14px;
  height: 14px;
  border-color: #4f46e5;
  background: #4f46e5;
  box-shadow: 0 1px 3px rgba(79,70,229,0.15);
}

.topk-value {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  min-width: 20px;
  text-align: right;
}

/* ── Answer ── */
.suggested-question-block {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.suggested-question-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.suggested-question-head span {
  font-size: 12.5px;
  font-weight: 700;
  color: #334155;
}

.suggested-question-head small {
  font-size: 11px;
  color: #94a3b8;
  text-align: right;
}

.suggested-question-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.suggested-question {
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fff;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}

.suggested-question:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  transform: translateY(-1px);
}

.suggested-question:disabled {
  cursor: not-allowed;
  opacity: 0.65;
  transform: none;
}

.suggested-label {
  display: block;
  margin-bottom: 4px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  color: #64748b;
}

.suggested-text {
  display: block;
  font-size: 12px;
  line-height: 1.4;
  color: #1e293b;
}

.suggested-question.public .suggested-label { color: #15803d; }
.suggested-question.security .suggested-label { color: #4f46e5; }
.suggested-question.credit .suggested-label { color: #1d4ed8; }
.suggested-question.compliance .suggested-label { color: #0f766e; }
.suggested-question.restricted .suggested-label { color: #b45309; }

.answer-text {
  line-height: 1.72;
  font-size: 13.5px;
  color: #1e293b;
}

.answer-text :deep(h2) { font-size: 16px; font-weight: 700; color: #0f172a; margin: 18px 0 8px; }
.answer-text :deep(h3) { font-size: 14px; font-weight: 700; color: #1e293b; margin: 14px 0 6px; }
.answer-text :deep(p) { margin: 0 0 10px; }
.answer-text :deep(ul), .answer-text :deep(ol) { margin: 0 0 10px; padding-left: 20px; }
.answer-text :deep(li) { margin-bottom: 4px; }
.answer-text :deep(li)::marker { color: #64748b; }
.answer-text :deep(strong) { font-weight: 650; color: #0f172a; }
.answer-text :deep(code) { background: #f1f5f9; padding: 1px 5px; border-radius: 4px; font-size: 12px; color: #b45309; }
.answer-text :deep(em) { color: #475569; }
.answer-text :deep(blockquote) { border-left: 3px solid #94a3b8; padding-left: 12px; margin: 0 0 10px; color: #475569; }
.answer-text :deep(a) { color: #1d4ed8; }
.answer-text :deep(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 14px 0; }

/* ── Getting Started Empty State ── */
.getting-started {
  padding-top: 4px;
}

.getting-started p {
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
  margin: 0 0 12px;
}

.getting-started ol {
  padding-left: 20px;
  margin: 0 0 12px;
}

.getting-started li {
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
  margin-bottom: 6px;
}

.gs-hint {
  font-size: 12px !important;
  color: #94a3b8 !important;
  border-top: 1px solid #f1f5f9;
  padding-top: 10px;
}

.trace-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  font-size: 11px;
  color: #94a3b8;
}
.trace-row code { font-family: monospace; font-size: 11px; color: #64748b; }

.response-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ── Citations ── */
.citation-list { display: flex; flex-direction: column; gap: 8px; }
.citation-item {
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  background: #f8fafc;
}
.citation-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.citation-sub { margin: -2px 0 6px; font-size: 11.5px; color: #94a3b8; }
.citation-item p { margin: 0; font-size: 12.5px; color: #64748b; line-height: 1.5; }

/* ── Chunks ── */
.chunk-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.chunk-text {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* ── Blocked ── */
.blocked-list { display: flex; flex-direction: column; gap: 8px; }
.blocked-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
}
.blocked-item h3 { margin: 0 0 2px; font-size: 13px; font-weight: 600; color: #1e293b; }
.blocked-item p { margin: 0; font-size: 11px; color: #94a3b8; }

/* ── Sidebar ── */
.document-list { display: flex; flex-direction: column; gap: 6px; }
.document-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}
.document-item:hover { background: #f1f5f9; box-shadow: 0 1px 4px rgba(15,23,42,0.04); }

.docs-dialog-list { display: flex; flex-direction: column; gap: 6px; }
.dialog-doc-item { cursor: pointer; }
.document-main { display: flex; align-items: flex-start; gap: 8px; }
.document-main h3 { margin: 0 0 3px; font-size: 12.5px; font-weight: 600; color: #1e293b; }
.document-main p { margin: 0; font-size: 11px; color: #94a3b8; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }

/* Document tag row */
.doc-tags-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 0;
}

/* Level tags */
.level-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}
.level-tag.public { background: #f0fdf4; color: #15803d; }
.level-tag.internal { background: #fffbeb; color: #b45309; }
.level-tag.confidential { background: #fef2f2; color: #dc2626; }

.reason-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  background: #f1f5f9;
  color: #64748b;
  white-space: nowrap;
}
.reason-tag.admin { background: #f3e8ff; color: #7c3aed; }
.reason-tag.approved { background: #fef3c7; color: #b45309; }
.reason-tag.global { background: #f0fdf4; color: #15803d; }
.reason-tag.dept { background: #dbeafe; color: #1d4ed8; }

.view-all-link {
  display: block;
  background: none;
  border: none;
  padding: 8px 0 0;
  font-size: 12px;
  color: #4f46e5;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
}
.view-all-link:hover { color: #3730a3; }

.mini-refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  color: #94a3b8;
}
.mini-refresh-btn:hover { background: #f8fafc; color: #0f172a; }

.loading-state { padding: 20px 0; }
.side-loading { padding: 10px 0; }
.spin { animation: loading-spin 1.2s linear infinite; }

@keyframes loading-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.index-task-cards {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-card-item {
  background: #f9fafb;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: background 0.12s;
}

.task-card-item:hover {
  background: #f3f4f6;
}

.task-card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.task-card-type {
  font-size: 12.5px;
  font-weight: 600;
  color: #111827;
}

.task-card-time {
  font-size: 11px;
  color: #9ca3af;
}

@media (max-width: 960px) {
  .workspace-grid { grid-template-columns: 1fr; }
  .rag-page { padding: 12px; }
  .suggested-question-list { grid-template-columns: 1fr; }
  .suggested-question-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 3px;
  }
  .suggested-question-head small { text-align: left; }
}

.health-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.health-status-row strong {
  display: block;
  margin-top: 2px;
  color: #111827;
  font-size: 15px;
}

.health-label,
.health-grid span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.health-grid div {
  min-width: 0;
  border: 1px solid #edf0f6;
  border-radius: 8px;
  padding: 10px;
  background: #fbfcff;
}

.health-grid strong {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.health-message {
  margin: 12px 0 0;
  border-radius: 8px;
  padding: 10px;
  background: #f0fdf4;
  color: #166534;
  font-size: 12.5px;
  line-height: 1.5;
}

.health-message.unhealthy {
  background: #fef2f2;
  color: #b42318;
}
</style>
