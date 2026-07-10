<template>
  <div class="task-center-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('task.adminPageTitle') }}</h1>
        <p class="page-sub">{{ $t('task.adminPageSub') }}</p>
      </div>
      <el-button class="refresh-btn" @click="fetchTasks" :loading="loading">
        <RefreshCw :size="14" :class="{ 'spin': loading }" />
        {{ $t('task.refresh') }}
      </el-button>
    </div>

    <!-- Stats Bar -->
    <div class="stats-bar">
      <div class="stat-card" v-for="stat in statsCards" :key="stat.label">
        <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </div>
    </div>

    <!-- Filters -->
    <div class="filter-container">
      <el-select
        v-model="filterType"
        :placeholder="$t('task.taskType')"
        clearable
        class="filter-select"
        @change="applyFilter"
      >
        <el-option value="CODE" label="CODE" />
        <el-option value="TOOL" label="TOOL" />
        <el-option value="RAG" label="RAG" />
      </el-select>

      <el-select
        v-model="filterStatus"
        :placeholder="$t('task.status')"
        clearable
        class="filter-select"
        @change="applyFilter"
      >
        <el-option value="INIT" :label="$t('task.init')" />
        <el-option value="RUNNING" :label="$t('task.running')" />
        <el-option value="SUCCESS" :label="$t('task.success')" />
        <el-option value="FAIL" :label="$t('task.fail')" />
      </el-select>

      <span class="result-count">{{ $t('task.totalRecords', { count: filteredTasks.length }) }}</span>
    </div>

    <!-- Table -->
    <el-card class="table-card" shadow="never">
      <el-table
        :data="filteredTasks"
        v-loading="loading"
        row-class-name="task-row"
        @row-click="openDetail"
        class="task-table"
        :empty-text="$t('task.noTasks')"
      >
        <el-table-column prop="id" :label="$t('task.taskId')" width="90">
          <template #default="{ row }">
            <span class="mono text-secondary">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="taskType" :label="$t('task.taskType')" width="90">
          <template #default="{ row }">
            <div class="task-type-badge" :class="'type-' + row.taskType.toLowerCase()">
              <component :is="typeIcon(row.taskType)" :size="11" />
              <span>{{ formatType(row.taskType) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="status" :label="$t('task.status')" width="110">
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              size="small"
              :class="{ 'pulsing': row.status === 'RUNNING' || row.status === 'INIT' }"
            >
              <el-icon v-if="row.status === 'RUNNING'" class="is-loading" style="margin-right:3px">
                <Loading />
              </el-icon>
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="userId" :label="$t('task.userId')" width="100">
          <template #default="{ row }">
            <span class="mono text-secondary">{{ row.userId }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="input" :label="$t('task.input')" min-width="200">
          <template #default="{ row }">
            <span class="input-ellipsis" :title="row.input">{{ row.input }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="elapsedTime" :label="$t('task.elapsedTime')" width="90">
          <template #default="{ row }">
            <span v-if="row.elapsedTime" class="elapsed">{{ formatElapsed(row.elapsedTime) }}</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" :label="$t('task.createdAt')" width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column width="80">
          <template #default>
            <span class="view-link">详情 <ChevronRight :size="13" /></span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
                <span class="dialog-meta-label">{{ $t('task.userId') }}</span>
                <span class="dialog-meta-value mono">{{ selectedTask.userId }}</span>
              </div>
              <div class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.createdAt') }}</span>
                <span class="dialog-meta-value">{{ formatTime(selectedTask.createdAt) }}</span>
              </div>
              <div v-if="selectedTask.elapsedTime" class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.elapsedTime') }}</span>
                <span class="dialog-meta-value">{{ formatElapsed(selectedTask.elapsedTime) }}</span>
              </div>
              <div class="dialog-meta-row">
                <span class="dialog-meta-label">{{ $t('task.attemptCount') }}</span>
                <span class="dialog-meta-value">{{ selectedTask.attemptCount }}</span>
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
  RefreshCw, ChevronRight, Copy, Code2, Wrench, BookOpen, ClipboardList
} from 'lucide-vue-next'
import { getAllTasks, type TaskRecord } from '@/api/task'

const { t } = useI18n()

const loading = ref(false)
const tasks = ref<TaskRecord[]>([])
const filterType = ref('')
const filterStatus = ref('')
const dialogVisible = ref(false)
const selectedTask = ref<TaskRecord | null>(null)

const filteredTasks = computed(() => {
  return tasks.value.filter(task => {
    const typeMatch = !filterType.value || task.taskType === filterType.value
    const statusMatch = !filterStatus.value || task.status === filterStatus.value
    return typeMatch && statusMatch
  })
})

const statsCards = computed(() => {
  const total = tasks.value.length
  const running = tasks.value.filter(t => t.status === 'RUNNING' || t.status === 'INIT').length
  const success = tasks.value.filter(t => t.status === 'SUCCESS').length
  const fail = tasks.value.filter(t => t.status === 'FAIL').length
  return [
    { label: t('task.all'), value: total, color: 'var(--el-color-primary)' },
    { label: t('task.running'), value: running, color: '#e6a23c' },
    { label: t('task.success'), value: success, color: '#67c23a' },
    { label: t('task.fail'), value: fail, color: '#f56c6c' },
  ]
})


const fetchTasks = async () => {
  loading.value = true
  try {
    const res: any = await getAllTasks()
    tasks.value = Array.isArray(res) ? res : []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const applyFilter = () => { /* reactive computed handles filtering */ }

const openDetail = (row: TaskRecord) => {
  selectedTask.value = row
  dialogVisible.value = true
}

const statusType = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: 'success', FAIL: 'danger', RUNNING: 'warning', INIT: 'info'
  }
  return map[status] ?? 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: t('task.success'), FAIL: t('task.fail'),
    RUNNING: t('task.running'), INIT: t('task.init'),
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
  try { return JSON.stringify(JSON.parse(raw), null, 2) }
  catch { return raw }
}

const copyText = async (text: string) => {
  await navigator.clipboard.writeText(text)
  ElMessage.success(t('task.copySuccess'))
}

onMounted(fetchTasks)
</script>

<style scoped>
.task-center-container {
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
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }


/* Stats bar */
.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px 20px;
  text-align: center;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 6px;
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* Filters */
.filter-container {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.filter-select { width: 130px; }
.result-count {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* Table card */
.table-card { border-radius: 12px; }
:deep(.task-row) { cursor: pointer; }
:deep(.task-row:hover td) { background: var(--el-fill-color-light) !important; }

/* Type badge */
.task-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.type-code { background: #e0e7ff; color: #4338ca; }
.type-tool { background: #f3e8ff; color: #7e22ce; }
.type-rag  { background: #fce7f3; color: #be185d; }

/* Status tag */
.pulsing { animation: pulse 1.6s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:.55; } }

/* Table cell helpers */
.mono { font-family: monospace; }
.text-secondary { color: var(--el-text-color-secondary); }
.input-ellipsis {
  display: block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.elapsed { font-size: 13px; color: var(--el-color-primary); }
.view-link {
  display: flex;
  align-items: center;
  gap: 2px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

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
