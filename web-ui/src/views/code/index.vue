<template>
  <div class="code-container">
    <el-page-header @back="router.back()" :title="$t('code.pageTitle')" />

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <!-- 输入区 -->
        <el-card class="query-card" shadow="hover">
          <div class="input-section">
            <el-input
                v-model="question"
                type="textarea"
                :rows="4"
                :placeholder="$t('code.placeholder')"
                :disabled="loading"
                class="query-textarea"
                @keydown.enter.ctrl="doQuery"
            />
            <div class="input-actions">
              <div class="action-buttons">
                <el-button
                    type="primary"
                    size="large"
                    @click="doQuery"
                    :loading="loading"
                    :disabled="!question.trim()"
                >
                  <el-icon><Search /></el-icon>
                  {{ $t('code.generateAndRun') }}
                </el-button>
                <el-button
                    size="large"
                    @click="doGenerateOnly"
                    :loading="loading"
                    :disabled="!question.trim()"
                >
                  <el-icon><EditPen /></el-icon>
                  {{ $t('code.generateOnly') }}
                </el-button>
                <el-button size="large" @click="clearAll">
                  <el-icon><Delete /></el-icon>
                  {{ $t('code.clear') }}
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 结果区 -->
    <el-row :gutter="20" style="margin-top: 20px;" v-if="currentResult">
      <!-- 左侧：SQL + 校验 -->
      <el-col :xs="24" :lg="12">
        <el-card class="result-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ $t('code.generatedSql') }}</span>
              <div>
                <el-tag
                    :type="currentResult.whitelistPassed ? 'success' : 'danger'"
                    effect="dark"
                    style="margin-right: 8px;"
                >
                  {{ currentResult.whitelistPassed ? $t('code.whitelistPass') : $t('code.whitelistFail') }}
                </el-tag>
                <el-tag type="info" effect="plain">
                  {{ currentResult.inferenceMethod || 'TEMPLATE' }}
                </el-tag>
              </div>
            </div>
          </template>

          <div class="sql-display">
            <pre><code>{{ currentResult.sql || $t('code.noSql') }}</code></pre>
          </div>

          <div v-if="currentResult.errorMessage" style="margin-top: 12px;">
            <el-alert
                :title="currentResult.errorMessage"
                type="error"
                :closable="false"
                show-icon
            />
          </div>

          <div style="margin-top: 12px; color: #909399; font-size: 12px;">
            {{ $t('code.elapsed') }}: {{ currentResult.elapsedMs ?? 'N/A' }}ms
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：查询结果 -->
      <el-col :xs="24" :lg="12">
        <el-card class="result-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ $t('code.queryResult') }}</span>
              <el-tag v-if="currentResult.rowCount !== undefined && currentResult.rowCount !== null" type="success">
                {{ currentResult.rowCount }} {{ $t('code.rows') }}
              </el-tag>
            </div>
          </template>

          <!-- 表格式展示结果 -->
          <div v-if="currentResult.columns && currentResult.columns.length > 0" class="table-wrapper">
            <el-table
                :data="currentResult.rows"
                stripe
                border
                max-height="400"
                size="small"
                empty-text="No data"
            >
              <el-table-column
                  v-for="col in currentResult.columns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  :min-width="120"
                  show-overflow-tooltip
              />
            </el-table>
          </div>

          <!-- 无列信息但有 rowCount -->
          <el-empty
              v-else-if="currentResult.rowCount !== undefined && currentResult.rowCount !== null"
              :description="$t('code.noColumns')"
          />

          <!-- 无结果 -->
          <el-empty v-else :description="$t('code.noResult')" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 历史记录 -->
    <el-row :gutter="20" style="margin-top: 20px;" v-if="history.length > 0">
      <el-col :span="24">
        <el-card class="history-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ $t('code.history') }} ({{ history.length }})</span>
              <el-button size="small" text @click="history = []">{{ $t('code.clearHistory') }}</el-button>
            </div>
          </template>
          <div
              v-for="(item, idx) in history"
              :key="idx"
              class="history-item"
              @click="replayHistory(item)"
          >
            <div class="history-question">
              <el-icon><ChatLineSquare /></el-icon>
              <strong>{{ item.question }}</strong>
            </div>
            <div class="history-sql">
              <el-tag size="small" :type="item.whitelistPassed ? 'success' : 'danger'">
                {{ item.whitelistPassed ? $t('code.whitelistPass') : $t('code.whitelistFail') }}
              </el-tag>
              <code>{{ item.sql }}</code>
            </div>
            <div class="history-meta">
              {{ item.rowCount ?? '?' }} {{ $t('code.rows') }} · {{ item.elapsedMs ?? '?' }}ms
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, EditPen, Delete, ChatLineSquare } from '@element-plus/icons-vue'
import { codeQuery, codeGenerate } from '@api/code'

const router = useRouter()
const { t } = useI18n()

const question = ref('')
const loading = ref(false)
const currentResult = ref<any>(null)
const history = ref<any[]>([])

// 一键查询
const doQuery = async () => {
  if (!question.value.trim()) return
  loading.value = true
  currentResult.value = null
  try {
    const res = await codeQuery(question.value.trim())
    currentResult.value = res
    addHistory(res)
    if (res.success) {
      ElMessage.success(t('code.querySuccess'))
    } else {
      ElMessage.warning(res.errorMessage || t('code.queryFailed'))
    }
  } catch (e: any) {
    ElMessage.error(e.message || t('code.networkError'))
  } finally {
    loading.value = false
  }
}

// 仅生成 SQL
const doGenerateOnly = async () => {
  if (!question.value.trim()) return
  loading.value = true
  currentResult.value = null
  try {
    const res = await codeGenerate(question.value.trim())
    currentResult.value = res
    addHistory(res)
    if (res.success) {
      ElMessage.success(t('code.generateSuccess'))
    } else {
      ElMessage.warning(res.errorMessage || t('code.generateFailed'))
    }
  } catch (e: any) {
    ElMessage.error(e.message || t('code.networkError'))
  } finally {
    loading.value = false
  }
}

// 清空
const clearAll = () => {
  question.value = ''
  currentResult.value = null
}

// 历史记录
const addHistory = (res: any) => {
  history.value.unshift({
    question: question.value.trim(),
    sql: res.sql,
    whitelistPassed: res.whitelistPassed,
    rowCount: res.rowCount,
    elapsedMs: res.elapsedMs,
    inferenceMethod: res.inferenceMethod
  })
}

const replayHistory = (item: any) => {
  question.value = item.question
  currentResult.value = {
    success: true,
    sql: item.sql,
    whitelistPassed: item.whitelistPassed,
    rowCount: item.rowCount,
    elapsedMs: item.elapsedMs,
    inferenceMethod: item.inferenceMethod,
    columns: null,
    rows: null
  }
}
</script>

<style scoped>
.code-container {
  padding: 0;
}

.query-card {
  border-left: 4px solid #409EFF;
}

.input-section {
  display: flex;
  flex-direction: column;
}

.query-textarea :deep(.el-textarea__inner) {
  font-size: 15px;
  line-height: 1.6;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  flex-wrap: wrap;
  gap: 10px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.result-card {
  min-height: 300px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sql-display {
  background: #1e1e1e;
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
}

.sql-display pre {
  margin: 0;
}

.sql-display code {
  color: #d4d4d4;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.table-wrapper {
  margin-top: 0;
}

.history-card {
  border-left: 4px solid #67C23A;
}

.history-item {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: background 0.2s;
}

.history-item:last-child {
  border-bottom: none;
}

.history-item:hover {
  background: #f5f7fa;
}

.history-question {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 14px;
}

.history-sql {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.history-sql code {
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
}

.history-meta {
  font-size: 12px;
  color: #909399;
}
</style>
