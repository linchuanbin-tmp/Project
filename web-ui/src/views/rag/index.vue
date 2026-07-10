<template>
  <div class="rag-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ $t('rag.title') }}</h1>
        <p class="page-sub">{{ $t('rag.desc') }}</p>
      </div>
      <div class="header-actions">
        <el-button :loading="refreshing" @click="refreshWorkspace">
          <RefreshCw :size="14" :class="{ spin: refreshing }" />
          Refresh
        </el-button>
        <el-button type="primary" :loading="rebuilding" @click="handleRebuild">
          <RotateCcw :size="14" :class="{ spin: rebuilding }" />
          Rebuild Index
        </el-button>
      </div>
    </div>

    <div class="workspace-grid">
      <main class="query-workspace">
        <section class="query-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <MessageSquareText :size="17" />
              <span>Ask Knowledge Base</span>
            </div>
            <div class="topk-control">
              <span>TopK</span>
              <el-input-number v-model="topK" :min="1" :max="20" size="small" controls-position="right" />
            </div>
          </div>

          <el-input
            v-model="question"
            type="textarea"
            :rows="5"
            resize="none"
            placeholder="Ask about accessible enterprise documents..."
            class="question-input"
            @keydown.ctrl.enter.prevent="handleQuery"
          />

          <div class="query-actions">
            <el-button :disabled="!question.trim()" :loading="querying" type="primary" @click="handleQuery">
              <SendHorizontal :size="14" />
              Send
            </el-button>
            <el-button :disabled="!question.trim()" :loading="taskRunning" @click="handleRunAsTask">
              <Clock3 :size="14" />
              Run as Task
            </el-button>
            <el-button :disabled="querying && !answer" @click="clearQuery">
              <Eraser :size="14" />
              Clear
            </el-button>
          </div>

          <div v-if="taskRunning || taskProgress > 0" class="task-progress-box">
            <div class="task-progress-head">
              <span>{{ taskMessage || 'Preparing RAG task...' }}</span>
              <span>{{ taskProgress }}%</span>
            </div>
            <el-progress :percentage="taskProgress" :show-text="false" />
          </div>
        </section>

        <section class="answer-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <FileSearch :size="17" />
              <span>Answer</span>
            </div>
            <div v-if="response" class="response-meta">
              <el-tag size="small" :type="statusTagType(response.status)">{{ response.status }}</el-tag>
              <span>{{ response.latencyMs || 0 }}ms</span>
            </div>
          </div>

          <div v-if="querying" class="loading-state">
            <el-skeleton :rows="5" animated />
          </div>

          <template v-else-if="response">
            <div class="answer-text">{{ response.answer }}</div>
            <div class="trace-row">
              <span>Trace</span>
              <code>{{ response.traceId }}</code>
            </div>
          </template>

          <el-empty v-else description="No answer yet" />
        </section>

        <section v-if="citations.length" class="citations-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <Quote :size="17" />
              <span>Citations</span>
            </div>
            <el-tag size="small">{{ citations.length }}</el-tag>
          </div>

          <div class="citation-list">
            <article v-for="citation in citations" :key="citationKey(citation)" class="citation-item">
              <div class="citation-head">
                <span>Doc {{ citation.documentId }} · Chunk {{ citation.chunkIndex }}</span>
                <el-tag size="small" effect="plain">{{ formatScore(citation.score) }}</el-tag>
              </div>
              <p>{{ citation.snippet || 'No snippet returned.' }}</p>
            </article>
          </div>
        </section>

        <section v-if="chunks.length" class="chunks-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <Layers3 :size="17" />
              <span>Retrieved Chunks</span>
            </div>
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
        </section>
      </main>

      <aside class="side-workspace">
        <section class="side-panel access-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <ShieldCheck :size="17" />
              <span>Accessible Documents</span>
            </div>
            <el-tag size="small">{{ accessibleDocuments.length }}</el-tag>
          </div>

          <div v-if="documentsLoading" class="side-loading">
            <el-skeleton :rows="6" animated />
          </div>
          <el-empty v-else-if="!accessibleDocuments.length" description="No accessible documents" />
          <div v-else class="document-list">
            <article v-for="doc in accessibleDocuments" :key="doc.documentId" class="document-item">
              <div class="document-main">
                <BookOpen :size="16" />
                <div>
                  <h3>{{ doc.title || `Document ${doc.documentId}` }}</h3>
                  <p>Doc {{ doc.documentId }} · Level {{ doc.securityLevel || 1 }}</p>
                </div>
              </div>
              <div class="document-actions">
                <el-tag size="small" effect="plain">{{ doc.accessReason }}</el-tag>
                <el-button :loading="indexingDocId === doc.documentId" size="small" @click="handleIndexDocument(doc.documentId)">
                  <RefreshCw :size="13" :class="{ spin: indexingDocId === doc.documentId }" />
                </el-button>
              </div>
            </article>
          </div>
        </section>

        <section class="side-panel task-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <Clock3 :size="17" />
              <span>Index Tasks</span>
            </div>
            <el-button size="small" :loading="tasksLoading" @click="loadTasks">
              <RefreshCw :size="13" :class="{ spin: tasksLoading }" />
            </el-button>
          </div>

          <el-table v-if="indexTasks.length" :data="indexTasks" size="small" class="task-table">
            <el-table-column prop="id" label="ID" width="68" />
            <el-table-column prop="taskType" label="Type" min-width="126" show-overflow-tooltip />
            <el-table-column label="Status" width="96">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Updated" width="94">
              <template #default="{ row }">
                {{ formatTime(row.updateTime || row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="No index tasks" />
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  BookOpen,
  Clock3,
  Eraser,
  FileSearch,
  Layers3,
  MessageSquareText,
  Quote,
  RefreshCw,
  RotateCcw,
  SendHorizontal,
  ShieldCheck
} from 'lucide-vue-next'
import { getTask, submitTask } from '@api/task'
import {
  getAccessibleDocuments,
  getRagIndexTasks,
  indexRagDocument,
  queryRag,
  rebuildRagIndex,
  type AccessibleDocument,
  type RagCitation,
  type RagChunk,
  type RagIndexTask,
  type RagQueryResponse
} from '@api/rag'
import { wsClient } from '@utils/websocket'

const route = useRoute()

const question = ref('')
const topK = ref(5)
const querying = ref(false)
const refreshing = ref(false)
const rebuilding = ref(false)
const documentsLoading = ref(false)
const tasksLoading = ref(false)
const indexingDocId = ref<number | null>(null)
const taskRunning = ref(false)
const taskProgress = ref(0)
const taskMessage = ref('')
const taskPollTimer = ref<number | null>(null)

const response = ref<RagQueryResponse | null>(null)
const accessibleDocuments = ref<AccessibleDocument[]>([])
const indexTasks = ref<RagIndexTask[]>([])

const citations = computed<RagCitation[]>(() => response.value?.citations || [])
const chunks = computed<RagChunk[]>(() => response.value?.chunks || [])
const answer = computed(() => response.value?.answer || '')

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

const handleRunAsTask = async () => {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('Please enter a question.')
    return
  }

  taskRunning.value = true
  taskProgress.value = 0
  taskMessage.value = 'Submitting RAG task...'

  try {
    const submitted: any = await submitTask({ taskType: 'RAG', input: text })
    const taskId = submitted?.id ?? submitted?.data?.id
    if (!taskId) {
      throw new Error('Task service did not return task id.')
    }

    connectTaskProgress(taskId)
  } catch (error: any) {
    taskRunning.value = false
    taskMessage.value = ''
    ElMessage.error(error.message || 'Failed to submit RAG task.')
  }
}

const connectTaskProgress = (taskId: number) => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/task/progress?taskId=${taskId}`

  const onMessage = async (data: any) => {
    taskProgress.value = data.progress ?? taskProgress.value
    taskMessage.value = data.message || taskMessage.value

    if (data.status === 'completed') {
      await hydrateResponseFromTask(taskId)
      cleanupTaskSocket(onMessage, onError)
      taskRunning.value = false
      ElMessage.success('RAG task completed.')
    } else if (data.status === 'error') {
      cleanupTaskSocket(onMessage, onError)
      taskRunning.value = false
      ElMessage.error(data.message || 'RAG task failed.')
    }
  }

  const onError = () => {
    cleanupTaskSocket(onMessage, onError)
    taskRunning.value = false
    ElMessage.error('Task progress connection failed.')
  }

  wsClient.on('message', onMessage)
  wsClient.on('error', onError)
  wsClient.connect(wsUrl)
  startTaskPolling(taskId, onMessage, onError)
}

const cleanupTaskSocket = (onMessage: Function, onError: Function) => {
  if (taskPollTimer.value) {
    window.clearInterval(taskPollTimer.value)
    taskPollTimer.value = null
  }
  wsClient.off('message', onMessage)
  wsClient.off('error', onError)
  wsClient.close()
}

const startTaskPolling = (taskId: number, onMessage: Function, onError: Function) => {
  taskPollTimer.value = window.setInterval(async () => {
    try {
      const task: any = await getTask(taskId)
      const status = task?.status ?? task?.data?.status
      if (status === 'SUCCESS') {
        taskProgress.value = 100
        taskMessage.value = 'Task completed successfully.'
        await hydrateResponseFromTask(taskId)
        cleanupTaskSocket(onMessage, onError)
        taskRunning.value = false
      } else if (status === 'FAIL') {
        const errorMsg = task?.errorMsg ?? task?.data?.errorMsg ?? 'RAG task failed.'
        cleanupTaskSocket(onMessage, onError)
        taskRunning.value = false
        ElMessage.error(errorMsg)
      }
    } catch {
      // WebSocket remains the primary progress channel; transient polling errors are ignored.
    }
  }, 1800)
}

const hydrateResponseFromTask = async (taskId: number) => {
  const task: any = await getTask(taskId)
  const output = task?.output ?? task?.data?.output
  if (!output) return
  try {
    response.value = JSON.parse(output)
  } catch {
    response.value = {
      traceId: `task-${taskId}`,
      status: 'SUCCESS',
      answer: output,
      citations: [],
      chunks: [],
      retrievedDocumentIds: [],
      blockedDocumentIds: [],
      topK: topK.value,
      latencyMs: task?.elapsedTime ?? 0,
      message: 'Task output parsed as plain text.'
    }
  }
}

const clearQuery = () => {
  question.value = ''
  response.value = null
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
    await Promise.all([loadAccessibleDocuments(), loadTasks()])
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

const statusTagType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'RUNNING') return 'warning'
  if (status === 'NO_CONTEXT') return 'info'
  return ''
}

const formatScore = (score?: number) => {
  if (score === undefined || score === null || Number.isNaN(score)) return 'score n/a'
  return score.toFixed(3)
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
  const initialQuery = route.query.query
  if (typeof initialQuery === 'string' && initialQuery.trim()) {
    question.value = initialQuery
  }
  await refreshWorkspace()
  if (question.value.trim()) {
    await handleQuery()
  }
})

onUnmounted(() => {
  if (taskRunning.value) {
    wsClient.close()
  }
  if (taskPollTimer.value) {
    window.clearInterval(taskPollTimer.value)
  }
})
</script>

<style scoped>
.rag-page {
  min-height: calc(100vh - 56px);
  padding: 24px;
  background: #f6f7fb;
  color: #111827;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 750;
  line-height: 1.2;
  letter-spacing: 0;
}

.page-sub {
  margin: 6px 0 0;
  max-width: 760px;
  color: #667085;
  font-size: 14px;
  line-height: 1.55;
}

.header-actions,
.query-actions,
.document-actions,
.response-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

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

.query-panel,
.answer-panel,
.citations-panel,
.chunks-panel,
.side-panel {
  background: #ffffff;
  border: 1px solid #e6e8ef;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.panel-toolbar {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.toolbar-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
}

.topk-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #667085;
  font-size: 13px;
}

.question-input :deep(.el-textarea__inner) {
  min-height: 132px !important;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
}

.query-actions {
  justify-content: flex-end;
  margin-top: 12px;
}

.task-progress-box {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid #e8edf7;
  border-radius: 8px;
  background: #f8fbff;
}

.task-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #475467;
  font-size: 12.5px;
}

.answer-panel {
  min-height: 260px;
}

.answer-text {
  white-space: pre-wrap;
  line-height: 1.72;
  font-size: 14.5px;
  color: #1f2937;
}

.trace-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid #eef0f5;
  color: #667085;
  font-size: 12px;
}

.trace-row code {
  min-width: 0;
  color: #344054;
  overflow-wrap: anywhere;
}

.loading-state,
.side-loading {
  padding: 8px 0;
}

.citation-list,
.document-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citation-item,
.document-item {
  border: 1px solid #edf0f6;
  border-radius: 8px;
  background: #fbfcff;
  padding: 12px;
}

.citation-head,
.document-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.citation-head {
  align-items: center;
  color: #344054;
  font-size: 13px;
  font-weight: 650;
}

.citation-item p,
.chunk-text {
  margin: 8px 0 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.chunk-collapse {
  border-top: 1px solid #eef0f5;
}

.chunk-title {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 12px;
  color: #344054;
  font-size: 13px;
}

.document-main {
  justify-content: flex-start;
}

.document-main svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #2563eb;
}

.document-main h3 {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.4;
  font-weight: 700;
  color: #1f2937;
}

.document-main p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
}

.document-actions {
  justify-content: space-between;
  margin-top: 10px;
}

.task-table {
  width: 100%;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .side-workspace {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .rag-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
  }

  .header-actions,
  .side-workspace {
    width: 100%;
    display: flex;
    flex-direction: column;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
