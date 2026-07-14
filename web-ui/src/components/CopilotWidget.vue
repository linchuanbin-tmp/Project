<template>
  <div class="copilot-container" :class="{ open: isOpen }">
    <!-- Single morphing element -->
    <div class="copilot-shell" @click="!isOpen && (isOpen = true)">
      <!-- Collapsed state: just the icon -->
      <div v-if="!isOpen" class="shell-collapsed">
        <MessageSquare :size="22" />
        <span v-if="hasUnread" class="unread-dot"></span>
      </div>

      <!-- Expanded content -->
      <template v-else>
        <!-- Header -->
        <div class="copilot-header" @click.stop>
          <span class="header-title">{{ t('copilot.title') }}</span>
          <div class="header-actions">
            <button class="action-btn" @click="showHistory = true" :title="t('copilot.history')">
              <Clock :size="14" />
            </button>
            <button class="action-btn" @click="clearHistory" :title="t('copilot.clearHistory')">
              <Trash2 :size="14" />
            </button>
            <button class="action-btn" @click="isOpen = false">
              <Minus :size="16" />
            </button>
          </div>
        </div>

        <!-- Chat Body -->
        <div class="copilot-chat-body" ref="chatBodyRef" @click.stop>
          <div v-if="messages.length === 0" class="welcome-box">
            <div class="welcome-icon"><Sparkles :size="28" /></div>
            <h2>{{ t('dashboard.chat.greetingTitle') }}</h2>
            <p>{{ t('dashboard.chat.greetingSub') }}</p>
            <button v-if="savedSessions.length > 0" class="history-btn-inline" @click="showHistory = true">
              <Clock :size="14" /> {{ t('copilot.viewHistory') }}
            </button>
          </div>

          <div v-else class="message-list">
            <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
              <div class="message-content-area">
                <div v-if="msg.role === 'user'" class="user-bubble">
                  <span class="message-text">{{ msg.content }}</span>
                </div>
                <div v-else class="message-text message-animate">{{ msg.content }}</div>

                <div v-if="msg.role === 'assistant' && msg.metadata" class="metadata-card">
                  <div v-if="msg.metadata.rooms && msg.metadata.rooms.length > 0" class="rooms-recommendation">
                    <p class="section-subtitle">Recommended rooms</p>
                    <div class="rooms-list">
                      <div v-for="room in msg.metadata.rooms" :key="room.id" class="room-row">
                        <span class="room-dot" :class="room.available ? 'free' : 'busy'"></span>
                        <div class="room-info">
                          <span class="room-row-name">{{ room.name }}</span>
                          <span class="room-row-loc">{{ room.location }}</span>
                        </div>
                        <span class="room-row-cap">{{ room.capacity }} pax</span>
                      </div>
                    </div>
                  </div>
                  <div v-if="msg.metadata.distance" class="route-result">
                    <div class="route-grid">
                      <div class="route-stat-item"><span class="stat-lbl">Distance</span><span class="stat-val">{{ msg.metadata.distance }}</span></div>
                      <div class="route-stat-item"><span class="stat-lbl">Duration</span><span class="stat-val">{{ msg.metadata.duration }}</span></div>
                    </div>
                  </div>
                  <div v-if="msg.metadata.hasConflict !== undefined" class="conflict-result">
                    <div class="conflict-alert" :class="msg.metadata.hasConflict ? 'warning' : 'success'">{{ msg.metadata.message }}</div>
                  </div>
                </div>

                <div class="message-time" :class="msg.role">{{ formatTime(msg.timestamp) }}</div>
              </div>
            </div>
          </div>

          <div v-if="isExecuting" class="thinking-row">
            <span class="processing-text-inline">{{ progressMessage || 'Processing...' }}</span>
            <span class="thinking-elapsed">{{ thinkingElapsed }}s</span>
          </div>
        </div>

        <!-- Input Area -->
        <div class="copilot-input-area" @click.stop>
          <template v-if="isExecuting">
            <div class="copilot-processing-bar">
              <span class="processing-dot"></span>
              <span class="processing-text">{{ progressMessage || 'Processing your request...' }}</span>
            </div>
          </template>
          <template v-else>
          <div class="copilot-input-row">
            <textarea v-model="inputQuery" rows="1" :placeholder="t('dashboard.chat.inputPlaceholder')" class="copilot-textarea" @keydown="handleKeydown"></textarea>
            <button class="copilot-send-btn" @click="submitQuery" :disabled="!inputQuery.trim()"><ArrowUp :size="20" /></button>
          </div>
          <div class="copilot-mode-selector">
            <span class="mode-pill mode-auto" :class="{ active: selectedRouter === 'AUTO' }" @click="selectedRouter = 'AUTO'">Auto</span>
            <span class="mode-divider"></span>
            <span class="mode-pill mode-specific" :class="{ active: selectedRouter === 'CODE' }" @click="selectedRouter = 'CODE'">Code</span>
            <span class="mode-pill mode-specific" :class="{ active: selectedRouter === 'TOOL' }" @click="selectedRouter = 'TOOL'">Tool</span>
            <span class="mode-pill mode-specific" :class="{ active: selectedRouter === 'RAG' }" @click="selectedRouter = 'RAG'">Docs</span>
          </div>
          </template>
        </div>
      </template>

      <!-- History overlay -->
      <div v-if="showHistory" class="history-overlay" @click.self="showHistory = false">
        <div class="history-panel">
          <div class="history-header-panel">
            <span class="history-title-panel">{{ t('copilot.history') }}</span>
            <button class="history-close-btn" @click="showHistory = false">&times;</button>
          </div>
          <div class="history-list">
            <div v-if="savedSessions.length === 0" class="history-empty">{{ t('copilot.noHistory') }}</div>
            <div v-for="sess in savedSessions" :key="sess.key" class="history-item" @click="loadSession(sess.key)">
              <span class="history-label">{{ sess.label }}</span>
              <button class="history-delete-btn" @click.stop="deleteSession(sess.key)" :title="t('copilot.deleteSession')">
                <Trash2 :size="12" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import request from '@utils/request'
import { submitTask, getTask } from '@api/task'
import { wsClient } from '@utils/websocket'
import {
  MessageSquare, Sparkles,
  Trash2, ArrowUp, Minus, Clock
} from 'lucide-vue-next'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  metadata?: any
}

const { t } = useI18n()
const router = useRouter()

const isOpen = ref(false)
const hasUnread = ref(false)
const inputQuery = ref('')
const selectedRouter = ref('AUTO')
const sessionContextQuery = ref('')
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement | null>(null)

const isExecuting = ref(false)
const thinkingElapsed = ref(0)
let thinkingTimer: ReturnType<typeof setInterval> | null = null
const progressMessage = ref('')

const showHistory = ref(false)
const savedSessions = ref<{ key: string; label: string; messages: ChatMessage[] }[]>([])

// ── session storage helpers ──
const SESSION_PREFIX = 'copilot_session_'
const SESSION_INDEX_KEY = 'copilot_session_index'

const loadSessionIndex = (): string[] => {
  try { return JSON.parse(localStorage.getItem(SESSION_INDEX_KEY) || '[]') } catch { return [] }
}

const saveSessionIndex = (keys: string[]) => {
  localStorage.setItem(SESSION_INDEX_KEY, JSON.stringify(keys))
}

const saveCurrentSession = () => {
  if (messages.value.length === 0) return
  const firstMsg = messages.value[0]
  const label = firstMsg.content.slice(0, 40).replace(/\n/g, ' ')
  const key = SESSION_PREFIX + Date.now()
  localStorage.setItem(key, JSON.stringify(messages.value))
  const keys = loadSessionIndex()
  keys.unshift(key)
  const trimmed = keys.slice(0, 20)
  saveSessionIndex(trimmed)
  keys.slice(20).forEach(k => localStorage.removeItem(k))
}

const loadSessions = () => {
  const keys = loadSessionIndex()
  savedSessions.value = keys.map(key => {
    try {
      const msgs: ChatMessage[] = JSON.parse(localStorage.getItem(key) || '[]')
      const first = msgs[0]
      return {
        key,
        label: first ? first.content.slice(0, 50).replace(/\n/g, ' ') : '(empty)',
        messages: msgs,
      }
    } catch { return { key, label: '(corrupted)', messages: [] as ChatMessage[] } }
  }).filter(s => s.messages.length > 0)
}

const loadSession = (key: string) => {
  const found = savedSessions.value.find(s => s.key === key)
  if (found) {
    messages.value = found.messages
    showHistory.value = false
    scrollToBottom()
  }
}

const deleteSession = (key: string) => {
  localStorage.removeItem(key)
  const keys = loadSessionIndex().filter(k => k !== key)
  saveSessionIndex(keys)
  loadSessions()
}

const startNewSession = () => {
  if (messages.value.length > 0) {
    saveCurrentSession()
  }
  messages.value = []
  sessionContextQuery.value = ''
  showHistory.value = false
}

const startThinkingTimer = () => {
  if (thinkingTimer) return
  thinkingElapsed.value = 0
  progressMessage.value = ''
  thinkingTimer = setInterval(() => { thinkingElapsed.value++ }, 1000)
}

const stopThinkingTimer = () => {
  if (thinkingTimer) { clearInterval(thinkingTimer); thinkingTimer = null }
  progressMessage.value = ''
}

// Observe isOpen to reset unread badge when widget is opened
watch(isOpen, (val) => {
  if (val) {
    hasUnread.value = false
  }
})

// Show unread dot when an assistant message arrives while the widget is collapsed
watch(messages, (newVal, oldVal) => {
  if (!isOpen.value && newVal.length > oldVal.length) {
    const latest = newVal[newVal.length - 1]
    if (latest.role === 'assistant') {
      hasUnread.value = true
    }
  }
})

const clearHistory = () => {
  messages.value = []
  sessionContextQuery.value = ''
  localStorage.removeItem('copilot_chat_history')
}

const saveHistory = () => {
  // Keep legacy key for backward compat, but sessions are the primary mechanism
  localStorage.setItem('copilot_chat_history', JSON.stringify(messages.value))
}

const formatTime = (ts: number) => {
  const date = new Date(ts)
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

const handleTaskCompleted = async (dbTaskId: number | null, queryText: string, taskType: string) => {
  let parsedOutput: any = null
  let taskRecord: any = null
  if (dbTaskId) {
    try {
      taskRecord = await getTask(dbTaskId)
      const output = taskRecord?.output
      if (output) {
        try { parsedOutput = JSON.parse(output) } catch { parsedOutput = { rawText: output } }
      }
    } catch (e) { console.error(e) }
  }

  let contentMessage: string
  if (!parsedOutput) {
    contentMessage = t('tool.ai.complete')
  } else if (parsedOutput.sql !== undefined) {
    const sql = parsedOutput.sql || ''
    contentMessage = sql
      ? `已为您生成 SQL：\n\`\`\`sql\n${sql}\n\`\`\`\n请在左侧代码生成页面查看详情。`
      : `SQL生成失败：${parsedOutput.error || '未知错误'}`
  } else if (parsedOutput.distance) {
    contentMessage = `已为您规划路线，全程 ${parsedOutput.distance || '未知距离'}，预计耗时 ${parsedOutput.duration || '未知时间'}。您可在左侧查看具体路线规划。`
  } else if (parsedOutput.rooms) {
    contentMessage = parsedOutput.rooms.filter((r: any) => r.available).length > 0
      ? `已为您找到 ${parsedOutput.rooms.filter((r: any) => r.available).length} 间符合要求的空余会议室。您可以在左侧面板选择并预订。`
      : `很抱歉，在指定时间段内未找到符合条件的会议室。您可以在左侧面板手动调整筛选。`
  } else if (parsedOutput.hasConflict !== undefined) {
    contentMessage = `日程冲突检测完成：${parsedOutput.message || (parsedOutput.hasConflict ? '发现冲突日程。' : '未发现时间冲突。')}。`
  } else {
    contentMessage = t('tool.ai.complete')
  }

  messages.value.push({ role: 'assistant', content: contentMessage, timestamp: Date.now(), metadata: parsedOutput })
  saveHistory()
  if (parsedOutput) {
    window.dispatchEvent(new CustomEvent('copilot-tool-result', { detail: { payload: parsedOutput, query: queryText } }))
    localStorage.setItem('copilot_pending_tool_result', JSON.stringify({ payload: parsedOutput, query: queryText, timestamp: Date.now() }))
  }
  isExecuting.value = false
  stopThinkingTimer()
  scrollToBottom()
}

const runAgentWebSocket = async (queryText: string, taskType: string = 'TOOL') => {
  isExecuting.value = true
  startThinkingTimer()

  let dbTaskId: number | null = null
  try {
    const submitRes: any = await submitTask({ taskType, input: queryText })
    dbTaskId = submitRes?.id ?? null
  } catch (err) {
    console.warn('task-service unavailable')
  }

  if (!dbTaskId) {
    isExecuting.value = false
    stopThinkingTimer()
    messages.value.push({ role: 'assistant', content: t('tool.ai.error'), timestamp: Date.now() })
    return
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/ws/task/progress?taskId=${dbTaskId}`
  let hasCompleted = false

  wsClient.removeAllListeners('message')
  wsClient.removeAllListeners('error')
  wsClient.removeAllListeners('open')

  wsClient.on('message', async (rawData: any) => {
    if (hasCompleted) return
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData
    if (data.status === 'completed') {
      hasCompleted = true
      await handleTaskCompleted(dbTaskId, queryText, taskType)
      wsClient.close?.()
    } else if (data.status === 'error') {
      hasCompleted = true
      isExecuting.value = false
      stopThinkingTimer()
      messages.value.push({ role: 'assistant', content: data.message || t('tool.ai.error'), timestamp: Date.now() })
      saveHistory()
      scrollToBottom()
      wsClient.close?.()
    } else if (data.message) {
      progressMessage.value = data.message
    }
  })

  wsClient.on('error', () => {
    if (hasCompleted) return
    hasCompleted = true
    isExecuting.value = false
    stopThinkingTimer()
    wsClient.close?.()
    messages.value.push({ role: 'assistant', content: t('tool.ai.connectionError'), timestamp: Date.now() })
    saveHistory()
    scrollToBottom()
  })

  let pollTimer: ReturnType<typeof setInterval> | null = null
  wsClient.on('open', () => {
    if (!dbTaskId || hasCompleted) return
    pollTimer = setInterval(async () => {
      if (hasCompleted) { clearInterval(pollTimer!); return }
      try {
        const taskRes: any = await getTask(dbTaskId!)
        const record = taskRes
        if (record?.status === 'SUCCESS' || record?.status === 'FAIL') {
          clearInterval(pollTimer!)
          if (hasCompleted) return
          hasCompleted = true
          if (record.status === 'SUCCESS') {
            await handleTaskCompleted(dbTaskId, queryText, taskType)
          } else {
            isExecuting.value = false
            stopThinkingTimer()
            messages.value.push({ role: 'assistant', content: record.errorMsg || t('tool.ai.error'), timestamp: Date.now() })
            saveHistory()
            scrollToBottom()
          }
          wsClient.close?.()
        }
      } catch { /* poll silently */ }
    }, 500)
  })

  wsClient.connect(wsUrl)
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    submitQuery()
  }
}

const submitQuery = async () => {
  const query = inputQuery.value.trim()
  if (!query) return
  messages.value.push({ role: 'user', content: query, timestamp: Date.now() })
  inputQuery.value = ''
  scrollToBottom()

  isExecuting.value = true
  startThinkingTimer()
  let targetRouter = selectedRouter.value

  let queryToSend = query
  if (sessionContextQuery.value) {
    queryToSend = sessionContextQuery.value + ' ' + query
  }

  try {
    if (targetRouter === 'AUTO') {
      const res: any = await request.post('/dashboard/route', { question: queryToSend })
      targetRouter = res?.intent || 'RAG'

      if (targetRouter === 'CHAT') {
        isExecuting.value = false
        stopThinkingTimer()
        sessionContextQuery.value = ''
        const replyText = res?.reply || t('copilot.chatDefault')
        messages.value.push({ role: 'assistant', content: replyText, timestamp: Date.now() })
        saveHistory()
        scrollToBottom()
        return
      }

      if (targetRouter === 'SETTINGS') {
        isExecuting.value = false
        stopThinkingTimer()
        sessionContextQuery.value = ''
        messages.value.push({ role: 'assistant', content: t('copilot.routingToSettings'), timestamp: Date.now() })
        saveHistory()
        scrollToBottom()
        await router.push('/app/settings')
        return
      }

      if (targetRouter === 'CLARIFY') {
        isExecuting.value = false
        stopThinkingTimer()
        sessionContextQuery.value = queryToSend
        const replyText = res?.reply || t('copilot.clarifyDefault')
        messages.value.push({ role: 'assistant', content: replyText, timestamp: Date.now() })
        saveHistory()
        scrollToBottom()
        return
      }
    }

    sessionContextQuery.value = ''

    if (targetRouter === 'TOOL') {
      messages.value.push({ role: 'assistant', content: t('copilot.processingTool'), timestamp: Date.now() })
      saveHistory(); scrollToBottom()
      if (router.currentRoute.value.path !== '/app/tool') await router.push('/app/tool')
      runAgentWebSocket(queryToSend)
    } else if (targetRouter === 'CODE') {
      isExecuting.value = false
      stopThinkingTimer()
      messages.value.push({ role: 'assistant', content: t('copilot.processingCode'), timestamp: Date.now() })
      saveHistory(); scrollToBottom()
      window.dispatchEvent(new CustomEvent('copilot-code-query', { detail: { query: queryToSend } }))
      localStorage.setItem('copilot_pending_code_query', JSON.stringify({ query: queryToSend, timestamp: Date.now() }))
      if (router.currentRoute.value.path !== '/app/code') {
        await router.push('/app/code')
      }
    } else {
      isExecuting.value = false
      stopThinkingTimer()
      messages.value.push({ role: 'assistant', content: t('copilot.routingToDocs'), timestamp: Date.now() })
      saveHistory(); scrollToBottom()
      await router.push({ path: '/app/rag', query: { query: queryToSend } })
    }
  } catch (e) {
    console.error('Copilot classification error:', e)
    isExecuting.value = false
    stopThinkingTimer()
    sessionContextQuery.value = ''
    messages.value.push({ role: 'assistant', content: t('copilot.routingFailedFallback'), timestamp: Date.now() })
    saveHistory(); scrollToBottom()
    await router.push({ path: '/app/rag', query: { query } })
  }
}

watch(() => router.currentRoute.value.query.query, async (newQuery) => {
  if (newQuery && router.currentRoute.value.path === '/app/tool') {
    isOpen.value = true
    inputQuery.value = newQuery as string
    submitQuery()
  }
}, { immediate: true })

onMounted(() => {
  loadSessions()
  // Don't auto-load last session — user sees welcome screen by default
  // Preload AI provider config so Copilot uses correct model
  request.get('/user/config/ai-provider').then((res: any) => {
    if (res && res.provider) {
      localStorage.setItem('ai_provider', res.provider)
      if (res.model) localStorage.setItem('ai_provider_custom_model', res.model)
    }
  }).catch(() => {})
})

onUnmounted(() => {
  if (messages.value.length > 0) saveCurrentSession()
  wsClient.close?.()
  stopThinkingTimer()
  if (thinkingTimer) clearInterval(thinkingTimer)
})
</script>

<style scoped>
.copilot-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* ── Morphing shell ──────────────────────────── */
.copilot-shell {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #111827;
  box-shadow: 0 4px 24px rgba(17, 24, 39, 0.2);
  transition:
    width 0.45s cubic-bezier(0.32, 0.72, 0, 1),
    height 0.45s cubic-bezier(0.32, 0.72, 0, 1),
    border-radius 0.45s cubic-bezier(0.32, 0.72, 0, 1),
    background 0.35s ease,
    box-shadow 0.45s cubic-bezier(0.32, 0.72, 0, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.copilot-container.open .copilot-shell {
  width: 440px;
  height: 620px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #f1f5f9;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08), 0 4px 12px rgba(15, 23, 42, 0.02);
}

/* Collapsed state */
.shell-collapsed {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  cursor: pointer;
  position: relative;
  transition: opacity 0.15s ease;
}

.unread-dot {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 10px;
  height: 10px;
  background-color: #ef4444;
  border-radius: 50%;
  border: 2px solid #111827;
}

/* ── Header ───────────────────────────────────── */
.copilot-header {
  background: #ffffff;
  padding: 18px 24px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.header-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s;
}

.action-btn:hover {
  background-color: #f1f5f9;
  color: #0f172a;
}

/* ── Chat Body ────────────────────────────────── */
.copilot-chat-body {
  flex: 1;
  padding: 12px 24px;
  overflow-y: auto;
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Welcome */
.welcome-box {
  text-align: center;
  padding: 24px 10px;
  margin: auto 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.welcome-icon {
  width: 48px;
  height: 48px;
  background: #f8fafc;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4f46e5;
  margin-bottom: 14px;
}

.welcome-box h2 {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
  letter-spacing: -0.4px;
}

.welcome-box p {
  font-size: 13px;
  color: #64748b;
  margin: 0;
  line-height: 1.5;
  max-width: 300px;
}

/* Messages */
.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message-row {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.message-content-area {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 100%;
}

.user-bubble {
  align-self: flex-end;
  background: #f3f4f6;
  border-radius: 14px 14px 4px 14px;
  padding: 10px 14px;
  max-width: 85%;
}

.user-bubble .message-text { color: #1f2937; }

.message-text {
  font-size: 13.5px;
  line-height: 1.6;
  color: #1e293b;
  word-break: break-word;
  white-space: pre-line;
}

.message-animate {
  animation: message-fade-up 0.35s ease-out both;
}

@keyframes message-fade-up {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.message-time {
  font-size: 10px;
  color: #94a3b8;
  margin-top: 4px;
}

.message-time.user {
  text-align: right;
  padding-right: 4px;
}

/* Metadata Cards */
.metadata-card {
  margin-top: 10px;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  background: #f8fafc;
  padding: 12px 14px;
  animation: card-reveal 0.3s ease-out 0.1s both;
}

@keyframes card-reveal {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.section-subtitle {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.rooms-list {
  display: flex;
  flex-direction: column;
}

.room-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}
.room-row:last-child { border-bottom: none; }

.room-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.room-dot.free { background: #059669; }
.room-dot.busy { background: #dc2626; }

.room-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.room-row-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.room-row-loc { font-size: 11px; color: #94a3b8; }

.room-row-cap {
  font-size: 12px;
  font-weight: 500;
  color: #475569;
  background: #f1f5f9;
  padding: 3px 8px;
  border-radius: 6px;
  flex-shrink: 0;
}

.rooms-grid, .room-item-card, .room-header,
.room-name, .room-status, .room-details-grid, .room-ai { display: none; }
.route-grid { display: flex; gap: 16px; }
.route-stat-item { display: flex; flex-direction: column; gap: 2px; }
.stat-lbl { font-size: 11px; color: #64748b; }
.stat-val { font-size: 13.5px; font-weight: 700; color: #0f172a; }
.conflict-alert { padding: 10px; border-radius: 8px; font-size: 12.5px; font-weight: 500; }
.conflict-alert.warning { background-color: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.conflict-alert.success { background-color: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }

.thinking-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 24px 8px;
}

.processing-text-inline {
  font-size: 12px;
  color: #94a3b8;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.thinking-elapsed {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

/* ── Input Area ───────────────────────────────── */
.copilot-input-area {
  padding: 12px 20px 20px;
  background: #ffffff;
  flex-shrink: 0;
}

/* Processing bar (replaces input during execution) */
.copilot-processing-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.processing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4f46e5;
  animation: processing-pulse 1.2s ease-in-out infinite;
}

@keyframes processing-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50%      { opacity: 1;   transform: scale(1.1); }
}

.processing-text {
  flex: 1;
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── Input area restored ── */

.copilot-input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 10px 8px 10px 16px;
  transition: border-color 0.2s;
}

.copilot-input-row:focus-within { border-color: #111827; }

.copilot-textarea {
  flex: 1;
  border: none;
  resize: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: #0f172a;
  outline: none;
  padding: 0;
  background: transparent;
  align-self: center;
}

.copilot-textarea::placeholder { color: #94a3b8; }

/* Send button — same round shape as collapsed FAB */
.copilot-send-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background-color: #111827;
  color: #ffffff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.copilot-send-btn:hover { background-color: #1f2937; transform: scale(1.06); }
.copilot-send-btn:disabled { background-color: #e2e8f0; color: #94a3b8; cursor: not-allowed; transform: none; }

.copilot-mode-selector {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 10px;
}

/* Mode pills */
.mode-pill {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.mode-auto { background-color: #f1f5f9; color: #475569; }
.mode-auto:hover { background-color: #e2e8f0; color: #0f172a; }
.mode-auto.active { background-color: #111827; color: #ffffff; }

.mode-divider {
  width: 1px;
  height: 20px;
  background: #e2e8f0;
  margin: 0 4px;
  flex-shrink: 0;
}

.mode-specific { background-color: transparent; color: #94a3b8; }
.mode-specific:hover { color: #475569; }
.mode-specific.active { color: #111827; }

/* Animations */
.spin { animation: loading-spin 1.2s linear infinite; }

@keyframes loading-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* ── History Panel ──────────────────────────────── */
.history-btn-inline {
  margin-top: 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.history-btn-inline:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #1e293b;
}

.history-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(15, 23, 42, 0.15);
  border-radius: 20px;
  z-index: 10;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 100px;
}

.history-panel {
  width: 92%;
  max-height: 60%;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.history-header-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px 10px;
  flex-shrink: 0;
}

.history-title-panel {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.history-close-btn {
  background: none;
  border: none;
  font-size: 20px;
  color: #94a3b8;
  cursor: pointer;
  line-height: 1;
}

.history-list {
  overflow-y: auto;
  padding: 4px 10px 14px;
  flex: 1;
}

.history-empty {
  text-align: center;
  padding: 20px 0;
  font-size: 13px;
  color: #94a3b8;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.history-item:hover { background: #f8fafc; }

.history-label {
  flex: 1;
  font-size: 13px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-delete-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  flex-shrink: 0;
  padding: 4px;
  border-radius: 4px;
}
.history-delete-btn:hover { color: #ef4444; background: #fef2f2; }
</style>
