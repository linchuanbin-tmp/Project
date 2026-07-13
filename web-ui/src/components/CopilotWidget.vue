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
          </div>

          <div v-else class="message-list">
            <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
              <div class="message-content-area">
                <div v-if="msg.role === 'user'" class="user-bubble">
                  <span class="message-text">{{ msg.content }}</span>
                </div>
                <div v-else class="message-text">{{ msg.content }}</div>

                <div v-if="msg.role === 'assistant' && msg.metadata" class="metadata-card">
                  <div v-if="msg.metadata.rooms && msg.metadata.rooms.length > 0" class="rooms-recommendation">
                    <p class="section-subtitle">Recommended rooms</p>
                    <div class="rooms-grid">
                      <div v-for="room in msg.metadata.rooms" :key="room.id" class="room-item-card">
                        <div class="room-header">
                          <span class="room-name">{{ room.name }}</span>
                          <span class="room-status" :class="room.available ? 'avail' : 'occ'">{{ room.available ? 'Available' : 'Occupied' }}</span>
                        </div>
                        <div class="room-details-grid">
                          <span>Capacity: {{ room.capacity }} pax</span>
                          <span>Loc: {{ room.location }}</span>
                        </div>
                        <div class="room-ai" v-if="room.aiMatchScore">Match score: {{ room.aiMatchScore }}%</div>
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
            <span class="thinking-label">Thinking</span>
            <span class="thinking-elapsed">{{ thinkingElapsed }}s</span>
          </div>
        </div>

        <!-- Input Area -->
        <div class="copilot-input-area" @click.stop>
          <div class="copilot-input-row">
            <textarea v-model="inputQuery" rows="1" :placeholder="t('dashboard.chat.inputPlaceholder')" class="copilot-textarea" @keydown="handleKeydown" :disabled="isExecuting"></textarea>
            <button class="copilot-send-btn" @click="submitQuery" :disabled="!inputQuery.trim() || isExecuting"><ArrowUp :size="20" /></button>
          </div>
          <div class="copilot-mode-selector">
            <span class="mode-pill mode-auto" :class="{ active: selectedRouter === 'AUTO' }" @click="selectedRouter = 'AUTO'">Auto</span>
            <span class="mode-divider"></span>
            <span class="mode-pill mode-specific" :class="{ active: selectedRouter === 'CODE' }" @click="selectedRouter = 'CODE'">Code</span>
            <span class="mode-pill mode-specific" :class="{ active: selectedRouter === 'TOOL' }" @click="selectedRouter = 'TOOL'">Tool</span>
            <span class="mode-pill mode-specific" :class="{ active: selectedRouter === 'RAG' }" @click="selectedRouter = 'RAG'">Docs</span>
          </div>
        </div>
      </template>
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
  Trash2, ArrowUp, Minus
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

const startThinkingTimer = () => {
  thinkingElapsed.value = 0
  thinkingTimer = setInterval(() => { thinkingElapsed.value++ }, 1000)
}

const stopThinkingTimer = () => {
  if (thinkingTimer) { clearInterval(thinkingTimer); thinkingTimer = null }
}

const clearHistory = () => {
  messages.value = []
  sessionContextQuery.value = ''
  localStorage.removeItem('copilot_chat_history')
}

const saveHistory = () => {
  localStorage.setItem('copilot_chat_history', JSON.stringify(messages.value))
}

const loadHistory = () => {
  const cached = localStorage.getItem('copilot_chat_history')
  if (cached) {
    try { messages.value = JSON.parse(cached) } catch { messages.value = [] }
  }
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

const handleToolCompleted = async (dbTaskId: number | null, queryText: string) => {
  let parsedOutput: any = null
  if (dbTaskId) {
    try {
      const taskRes: any = await getTask(dbTaskId)
      const record = taskRes
      if (record?.output) {
        try { parsedOutput = JSON.parse(record.output) } catch { parsedOutput = { rawText: record.output } }
      }
    } catch (e) { console.error(e) }
  }

  let contentMessage: string = parsedOutput
    ? (parsedOutput.distance
        ? `已为您规划路线，全程 ${parsedOutput.distance || '未知距离'}，预计耗时 ${parsedOutput.duration || '未知时间'}。您可在左侧查看具体路线规划。`
        : parsedOutput.rooms
          ? (parsedOutput.rooms.filter((r: any) => r.available).length > 0
              ? `已为您找到 ${parsedOutput.rooms.filter((r: any) => r.available).length} 间符合要求的空余会议室。您可以在左侧面板选择并预订。`
              : `很抱歉，在指定时间段内未找到符合条件的会议室。您可以在左侧面板手动调整筛选。`)
          : parsedOutput.hasConflict !== undefined
            ? `日程冲突检测完成：${parsedOutput.message || (parsedOutput.hasConflict ? '发现冲突日程。' : '未发现时间冲突。')}。`
            : t('tool.ai.complete'))
    : t('tool.ai.complete')

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

const runToolAgentWebSocket = async (queryText: string) => {
  isExecuting.value = true
  startThinkingTimer()

  let dbTaskId: number | null = null
  try {
    const submitRes: any = await submitTask({ taskType: 'TOOL', input: queryText })
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

  wsClient.connect(wsUrl)

  wsClient.on('message', async (rawData: any) => {
    if (hasCompleted) return
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData
    if (data.status === 'completed') {
      hasCompleted = true
      await handleToolCompleted(dbTaskId, queryText)
      wsClient.close?.()
    } else if (data.status === 'error') {
      hasCompleted = true
      isExecuting.value = false
      stopThinkingTimer()
      messages.value.push({ role: 'assistant', content: data.message || t('tool.ai.error'), timestamp: Date.now() })
      saveHistory()
      scrollToBottom()
      wsClient.close?.()
    }
  })

  wsClient.on('error', () => {
    if (hasCompleted) return
    hasCompleted = true
    isExecuting.value = false
    stopThinkingTimer()
    messages.value.push({ role: 'assistant', content: t('tool.ai.connectionError'), timestamp: Date.now() })
    saveHistory()
    scrollToBottom()
  })

  // Fallback poll — the task may complete before the WS connects (race condition),
  // so poll GET /api/task/{id} once per second until SUCCESS/FAIL.
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
            await handleToolCompleted(dbTaskId, queryText)
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
  let targetRouter = selectedRouter.value
  
  // Construct the accumulated query context
  let queryToSend = query
  if (sessionContextQuery.value) {
    queryToSend = sessionContextQuery.value + ' ' + query
  }

  try {
    if (targetRouter === 'AUTO') {
      const res: any = await request.post('/dashboard/route', { question: queryToSend })
      targetRouter = res?.intent || 'RAG'

      // Check for casual chat / greetings
      if (targetRouter === 'CHAT') {
        isExecuting.value = false
        sessionContextQuery.value = '' // Clear context on chat
        const replyText = res?.reply || '你好！我是你的智能助理 Copilot，随时可以帮您处理 SQL 查询、会议室预订、日程检测或知识库检索。请问今天有什么我可以帮您的？'
        messages.value.push({
          role: 'assistant',
          content: replyText,
          timestamp: Date.now()
        })
        saveHistory()
        scrollToBottom()
        return
      }

      // Check for settings navigation
      if (targetRouter === 'SETTINGS') {
        isExecuting.value = false
        sessionContextQuery.value = '' // Clear context
        messages.value.push({
          role: 'assistant',
          content: 'Intent classified as Settings Navigation. Redirecting you to the settings page...',
          timestamp: Date.now()
        })
        saveHistory()
        scrollToBottom()
        await router.push('/app/settings')
        return
      }

      // Check for slot-filling / clarification query
      if (targetRouter === 'CLARIFY') {
        isExecuting.value = false
        // Update accumulated context query so subsequent inputs are merged
        sessionContextQuery.value = queryToSend
        
        const replyText = res?.reply || '请问您需要预定哪一天的会议室、大概多少人？或者您能提供路线规划的起点和终点吗？'
        messages.value.push({
          role: 'assistant',
          content: replyText,
          timestamp: Date.now()
        })
        saveHistory()
        scrollToBottom()
        return
      }
    }
    
    // Clear context when executing a terminal action
    sessionContextQuery.value = ''

    if (targetRouter === 'TOOL') {
      messages.value.push({ role: 'assistant', content: t('copilot.processingTool'), timestamp: Date.now() })
      saveHistory(); scrollToBottom()
      if (router.currentRoute.value.path !== '/app/tool') await router.push('/app/tool')
      runToolAgentWebSocket(queryToSend)
    } else if (targetRouter === 'CODE') {
      messages.value.push({ role: 'assistant', content: t('copilot.processingCode'), timestamp: Date.now() })
      saveHistory(); scrollToBottom()
      await router.push({ path: '/app/code', query: { query: queryToSend } })
      isExecuting.value = false
    } else {
      isExecuting.value = false
      messages.value.push({
        role: 'assistant',
        content: 'Intent classified as Document Query. Opening RAG Workspace...',
        timestamp: Date.now()
      })
      saveHistory()
      scrollToBottom()

      await router.push({
        path: '/app/rag',
        query: { query }
      })
    }
  } catch (e) {
    console.error('Copilot classification error:', e)
    isExecuting.value = false
    messages.value.push({
      role: 'assistant',
      content: 'Routing failed, opening RAG Workspace...',
      timestamp: Date.now()
    })
    saveHistory()
    scrollToBottom()

    await router.push({
      path: '/app/rag',
      query: { query }
    })
  }
}

watch(() => router.currentRoute.value.query.query, async (newQuery) => {
  if (newQuery && router.currentRoute.value.path === '/app/tool') {
    isOpen.value = true
    inputQuery.value = newQuery as string
    submitQuery()
  }
}, { immediate: true })

onMounted(() => { loadHistory() })
onUnmounted(() => { wsClient.close?.(); stopThinkingTimer(); if (thinkingTimer) clearInterval(thinkingTimer) })
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
}

.section-subtitle {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
}

.rooms-grid { display: flex; flex-direction: column; gap: 8px; }
.room-item-card { background: #ffffff; border: 1px solid #f1f5f9; border-radius: 8px; padding: 10px; }
.room-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.room-name { font-weight: 600; color: #1e293b; font-size: 12.5px; }
.room-status { font-size: 10px; font-weight: 600; padding: 2px 6px; border-radius: 4px; }
.room-status.avail { background-color: #ecfdf5; color: #059669; }
.room-status.occ { background-color: #fef2f2; color: #dc2626; }
.room-details-grid { display: flex; gap: 12px; font-size: 11px; color: #64748b; }
.room-ai { margin-top: 6px; font-size: 11px; font-weight: 600; color: #4f46e5; }
.route-grid { display: flex; gap: 16px; }
.route-stat-item { display: flex; flex-direction: column; gap: 2px; }
.stat-lbl { font-size: 11px; color: #64748b; }
.stat-val { font-size: 13.5px; font-weight: 700; color: #0f172a; }
.conflict-alert { padding: 10px; border-radius: 8px; font-size: 12.5px; font-weight: 500; }
.conflict-alert.warning { background-color: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.conflict-alert.success { background-color: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }

/* Thinking indicator — shimmer typewriter text + elapsed time */
.thinking-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.thinking-label {
  font-size: 13px;
  font-weight: 500;
  background: linear-gradient(
    90deg,
    #94a3b8 0%,
    #64748b 45%,
    #94a3b8 55%,
    #64748b 100%
  );
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: thinking-shimmer 2s ease-in-out infinite;
}

@keyframes thinking-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.thinking-elapsed {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
  margin-left: auto;
}

/* ── Input Area ───────────────────────────────── */
.copilot-input-area {
  padding: 12px 20px 20px;
  background: #ffffff;
  flex-shrink: 0;
}

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
</style>
