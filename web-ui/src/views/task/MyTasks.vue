<template>
  <div class="my-tasks-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('task.pageTitle') }}</h1>
        <p class="page-sub">{{ $t('task.pageSub') }}</p>
      </div>
      <el-button class="refresh-btn" @click="fetchTasks" :loading="loading">
        <RefreshCw :size="14" :class="{ 'spin': loading }" />
        {{ $t('task.refresh') }}
      </el-button>
    </div>

    <!-- Main Content Tabs (Border-card style) -->
    <el-tabs v-model="filterStatus" class="tasks-tabs" type="border-card">
      <el-tab-pane
        v-for="statusOpt in statusOptions"
        :key="statusOpt.value"
        :name="statusOpt.value"
      >
        <template #label>
          <div class="tab-label">
            <span v-if="statusOpt.dotClass" class="tab-dot" :class="statusOpt.dotClass"></span>
            <span>{{ statusOpt.label }}</span>
          </div>
        </template>

        <!-- Loading skeleton -->
        <div v-if="loading" class="task-list">
          <div v-for="i in 3" :key="i" class="task-item skeleton-item">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="text" style="width: 20%; height: 16px;" />
                <el-skeleton-item variant="text" style="width: 70%; margin-top: 12px; height: 14px;" />
                <el-skeleton-item variant="text" style="width: 40%; margin-top: 10px; height: 12px;" />
              </template>
            </el-skeleton>
          </div>
        </div>

        <!-- Empty state -->
        <div v-else-if="filteredTasks.length === 0" class="empty-state">
          <div class="empty-icon">
            <ClipboardList :size="48" class="empty-icon-svg" />
          </div>
          <p class="empty-text">{{ $t('task.noTasks') }}</p>
        </div>

        <!-- Task items list -->
        <div v-else class="task-list">
          <div
            v-for="task in filteredTasks"
            :key="task.id"
            class="task-item"
            @click="openDetail(task)"
          >
            <div class="task-item-header">
              <div class="task-type-badge" :class="'type-' + task.taskType.toLowerCase()">
                <component :is="typeIcon(task.taskType)" :size="12" />
                <span>{{ formatType(task.taskType) }}</span>
              </div>
              <el-tag
                :type="statusType(task.status)"
                size="small"
                class="status-tag"
                :class="{ 'pulsing': task.status === 'RUNNING' || task.status === 'INIT' }"
              >
                <el-icon v-if="task.status === 'RUNNING' || task.status === 'INIT'" class="is-loading" style="margin-right:4px;">
                  <Loading />
                </el-icon>
                {{ statusLabel(task.status) }}
              </el-tag>
            </div>

            <p class="task-input" :title="task.input">{{ task.input }}</p>

            <div class="task-meta">
              <span class="meta-item">
                <Clock :size="12" />
                {{ formatTime(task.createdAt) }}
              </span>
              <span v-if="task.elapsedTime" class="meta-item">
                <Zap :size="12" />
                {{ formatElapsed(task.elapsedTime) }}
              </span>
              <span class="view-link">
                {{ $t('task.viewResult') }}
                <ChevronRight :size="13" />
              </span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Detail Dialog (Modern Split Layout) -->
    <el-dialog
      v-model="dialogVisible"
      :title="$t('task.resultDrawerTitle')"
      width="880px"
      align-center
      class="task-detail-dialog"
    >
      <template v-if="selectedTask">
        <div class="dialog-split-container">
          <!-- Left Panel: Meta & Input -->
          <div class="split-left-panel">
            <div class="dialog-meta-section">
              <div class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.taskId') }}</span>
                <div class="dialog-meta-value-wrap">
                  <span class="dialog-meta-value mono">#{{ selectedTask.id }}</span>
                  <el-button text size="small" @click="copyText(String(selectedTask.id))" class="copy-btn">
                    <Copy :size="12" />
                  </el-button>
                </div>
              </div>
              <div class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.taskType') }}</span>
                <div class="task-type-badge inline" :class="'type-' + selectedTask.taskType.toLowerCase()">
                  <component :is="typeIcon(selectedTask.taskType)" :size="12" />
                  <span>{{ formatType(selectedTask.taskType) }}</span>
                </div>
              </div>
              <div class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.status') }}</span>
                <el-tag :type="statusType(selectedTask.status)" size="small">{{ statusLabel(selectedTask.status) }}</el-tag>
              </div>
              <div class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.createdAt') }}</span>
                <span class="dialog-meta-value">{{ formatTime(selectedTask.createdAt) }}</span>
              </div>
              <div v-if="selectedTask.elapsedTime" class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.elapsedTime') }}</span>
                <span class="dialog-meta-value">{{ formatElapsed(selectedTask.elapsedTime) }}</span>
              </div>
            </div>

            <el-divider class="dialog-divider" />

            <div class="dialog-section">
              <div class="section-label">{{ $t('task.input') }}</div>
              <div class="content-box input-box">{{ selectedTask.input }}</div>
            </div>
          </div>

          <!-- Right Panel: Output or Error Message -->
          <div class="split-right-panel">
            <template v-if="selectedTask.status === 'SUCCESS' && selectedTask.output">
              <div class="dialog-section flex-column-grow">
                <div class="section-label-row">
                  <span class="section-label">{{ $t('task.output') }}</span>
                  <el-button text size="small" @click="copyText(selectedTask.output)" class="copy-output-btn">
                    <Copy :size="12" style="margin-right: 4px;" />
                    {{ $t('task.copyOutput') }}
                  </el-button>
                </div>
                <div class="content-box output-box">
                  <pre class="output-pre">{{ formatOutput(selectedTask.output) }}</pre>
                </div>
              </div>
            </template>

            <template v-else-if="selectedTask.status === 'FAIL' && selectedTask.errorMsg">
              <div class="dialog-section flex-column-grow">
                <div class="section-label error-label">{{ $t('task.errorMsg') }}</div>
                <div class="content-box error-box">
                  {{ selectedTask.errorMsg }}
                </div>
              </div>
            </template>

            <template v-else>
              <div class="dialog-section flex-column-grow empty-output-section">
                <div class="section-label">{{ $t('task.output') }}</div>
                <div class="content-box running-box">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>任务处理中，暂无输出结果...</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  RefreshCw, ClipboardList, Clock, Zap, ChevronRight,
  Copy, Code2, Wrench, BookOpen
} from 'lucide-vue-next'
import { getMyTasks, type TaskRecord } from '@/api/task'

const { t } = useI18n()

const loading = ref(false)
const tasks = ref<TaskRecord[]>([])
const filterStatus = ref('')
const dialogVisible = ref(false)
const selectedTask = ref<TaskRecord | null>(null)

const statusOptions = computed(() => [
  { value: '', label: t('task.all'), dotClass: '' },
  { value: 'RUNNING', label: t('task.running'), dotClass: 'running' },
  { value: 'SUCCESS', label: t('task.success'), dotClass: 'success' },
  { value: 'FAIL', label: t('task.fail'), dotClass: 'fail' }
])

const filteredTasks = computed(() => {
  if (!filterStatus.value) return tasks.value
  return tasks.value.filter(t => t.status === filterStatus.value)
})

const fetchTasks = async () => {
  loading.value = true
  try {
    const res: any = await getMyTasks()
    if (Array.isArray(res)) {
      tasks.value = res
    } else {
      tasks.value = res?.data?.data ?? res?.data ?? []
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const applyFilter = () => {
  // computed handles re-render; no extra logic needed
}

const openDetail = (task: TaskRecord) => {
  selectedTask.value = task
  dialogVisible.value = true
}

const statusType = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: 'success',
    FAIL: 'danger',
    RUNNING: 'warning',
    INIT: 'info',
  }
  return map[status] ?? 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: t('task.success'),
    FAIL: t('task.fail'),
    RUNNING: t('task.running'),
    INIT: t('task.init'),
  }
  return map[status] ?? status
}

const typeIcon = (type: string) => {
  const map: Record<string, any> = { CODE: Code2, TOOL: Wrench, RAG: BookOpen }
  return map[type] ?? ClipboardList
}

const formatType = (type: string) => {
  const map: Record<string, string> = { CODE: 'Code', TOOL: 'Tool', RAG: 'RAG' }
  return map[type] ?? type
}

const formatTime = (ts: string) => {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}

const formatElapsed = (ms: number) => {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const formatOutput = (raw: string) => {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

const copyText = async (text: string) => {
  await navigator.clipboard.writeText(text)
  ElMessage.success(t('task.copySuccess'))
}

onMounted(fetchTasks)
</script>

<style scoped>
.my-tasks-container {
  max-width: 1200px;
  padding: 16px 0;
  min-height: 100%;
}

/* Header */
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
.refresh-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }


/* Custom Tabs Styling */
:deep(.el-tabs--border-card) {
  background: #ffffff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.015);
  overflow: hidden;
}
:deep(.el-tabs--border-card > .el-tabs__header) {
  background-color: #ffffff; /* Modern pure white background */
  border-bottom: 1px solid #f3f4f6; /* Super faint modern divider */
  padding: 0 16px;
  height: 56px; /* Increased height for spacious breathing room */
  display: flex;
  align-items: center;
}
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) {
  color: #6b7280;
  font-weight: 500;
  font-size: 13.5px;
  height: 56px;
  line-height: 56px;
  transition: all 0.2s;
  border: none !important;
  margin: 0 4px;
  border-bottom: 2px solid transparent !important;
}
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  color: #111827;
  background-color: transparent !important;
  font-weight: 600;
  border-bottom: 2px solid #111827 !important;
}
:deep(.el-tabs--border-card > .el-tabs__content) {
  padding: 0;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tab-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  vertical-align: middle;
}
.tab-dot.running { background: #e6a23c; }
.tab-dot.success { background: #67c23a; }
.tab-dot.fail    { background: #f56c6c; }

/* Task list */
.task-list {
  display: flex;
  flex-direction: column;
}

/* Task item */
.task-item {
  padding: 18px 20px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background-color 0.2s ease;
}
.task-item:hover {
  background-color: #f9fafb;
}
.task-item:last-child {
  border-bottom: none;
}
.skeleton-item {
  cursor: default;
}
.skeleton-item:hover {
  background-color: transparent;
}

/* Card header */
.task-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

/* Type badge */
.task-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.task-type-badge.inline { font-size: 11px; }
.type-code { background: #e0e7ff; color: #4338ca; }
.type-tool { background: #f3e8ff; color: #7e22ce; }
.type-rag  { background: #fce7f3; color: #be185d; }

/* Status tag */
.status-tag { font-size: 11px; }
.pulsing { animation: pulse 1.6s ease-in-out infinite; }
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.55; }
}

/* Input text */
.task-input {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

/* Meta row */
.task-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 3px;
}
.view-link {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  color: var(--el-color-primary);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.view-link:hover { opacity: 0.75; }

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--el-text-color-secondary);
}
.empty-icon-svg { opacity: 0.2; }
.empty-text { margin-top: 16px; font-size: 14px; }

/* Detail Dialog Modern Styling */
:deep(.task-detail-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 12px 36px rgba(0,0,0,0.1);
  padding: 0;
}

:deep(.task-detail-dialog .el-dialog__header) {
  margin: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f3f4f6;
  background-color: #fafafa;
}

:deep(.task-detail-dialog .el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

:deep(.task-detail-dialog .el-dialog__body) {
  padding: 24px;
}

/* Split Pane Layout */
.dialog-split-container {
  display: flex;
  gap: 28px;
  min-height: 400px;
  max-height: 62vh;
}

.split-left-panel {
  flex: 4; /* 40% width */
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f3f4f6;
  padding-right: 28px;
  overflow-y: auto;
}

.split-right-panel {
  flex: 6; /* 60% width */
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  min-width: 0; /* Prevents pre flex overflow */
}

/* Meta Data section */
.dialog-meta-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dialog-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.dialog-meta-label {
  color: var(--el-text-color-secondary);
}

.dialog-meta-value-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dialog-meta-value {
  color: var(--el-text-color-primary);
}

.dialog-meta-value.mono {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-weight: 500;
}

.dialog-divider {
  margin: 16px 0;
  border-color: #f3f4f6;
}

/* Dialog content sections */
.dialog-section {
  display: flex;
  flex-direction: column;
  margin-bottom: 0;
}

.flex-column-grow {
  flex-grow: 1;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.section-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  height: 24px;
}

.section-label-row .section-label {
  margin-bottom: 0;
}

.error-label {
  color: var(--el-color-danger);
}

/* Content box blocks */
.content-box {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-word;
}

.input-box {
  background: #f9fafb;
  max-height: 220px;
  overflow-y: auto;
}

.output-box {
  background: #1e1e2e;
  border-color: #2d2d3f;
  flex-grow: 1;
  overflow-y: auto;
  max-height: 420px;
  display: flex;
}

.output-pre {
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #cdd6f4;
  white-space: pre-wrap;
  word-break: break-all;
  flex-grow: 1;
}

.error-box {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  flex-grow: 1;
}

.running-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  background: #fafafa;
  flex-grow: 1;
  min-height: 200px;
}

.copy-btn {
  padding: 4px !important;
  height: 24px !important;
}

.copy-output-btn {
  font-size: 12px !important;
  padding: 0 !important;
  height: auto !important;
}
</style>
