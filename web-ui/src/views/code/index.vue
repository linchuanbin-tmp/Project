<template>
  <div class="sql-generator-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">SQL Agent</h1>
        <p class="page-sub">
          Describe the data you need in natural language, review the generated query, and run it with human-in-the-loop security.
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
      <!-- Left side: Prompt Editor, Code Verification & Results -->
      <div class="editor-section">
        <!-- Input Card -->
        <el-card class="editor-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">
                <Sparkles :size="16" class="icon-sparkles" />
                Describe Your Query
              </span>
              <span class="step-badge">Step 1: Write Prompt</span>
            </div>
          </template>

          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            placeholder="e.g. Calculate the average balance for each account type, show all high risk customers..."
            resize="none"
            class="prompt-textarea"
          />

          <!-- Quick Templates -->
          <div class="templates-section">
            <span class="label-text">Try these:</span>
            <div class="template-tags">
              <span
                v-for="tmpl in quickTemplates"
                :key="tmpl.en"
                class="template-tag"
                @click="useTemplate(tmpl.en)"
              >
                {{ tmpl.label }}
              </span>
            </div>
          </div>

          <div class="card-actions">
            <el-button
              type="primary"
              :loading="queryLoading"
              class="btn-generate"
              @click="handleGenerate"
            >
              Generate SQL Query
            </el-button>
          </div>
        </el-card>

        <!-- Step 2: SQL Review & Edit Card -->
        <el-card v-if="generatedSql || generateError" class="editor-card review-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">
                <Database :size="16" class="icon-database" />
                SQL Query Review
              </span>
              <span class="step-badge step-2">Step 2: Verify & Edit</span>
            </div>
          </template>

          <!-- Generation Error Alert -->
          <el-alert
            v-if="generateError"
            type="error"
            :title="generateError"
            show-icon
            :closable="false"
            class="result-alert"
          />

          <div v-else>
            <div class="editor-wrapper">
              <div class="editor-header">
                <span class="editor-label">Generated MySQL SELECT statement (You can edit it below)</span>
                <el-button size="small" @click="copySQL" class="btn-copy">
                  <component :is="copied ? Check : Copy" :size="12" style="margin-right: 4px;" />
                  {{ copied ? 'Copied' : 'Copy' }}
                </el-button>
              </div>

              <!-- Interactive dark code editor -->
              <div class="code-editor-container">
                <textarea
                  v-model="generatedSql"
                  rows="5"
                  class="code-textarea"
                  spellcheck="false"
                ></textarea>
                <div class="editor-status-tag">Editable Review Mode</div>
              </div>
            </div>

            <!-- Stats Bar -->
            <div class="stats-bar-inner">
              <div class="stat-item">
                <span class="stat-label">Model:</span>
                <el-tag size="small" class="tag-inference">{{ inferenceMethod || 'LLM' }}</el-tag>
              </div>
              <div class="stat-item">
                <span class="stat-label">Restriction:</span>
                <span class="stat-value danger-text">SELECT queries only</span>
              </div>
            </div>

            <div class="card-actions">
              <el-button
                type="success"
                :loading="executionLoading"
                class="btn-execute"
                @click="handleExecute"
              >
                <Play :size="14" class="btn-icon" /> Run Query
              </el-button>
            </div>
          </div>
        </el-card>

        <!-- Step 3: Query Results Card -->
        <el-card v-if="executionResult || executeError" class="result-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">
                <Database :size="16" class="icon-result" />
                Query Results
              </span>
              <div v-if="executionResult && executionResult.success" class="result-actions">
                <el-button
                  v-if="executionResult.rows && executionResult.rows.length"
                  size="small"
                  @click="exportCSV"
                  class="btn-export"
                >
                  <FileSpreadsheet :size="14" style="margin-right: 4px;" />
                  Export CSV
                </el-button>
              </div>
            </div>
          </template>

          <!-- Execution Error Display -->
          <el-alert
            v-if="executeError"
            type="error"
            :title="executeError"
            show-icon
            :closable="false"
            class="result-alert"
          >
            <template #default>
              <p class="error-detail">
                This query was blocked by the security whitelist filter (e.g. attempted write operation or disallowed keywords), or has MySQL syntax errors.
              </p>
            </template>
          </el-alert>

          <!-- Success Results Display -->
          <div v-else-if="executionResult && executionResult.success">
            <!-- Stats -->
            <div class="stats-bar-inner">
              <div class="stat-item">
                <span class="stat-label">Status:</span>
                <el-tag size="small" type="success" effect="dark" class="tag-success">Success</el-tag>
              </div>
              <div class="stat-item" v-if="executionResult.elapsedMs">
                <span class="stat-label">Execution Time:</span>
                <span class="stat-value font-semibold">{{ executionResult.elapsedMs }}ms</span>
              </div>
              <div class="stat-item" v-if="executionResult.rowCount !== undefined">
                <span class="stat-label">Rows Returned:</span>
                <span class="stat-value font-semibold">{{ executionResult.rowCount }}</span>
              </div>
            </div>

            <!-- Results Table -->
            <div v-if="executionResult.rows && executionResult.rows.length" class="table-container">
              <el-table :data="executionResult.rows" style="width: 100%" border max-height="350" stripe>
                <el-table-column
                  v-for="col in executionResult.columns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  show-overflow-tooltip
                />
              </el-table>
            </div>
            <div v-else class="empty-rows-message">
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
            No tables cached. Click <a @click="refreshMetadata" class="refresh-link">Sync Schema</a> to load.
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
  generateSQLOnly, 
  executeSQLDirectly,
  getCodeMetadata, 
  refreshCodeMetadata 
} from '@api/code'

// Quick prompt templates for Text-to-SQL (translated to English)
const quickTemplates = [
  { label: 'Show Customers', en: 'Show all customer records' },
  { label: 'Avg Balances', en: 'Calculate average balance per account type' },
  { label: 'Large Txns', en: 'Find transactions in 2026 with amount > 10,000' },
  { label: 'High Risk Users', en: 'List all high-risk customers' },
  { label: 'Employee Stats', en: 'Count employees by department' }
]

const tables = ref<string[]>([])
const metadataLoading = ref(false)
const question = ref('')

const queryLoading = ref(false)
const generatedSql = ref('')
const generateError = ref('')
const inferenceMethod = ref('')

const executionLoading = ref(false)
const executionResult = ref<any>(null)
const executeError = ref('')

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
    ElMessage.success('Schema metadata synchronized!')
  } catch (e: any) {
    ElMessage.error('Failed to synchronize schema metadata')
    console.error(e)
  } finally {
    metadataLoading.value = false
  }
}

const useTemplate = (tmpl: string) => {
  question.value = tmpl
}

// Step 1: Generate SQL from natural language prompt
const handleGenerate = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('Please enter a query prompt')
    return
  }
  queryLoading.value = true
  generatedSql.value = ''
  generateError.value = ''
  executionResult.value = null
  executeError.value = ''
  try {
    const res = await generateSQLOnly({ question: question.value })
    const payload = res?.data ?? res
    
    if (payload && payload.success) {
      generatedSql.value = payload.sql || ''
      inferenceMethod.value = payload.inferenceMethod || 'LLM'
      ElMessage.success('SQL query generated successfully!')
    } else {
      generateError.value = payload?.errorMessage || 'Failed to generate SQL'
    }
  } catch (e: any) {
    generateError.value = e.message || 'Server error occurred during SQL generation'
    ElMessage.error('Generation failed')
  } finally {
    queryLoading.value = false
  }
}

// Step 2: Execute reviewed & possibly edited SQL query
const handleExecute = async () => {
  if (!generatedSql.value.trim()) {
    ElMessage.warning('SQL statement is empty')
    return
  }
  executionLoading.value = true
  executionResult.value = null
  executeError.value = ''
  try {
    const res = await executeSQLDirectly({ sql: generatedSql.value })
    const payload = res?.data ?? res
    
    if (payload && payload.success) {
      executionResult.value = payload
      ElMessage.success('Query executed successfully!')
    } else {
      executeError.value = payload?.errorMessage || 'Query execution failed'
    }
  } catch (e: any) {
    executeError.value = e.message || 'Server error occurred during SQL execution'
    ElMessage.error('Execution failed')
  } finally {
    executionLoading.value = false
  }
}

// Copy query SQL to clipboard
const copySQL = () => {
  if (generatedSql.value) {
    navigator.clipboard.writeText(generatedSql.value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
    ElMessage.success('SQL copied to clipboard!')
  }
}

// Export execution rows to CSV format
const exportCSV = () => {
  if (!executionResult.value || !executionResult.value.rows || !executionResult.value.rows.length) return
  const cols = executionResult.value.columns
  const rows = executionResult.value.rows
  
  let csvContent = '\uFEFF' // Add BOM for Excel compatibility with UTF-8 characters
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
  border-bottom: 1px solid #e5e7eb;
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
  color: #6b7280;
  margin: 0;
}

.btn-refresh {
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  color: #374151;
  font-weight: 500;
  height: 32px;
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
  gap: 24px;
}

@media (max-width: 992px) {
  .main-layout {
    grid-template-columns: 1fr;
  }
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ── Editor Card ── */
.editor-card, .result-card, .schema-card {
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.008);
  background: #ffffff;
  transition: box-shadow 0.2s;
}

.editor-card:hover, .result-card:hover, .schema-card:hover {
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.02);
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
  color: #6366f1;
}

.icon-database {
  color: #10b981;
}

.step-badge {
  font-size: 11px;
  font-weight: 600;
  color: #6366f1;
  background: #e0e7ff;
  padding: 4px 10px;
  border-radius: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.step-badge.step-2 {
  color: #10b981;
  background: #d1fae5;
}

.prompt-textarea :deep(.el-textarea__inner) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  padding: 14px;
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
  align-items: center;
  gap: 12px;
}

.label-text {
  font-size: 12px;
  font-weight: 600;
  color: #9ca3af;
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
  padding: 5px 12px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
  font-weight: 500;
}

.template-tag:hover {
  background: #111827;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.15);
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
  height: 42px;
  font-weight: 500;
  padding: 10px 20px;
  transition: all 0.15s;
}

.btn-generate:hover, :deep(.el-button--primary:hover) {
  opacity: 0.88;
  transform: translateY(-1px);
}

.btn-generate:active, :deep(.el-button--primary:active) {
  transform: translateY(0);
}

.btn-execute :deep(.el-button--success), :deep(.el-button--success) {
  background-color: #10b981 !important;
  border: none !important;
  border-radius: 10px !important;
  height: 42px;
  font-weight: 500;
  padding: 10px 20px;
  transition: all 0.15s;
}

.btn-execute:hover, :deep(.el-button--success:hover) {
  opacity: 0.88;
  transform: translateY(-1px);
}

.btn-execute:active, :deep(.el-button--success:active) {
  transform: translateY(0);
}

.btn-icon {
  margin-right: 6px;
}

/* ── Code Editor Component ── */
.editor-wrapper {
  margin-bottom: 18px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.editor-label {
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
}

.btn-copy {
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.code-editor-container {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #334155;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.2);
}

.code-textarea {
  width: 100%;
  box-sizing: border-box;
  background: #0f172a; /* Premium Slate 900 */
  color: #38bdf8; /* Ocean blue code text */
  font-family: Consolas, SFMono-Regular, "Liberation Mono", Menlo, Courier, monospace;
  font-size: 14px;
  line-height: 1.6;
  border: none;
  padding: 18px 18px 32px;
  resize: vertical;
  outline: none;
  transition: all 0.2s;
}

.code-textarea:focus {
  background: #020617; /* Slate 950 */
}

.editor-status-tag {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 10px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  pointer-events: none;
}

/* ── Result Display ── */
.result-actions {
  display: flex;
  gap: 8px;
}

.btn-export {
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

/* ── Stats Bar ── */
.stats-bar-inner {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 12px 18px;
  background: #f9fafb;
  border-radius: 10px;
  margin-bottom: 16px;
  border: 1px solid #e5e7eb;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.stat-label {
  color: #6b7280;
  font-weight: 500;
}

.stat-value {
  color: #111827;
  font-weight: 600;
}

.text-sec {
  color: #4b5563;
  font-weight: 500;
}

.danger-text {
  color: #ef4444;
  font-weight: 500;
}

.tag-inference {
  background-color: #f0fdf4 !important;
  color: #166534 !important;
  border-color: #bbf7d0 !important;
  font-weight: 600;
}

.tag-success {
  background-color: #10b981 !important;
  border-color: #10b981 !important;
}

.table-container {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f9fafb;
  color: #111827;
  font-weight: 600;
}

.empty-rows-message {
  text-align: center;
  padding: 32px;
  color: #6b7280;
  font-size: 13.5px;
  background: #f9fafb;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
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
  color: #4b5563;
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
  padding: 10px 14px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  transition: all 0.15s;
}

.table-schema-item:hover {
  background: #f3f4f6;
  border-color: #cbd5e1;
}

.table-name-tag code {
  font-family: SFMono-Regular, Consolas, Menlo, monospace;
  font-size: 12.5px;
  color: #3b82f6;
  font-weight: 600;
}

.schema-footer-note {
  font-size: 11.5px;
  color: #9ca3af;
  line-height: 1.4;
  margin-top: 14px;
  text-align: center;
}

.refresh-link {
  color: #3b82f6;
  cursor: pointer;
  text-decoration: underline;
}

.refresh-link:hover {
  color: #2563eb;
}
</style>