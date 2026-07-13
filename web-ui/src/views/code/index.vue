<template>
  <div class="sql-generator-wrapper">
    <div class="sql-generator-container">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('code.pageTitle') }}</h1>
        <p class="page-sub">{{ $t('code.pageSub') }}</p>
      </div>
      <div class="header-right" style="display: flex; align-items: center;">
        <el-button 
          @click="refreshMetadata" 
          :loading="metadataLoading" 
          class="btn-refresh"
        >
          <RefreshCw :size="14" :class="{ 'spin': metadataLoading }" />
          {{ $t('code.syncSchema') }}
        </el-button>
      </div>
    </div>

    <!-- Main Content Area: Left Input, Right Schema/Metadata -->
    <div class="main-layout">
      <!-- Left side: Prompt Editor, Code Verification & Results -->
      <div class="editor-section">
        
        <!-- Modern Prompt Card (Without horizontal header lines) -->
        <div class="premium-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Sparkles :size="16" class="icon-sparkles" />
              {{ $t('code.describeQuery') }}
            </span>
            <span class="step-badge">{{ $t('code.step1') }}</span>
          </div>

          <!-- Modern Chat-like Input Container -->
          <div class="modern-input-container">
            <textarea
              v-model="question"
              rows="3"
              :placeholder="$t('code.placeholder')"
              class="modern-textarea"
            ></textarea>
            
            <div class="modern-input-footer">
              <div class="footer-spacer"></div>
              <button
                :disabled="queryLoading"
                class="modern-btn-generate"
                @click="handleGenerate"
              >
                <span v-if="queryLoading">{{ $t('code.generating') }}</span>
                <span v-else>{{ $t('code.generateBtn') }}</span>
              </button>
            </div>
          </div>

          <!-- Quick Templates (Outside the prompt container) -->
          <div class="templates-section-outside">
            <span class="label-text">{{ $t('code.tryPrompts') }}</span>
            <div class="template-tags">
              <span
                v-for="tmpl in quickTemplates"
                :key="tmpl.en"
                class="template-tag"
                @click="useTemplate(tmpl.en)"
              >
                {{ tmpl.en }}
              </span>
            </div>
          </div>
        </div>

        <!-- Step 2: SQL Review & Edit Card (Without horizontal header lines) -->
        <div v-if="generatedSql || generateError" class="premium-card review-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Database :size="16" class="icon-database" />
              {{ $t('code.queryReview') }}
            </span>
            <span class="step-badge step-2">{{ $t('code.step2') }}</span>
          </div>

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
                <span class="editor-label">{{ $t('code.editableLabel') }}</span>
                <button @click="copySQL" class="btn-copy">
                  <component :is="copied ? Check : Copy" :size="12" style="margin-right: 4px;" />
                  {{ copied ? $t('code.copied') : $t('code.copy') }}
                </button>
              </div>

              <!-- Interactive dark code editor -->
              <div class="code-editor-container">
                <textarea
                  v-model="generatedSql"
                  rows="5"
                  class="code-textarea"
                  spellcheck="false"
                ></textarea>
                <div class="editor-status-tag">{{ $t('code.editableHint') }}</div>
              </div>
            </div>

            <!-- Stats Bar -->
            <div class="stats-bar-inner">
              <div class="stat-item">
                <span class="stat-label">{{ $t('code.model') }}</span>
                <el-tag size="small" class="tag-inference">{{ inferenceMethod || 'LLM' }}</el-tag>
              </div>
              <div class="stat-item">
                <span class="stat-label">{{ $t('code.restriction') }}</span>
                <span class="stat-value danger-text">{{ $t('code.selectOnly') }}</span>
              </div>
            </div>

            <div class="card-actions">
              <button
                :disabled="executionLoading"
                class="modern-btn-execute"
                @click="handleExecute"
              >
                <span v-if="executionLoading">{{ $t('code.running') }}</span>
                <span v-else>{{ $t('code.runQuery') }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Step 3: Query Results Card (Without horizontal header lines) -->
        <div v-if="executionResult || executeError" class="premium-card result-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Database :size="16" class="icon-result" />
              {{ $t('code.queryResults') }}
            </span>
            <div v-if="executionResult && executionResult.success" class="result-actions">
              <el-button
                v-if="executionResult.rows && executionResult.rows.length"
                size="small"
                @click="exportCSV"
                class="btn-export"
              >
                <FileSpreadsheet :size="14" style="margin-right: 4px;" />
                {{ $t('code.exportCSV') }}
              </el-button>
            </div>
          </div>

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
              <p class="error-detail">{{ $t('code.blockError') }}</p>
            </template>
          </el-alert>

          <!-- Success Results Display -->
          <div v-else-if="executionResult && executionResult.success">
            <!-- Stats -->
            <div class="stats-bar-inner">
              <div class="stat-item">
                <span class="stat-label">{{ $t('code.status') }}</span>
                <el-tag size="small" type="success" effect="dark" class="tag-success">{{ $t('code.success') }}</el-tag>
              </div>
              <div class="stat-item" v-if="executionResult.elapsedMs">
                <span class="stat-label">{{ $t('code.executionTime') }}</span>
                <span class="stat-value font-semibold">{{ executionResult.elapsedMs }}ms</span>
              </div>
              <div class="stat-item" v-if="executionResult.rowCount !== undefined">
                <span class="stat-label">{{ $t('code.rowsReturned') }}</span>
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
              {{ $t('code.noRows') }}
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Database Schema/Metadata (Without horizontal header lines) -->
      <div class="schema-section">
        <div class="premium-card schema-card">
          <div class="card-header-simple">
            <span class="card-title-text">
              <Database :size="16" class="icon-db-list" />
              {{ $t('code.availableSchema') }}
            </span>
          </div>

          <div v-if="metadataLoading" class="loading-schema">
            <el-skeleton :rows="6" animated />
          </div>
          <div v-else-if="!tables.length" class="empty-schema">
            {{ $t('code.noTablesCached') }} <a @click="refreshMetadata" class="refresh-link">{{ $t('code.syncLink') }}</a> {{ $t('code.loadTables') }}
          </div>
          <div v-else class="tables-list">
            <div v-for="table in tables" :key="table" class="table-schema-item">
              <span class="table-name-tag">
                <code>{{ table }}</code>
              </span>
            </div>
            <div class="schema-footer-note">
              {{ $t('code.schemaFooter') }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Reusable Agent Thinking Modal -->
  <AgentThinking
    :visible="queryLoading"
    :title="$t('code.thinkingTitle')"
    :footer="$t('code.thinkingFooter')"
    :steps="thoughtMessages"
  />
</div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Sparkles, Database, Play, RefreshCw, Copy, Check, FileSpreadsheet } from 'lucide-vue-next'
import AgentThinking from '@components/AgentThinking.vue'
import {
  generateSQLOnly,
  executeSQLDirectly,
  getCodeMetadata,
  refreshCodeMetadata
} from '@api/code'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

// Quick prompt templates for Text-to-SQL (translated to English)
const quickTemplates = computed(() => [
  { label: t('code.templateShowCustomers'), en: t('code.templateShowCustomers') },
  { label: t('code.templateAvgBalances'), en: t('code.templateAvgBalances') },
  { label: t('code.templateLargeTxns'), en: t('code.templateLargeTxns') },
  { label: t('code.templateHighRisk'), en: t('code.templateHighRisk') },
  { label: t('code.templateEmployeeStats'), en: t('code.templateEmployeeStats') }
])

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

// Thinking Modal Custom Thought Messages
const thoughtMessages = computed(() => [
  t('code.thinking1'),
  t('code.thinking2'),
  t('code.thinking3'),
  t('code.thinking4'),
  t('code.thinking5')
])

// Fetch table names in schema
const fetchMetadata = async () => {
  metadataLoading.value = true
  try {
    const res = await getCodeMetadata()
    tables.value = res.tableNames || []
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
    tables.value = res.tableNames || []
    ElMessage.success(t('code.refreshSuccess'))
  } catch (e: any) {
    ElMessage.error(t('code.refreshFailed'))
    console.error(e)
  } finally {
    metadataLoading.value = false
  }
}

const useTemplate = (tmpl: string) => {
  question.value = tmpl
}

// Step 1: Generate SQL from natural language prompt
const handleGenerate = async (silent = false) => {
  if (!question.value.trim()) {
    if (!silent) ElMessage.warning(t('code.enterPrompt'))
    return
  }
  queryLoading.value = true

  generatedSql.value = ''
  generateError.value = ''
  executionResult.value = null
  executeError.value = ''
  try {
    const res = await generateSQLOnly({ question: question.value })

    if (res && res.success) {
      generatedSql.value = res.sql || ''
      inferenceMethod.value = res.inferenceMethod || 'LLM'
      if (!silent) ElMessage.success(t('code.generateSuccess'))
    } else {
      generateError.value = res?.errorMessage || t('code.generateFailed')
      if (!silent) ElMessage.error(generateError.value)
    }
  } catch (e: any) {
    generateError.value = e.message || t('code.generateFailed')
    if (!silent) ElMessage.error(t('code.generateFailed'))
  } finally {
    // Provide a small delay so the progress bar reaches 100% smoothly before overlay fades out
    setTimeout(() => {
      queryLoading.value = false
    }, 450)
  }
}

// Step 2: Execute reviewed & possibly edited SQL query
const handleExecute = async () => {
  if (!generatedSql.value.trim()) {
    ElMessage.warning(t('code.sqlEmpty'))
    return
  }
  executionLoading.value = true
  executionResult.value = null
  executeError.value = ''
  try {
    const res = await executeSQLDirectly({ sql: generatedSql.value })

    if (res && res.success) {
      executionResult.value = res
      ElMessage.success(t('code.executeSuccess'))
    } else {
      executeError.value = res?.errorMessage || t('code.executeFailed')
    }
  } catch (e: any) {
    executeError.value = e.message || t('code.executeFailed')
    ElMessage.error(t('code.executeFailed'))
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
    ElMessage.success(t('code.copied'))
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
  ElMessage.success(t('code.csvSuccess'))
}

onMounted(async () => {
  await fetchMetadata()

  // Handle copilot code request that arrived before mount
  const cached = localStorage.getItem('copilot_pending_code_query')
  if (cached) {
    try {
      const { query, timestamp } = JSON.parse(cached)
      if (Date.now() - timestamp < 30_000) {
        question.value = query
        localStorage.removeItem('copilot_pending_code_query')
        await nextTick()
        handleGenerate(true)
      } else {
        localStorage.removeItem('copilot_pending_code_query')
      }
    } catch { localStorage.removeItem('copilot_pending_code_query') }
  }

  // Listen for copilot code requests sent while page is already open
  const onCopilotCodeQuery = async (event: Event) => {
    const { query } = (event as CustomEvent).detail || {}
    if (query) {
      localStorage.removeItem('copilot_pending_code_query')
      question.value = query
      await nextTick()
      handleGenerate(true)
    }
  }
  window.addEventListener('copilot-code-query', onCopilotCodeQuery)
  onBeforeUnmount(() => window.removeEventListener('copilot-code-query', onCopilotCodeQuery))
})
</script>

<style scoped>
.sql-generator-container {
  padding: 16px 0;
  max-width: 1200px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
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
  justify-content: center;
}

.btn-refresh:hover {
  background: #f9fafb !important;
  border-color: #cbd5e1 !important;
  color: #111827 !important;
}

.btn-refresh :deep(span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 100%;
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

/* ── Premium Minimalist Cards (Without Divider Lines) ── */
.premium-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  transition: all 0.2s ease;
}

.premium-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.card-header-simple {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px; /* Spacer instead of a divider line */
}

.card-title-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
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
  background: #eff6ff;
  padding: 4px 10px;
  border-radius: 12px;
}

.step-badge.step-2 {
  color: #10b981;
  background: #f0fdf4;
}

/* ── Modern Chat-like Input Container (ChatGPT Style) ── */
.modern-input-container {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc; /* Sleek light slate background */
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.2s ease;
}

.modern-input-container:focus-within {
  border-color: #6366f1;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.08);
}

.modern-textarea {
  width: 100%;
  border: none;
  background: transparent;
  resize: none;
  outline: none;
  font-family: inherit;
  font-size: 14px;
  color: #1e293b;
  line-height: 1.6;
  min-height: 60px;
}

.modern-textarea::placeholder {
  color: #94a3b8;
}

.modern-input-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.templates-section-outside {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.templates-section-outside .label-text {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}

.template-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-tag {
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  padding: 6px 14px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid #e2e8f0;
  font-weight: 500;
}

.template-tag:hover {
  background: #ffffff;
  color: #6366f1;
  border-color: #6366f1;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.05);
}

.modern-btn-generate {
  background: #0f172a;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  padding: 10px 18px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.modern-btn-generate:hover:not(:disabled) {
  background: #1e293b;
  transform: translateY(-1px);
}

.modern-btn-generate:active:not(:disabled) {
  transform: translateY(0);
}

.modern-btn-generate:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

/* ── SQL Review Styles ── */
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
  color: #475569;
}

.btn-copy {
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 4px 10px;
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: all 0.15s;
}

.btn-copy:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.code-editor-container {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #0f172a;
}

.code-textarea {
  width: 100%;
  box-sizing: border-box;
  background: #0f172a;
  color: #38bdf8;
  font-family: Consolas, SFMono-Regular, "Liberation Mono", Menlo, Courier, monospace;
  font-size: 14px;
  line-height: 1.6;
  border: none;
  padding: 16px 16px 28px;
  resize: vertical;
  outline: none;
  transition: all 0.2s;
}

.code-textarea:focus {
  background: #020617;
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

.stats-bar-inner {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 12px 18px;
  background: #f8fafc;
  border-radius: 10px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.stat-label {
  color: #64748b;
  font-weight: 500;
}

.stat-value {
  color: #0f172a;
  font-weight: 600;
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

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.modern-btn-execute {
  background: #10b981;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  padding: 10px 18px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.modern-btn-execute:hover:not(:disabled) {
  background: #059669;
  transform: translateY(-1px);
}

.modern-btn-execute:active:not(:disabled) {
  transform: translateY(0);
}

/* ── Result Display ── */
.result-actions {
  display: flex;
  gap: 8px;
}

.btn-export {
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.result-alert {
  border-radius: 8px;
  margin-bottom: 16px;
}

.error-detail {
  font-size: 12.5px;
  color: #ef4444;
  margin-top: 4px;
}

.table-container {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f8fafc;
  color: #0f172a;
  font-weight: 600;
}

.empty-rows-message {
  text-align: center;
  padding: 32px;
  color: #64748b;
  font-size: 13.5px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
}

/* ── Schema Explorer ── */
.card-header-schema {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.card-title-text-schema {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-db-list {
  color: #475569;
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
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  transition: all 0.15s;
}

.table-schema-item:hover {
  background: #f1f5f9;
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
  color: #94a3b8;
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