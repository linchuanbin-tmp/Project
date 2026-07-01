<template>
  <div class="sql-generator-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">SQL Generator</h1>
        <p class="page-sub">
          Describe what data you need in plain Chinese or English, and the AI agent will generate and execute the MySQL query.
        </p>
      </div>
      <div class="header-right">
        <el-button 
          :icon="RefreshCw" 
          @click="refreshMetadata" 
          :loading="metadataLoading" 
          size="small" 
          class="btn-refresh"
        >
          Sync Schema
        </el-button>
      </div>
    </div>

    <!-- Main Content Area: Left Input, Right Schema/Metadata -->
    <div class="main-layout">
      <!-- Left side: Prompt Editor & Results -->
      <div class="editor-section">
        <!-- Input Card -->
        <el-card class="editor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">
                <Sparkles :size="16" class="icon-sparkles" />
                AI SQL Generation Prompt
              </span>
              <el-switch
                v-model="executeDirectly"
                active-text="Execute SQL"
                inactive-text="Generate Only"
                inline-prompt
                class="execute-switch"
              />
            </div>
          </template>

          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            placeholder="e.g. 统计每个账户类型的平均余额, or 查询余额大于50000的所有客户列表..."
            resize="none"
            class="prompt-textarea"
          />

          <!-- Quick Templates -->
          <div class="templates-section">
            <span class="label-text">Quick Prompts:</span>
            <div class="template-tags">
              <span
                v-for="tmpl in quickTemplates"
                :key="tmpl"
                class="template-tag"
                @click="useTemplate(tmpl)"
              >
                {{ tmpl }}
              </span>
            </div>
          </div>

          <div class="card-actions">
            <el-button
              type="primary"
              :loading="queryLoading"
              class="btn-generate"
              @click="handleRun"
            >
              <Play :size="14" class="btn-icon" /> Run SQL Generation
            </el-button>
          </div>
        </el-card>

        <!-- Result Card -->
        <el-card v-if="result || error" class="result-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">
                <Database :size="16" class="icon-database" />
                Generation Results
              </span>
              <div v-if="result && result.success" class="result-actions">
                <el-button
                  v-if="result.rows && result.rows.length"
                  size="small"
                  @click="exportCSV"
                  class="btn-export"
                >
                  <FileSpreadsheet :size="14" style="margin-right: 4px;" />
                  Export CSV
                </el-button>
                <el-button size="small" @click="copySQL" class="btn-copy">
                  <component :is="copied ? Check : Copy" :size="12" style="margin-right: 4px;" />
                  {{ copied ? 'Copied' : 'Copy SQL' }}
                </el-button>
              </div>
            </div>
          </template>

          <!-- Error Display -->
          <el-alert
            v-if="error || (result && !result.success)"
            type="error"
            :title="error || result.errorMessage || 'Generation Failed'"
            show-icon
            :closable="false"
            class="result-alert"
          >
            <template #default>
              <p class="error-detail">
                The AI agent might have encountered a safety check restriction, an empty response, or database validation failure.
              </p>
            </template>
          </el-alert>

          <!-- Success Display -->
          <div v-else-if="result && result.success">
            <!-- SQL Code Block -->
            <div class="sql-code-block">
              <pre><code>{{ result.sql }}</code></pre>
            </div>

            <!-- Stats Bar -->
            <div class="stats-bar-inner">
              <div class="stat-item">
                <span class="stat-label">Inference Mode:</span>
                <el-tag size="small" class="tag-inference">{{ result.inferenceMethod }}</el-tag>
              </div>
              <div class="stat-item" v-if="result.whitelistPassed !== null">
                <span class="stat-label">Safety Whitelist:</span>
                <el-tag 
                  size="small" 
                  :type="result.whitelistPassed ? 'success' : 'danger'" 
                  effect="plain"
                >
                  {{ result.whitelistPassed ? 'Passed' : 'Failed' }}
                </el-tag>
              </div>
              <div class="stat-item" v-if="result.rowCount !== undefined">
                <span class="stat-label">Row Count:</span>
                <span class="stat-value">{{ result.rowCount }}</span>
              </div>
            </div>

            <!-- Results Table -->
            <div v-if="result.rows && result.rows.length" class="table-container">
              <el-table :data="result.rows" style="width: 100%" border max-height="350" stripe>
                <el-table-column
                  v-for="col in result.columns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  show-overflow-tooltip
                />
              </el-table>
            </div>
            <div v-else-if="executeDirectly" class="empty-rows-message">
              Query executed successfully, but returned 0 rows.
            </div>
          </div>
        </el-card>
      </div>

      <!-- Right Column: Database Schema/Metadata -->
      <div class="schema-section">
        <el-card class="schema-card" shadow="never">
          <template #header>
            <div class="card-header-schema">
              <span class="card-title-text-schema">
                <Database :size="16" class="icon-db-list" />
                Available Schema
              </span>
            </div>
          </template>

          <div v-if="metadataLoading" class="loading-schema">
            <el-skeleton :rows="6" animated />
          </div>
          <div v-else-if="!tables.length" class="empty-schema">
            No tables cached. Click <a @click="refreshMetadata" class="refresh-link">Sync Schema</a> to pull from MySQL.
          </div>
          <div v-else class="tables-list">
            <div v-for="table in tables" :key="table" class="table-schema-item">
              <span class="table-name-tag">
                <code>{{ table }}</code>
              </span>
            </div>
            <div class="schema-footer-note">
              Only SELECT queries covering these whitelisted tables are permitted.
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Sparkles, Database, Play, RefreshCw, Copy, Check, FileSpreadsheet } from 'lucide-vue-next'
import { 
  executeCodeQuery, 
  generateSQLOnly, 
  getCodeMetadata, 
  refreshCodeMetadata 
} from '@api/code'

// Quick prompt templates for banker Text-to-SQL
const quickTemplates = [
  '查询所有客户',
  '统计每个账户类型的平均余额',
  '查询2026年交易金额大于10000的交易流水',
  '找出风险等级为HIGH的客户名单',
  '统计各部门的员工人数'
]

const tables = ref<string[]>([])
const metadataLoading = ref(false)
const question = ref('')
const executeDirectly = ref(true)
const queryLoading = ref(false)
const result = ref<any>(null)
const error = ref('')
const copied = ref(false)

// Fetch table names in schema
const fetchMetadata = async () => {
  metadataLoading.value = true
  try {
    const res = await getCodeMetadata()
    const payload = res?.data ?? res
    tables.value = payload.tableNames || []
  } catch (e: any) {
    console.error('Failed to load metadata:', e)
  } finally {
    metadataLoading.value = false
  }
}

// Refresh table schema from mysql
const refreshMetadata = async () => {
  metadataLoading.value = true
  try {
    const res = await refreshCodeMetadata()
    const payload = res?.data ?? res
    tables.value = payload.tableNames || []
    ElMessage.success('Schema database synchronized!')
  } catch (e: any) {
    ElMessage.error('Failed to synchronize schema database')
    console.error(e)
  } finally {
    metadataLoading.value = false
  }
}

const useTemplate = (tmpl: string) => {
  question.value = tmpl
}

// Run prompt to generate SQL
const handleRun = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('Please enter a query prompt')
    return
  }
  queryLoading.value = true
  result.value = null
  error.value = ''
  try {
    let res
    if (executeDirectly.value) {
      res = await executeCodeQuery({ question: question.value })
    } else {
      res = await generateSQLOnly({ question: question.value })
    }
    const payload = res?.data ?? res
    result.value = payload
    if (payload && !payload.success) {
      error.value = payload.errorMessage || 'Failed to generate SQL'
    }
  } catch (e: any) {
    error.value = e.message || 'Server error occurred during query execution'
    ElMessage.error('Execution failed')
  } finally {
    queryLoading.value = false
  }
}

// Copy query SQL to clipboard
const copySQL = () => {
  if (result.value && result.value.sql) {
    navigator.clipboard.writeText(result.value.sql)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
    ElMessage.success('SQL copied to clipboard!')
  }
}

// Export execution rows to CSV format
const exportCSV = () => {
  if (!result.value || !result.value.rows || !result.value.rows.length) return
  const cols = result.value.columns
  const rows = result.value.rows
  
  let csvContent = '\uFEFF' // Add BOM for Chinese character support in Excel
  csvContent += cols.join(',') + '\n'
  
  rows.forEach((row: any) => {
    const rowStr = cols.map((c: string) => {
      let val = row[c] ?? ''
      if (typeof val === 'object') {
        val = JSON.stringify(val)
      }
      val = String(val).replace(/"/g, '""')
      return `"${val}"`
    }).join(',')
    csvContent += rowStr + '\n'
  })
  
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.setAttribute('download', `sql_result_${Date.now()}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  ElMessage.success('CSV exported successfully!')
}

onMounted(() => {
  fetchMetadata()
})
</script>

<style scoped>
.sql-generator-container {
  padding: 8px 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 16px;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 24px;
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

.btn-refresh {
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  color: #374151;
  font-weight: 500;
  transition: all 0.15s;
}

.btn-refresh:hover {
  background: #f9fafb;
  border-color: #d1d5db;
}

/* ── Layout Grid ── */
.main-layout {
  display: grid;
  grid-template-columns: 3fr 1fr;
  gap: 20px;
}

@media (max-width: 992px) {
  .main-layout {
    grid-template-columns: 1fr;
  }
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── Editor Card ── */
.editor-card, .result-card, .schema-card {
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.015);
  background: #ffffff;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.icon-sparkles {
  color: #3b82f6;
}

.icon-database {
  color: #10b981;
}

.execute-switch :deep(.el-switch__label) {
  font-size: 12px;
  color: #6b7280;
}

.execute-switch :deep(.el-switch__label.is-active) {
  color: #111827;
  font-weight: 500;
}

.prompt-textarea :deep(.el-textarea__inner) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 13.5px;
  padding: 12px;
  line-height: 1.5;
  transition: all 0.15s;
}

.prompt-textarea :deep(.el-textarea__inner:focus) {
  border-color: #111827;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08) !important;
}

/* ── Quick Templates ── */
.templates-section {
  margin-top: 14px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.label-text {
  font-size: 12.5px;
  font-weight: 500;
  color: #6b7280;
  margin-top: 4px;
  flex-shrink: 0;
}

.template-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-tag {
  background: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.template-tag:hover {
  background: #ffffff;
  border-color: #d1d5db;
  color: #111827;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.03);
}

.card-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.btn-generate :deep(.el-button--primary), :deep(.el-button--primary) {
  background-color: #111827 !important;
  border: none !important;
  border-radius: 10px !important;
  height: 40px;
  font-weight: 500;
  padding: 10px 20px;
  transition: all 0.15s;
}

:deep(.el-button--primary:hover) {
  opacity: 0.88;
  transform: translateY(-1px);
}

:deep(.el-button--primary:active) {
  transform: translateY(0);
}

.btn-icon {
  margin-right: 6px;
}

/* ── Result Display ── */
.result-actions {
  display: flex;
  gap: 8px;
}

.btn-export, .btn-copy {
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.result-alert {
  border-radius: 10px;
  margin-bottom: 16px;
}

.error-detail {
  font-size: 12.5px;
  color: #ef4444;
  margin-top: 4px;
}

.sql-code-block {
  background: #1e1e2e;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  overflow-x: auto;
  border: 1px solid #2d2d3d;
}

.sql-code-block code {
  font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13.5px;
  color: #cdd6f4;
  white-space: pre-wrap;
  word-break: break-all;
}

/* ── Stats Bar ── */
.stats-bar-inner {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 10px;
  margin-bottom: 16px;
  border: 1px solid #f0f0f0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
}

.stat-label {
  color: #6b7280;
  font-weight: 500;
}

.stat-value {
  color: #111827;
  font-weight: 600;
}

.tag-inference {
  background-color: #eff6ff !important;
  color: #1d4ed8 !important;
  border-color: #bfdbfe !important;
}

.table-container {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f9fafb;
  color: #374151;
  font-weight: 600;
}

.empty-rows-message {
  text-align: center;
  padding: 24px;
  color: #6b7280;
  font-size: 13.5px;
  background: #f9fafb;
  border-radius: 10px;
  border: 1px dashed #d1d5db;
}

/* ── Schema Column ── */
.card-header-schema {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.card-title-text-schema {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-db-list {
  color: #6b7280;
}

.tables-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.table-schema-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.table-name-tag code {
  font-family: SFMono-Regular, Consolas, Menlo, monospace;
  font-size: 12.5px;
  color: #2563eb;
  font-weight: 600;
}

.schema-footer-note {
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.4;
  margin-top: 14px;
  text-align: center;
}

.refresh-link {
  color: #2563eb;
  cursor: pointer;
  text-decoration: underline;
}

.refresh-link:hover {
  color: #1d4ed8;
}
</style>