<template>
  <div class="copilot-container">
    <!-- Floating Trigger Button -->
    <button 
      class="copilot-btn" 
      :class="{ active: isOpen }"
      @click="toggleOpen"
      :title="t('copilot.tooltip')"
    >
      <MessageSquare v-if="!isOpen" :size="24" />
      <X v-else :size="24" />
      <span v-if="hasUnread && !isOpen" class="unread-dot"></span>
    </button>

    <!-- Chat Overlay Window (Unified Single Card) -->
    <transition name="slide-up">
      <div v-if="isOpen" class="copilot-window">
        <!-- Header (Unified with Card Background) -->
        <div class="copilot-header">
          <div class="header-info">
            <div class="header-icon-badge">
              <Sparkles :size="15" />
            </div>
            <div class="header-text-block">
              <span class="header-title">{{ t('copilot.title') }}</span>
              <span class="header-status">
                <span class="status-dot online"></span>
                {{ t('dashboard.online') }}
              </span>
            </div>
          </div>
          <div class="header-actions">
            <button class="action-btn" @click="clearHistory" :title="t('copilot.clearHistory')">
              <Trash2 :size="14" />
            </button>
            <button class="action-btn" @click="isOpen = false">
              <ChevronDown :size="16" />
            </button>
          </div>
        </div>

        <!-- Chat Area (Unified Content Flow) -->
        <div class="copilot-chat-body" ref="chatBodyRef">
          <!-- Welcome message if history is empty -->
          <div v-if="messages.length === 0" class="welcome-box">
            <div class="welcome-icon">
              <Sparkles :size="28" />
            </div>
            <h2>{{ t('dashboard.chat.greetingTitle') }}</h2>
            <p>{{ t('dashboard.chat.greetingSub') }}</p>

            <!-- Suggestions in a modern grid -->
            <div class="copilot-suggestions">
              <div 
                v-for="sug in suggestions" 
                :key="sug.text"
                class="sug-pill"
                @click="useSuggestion(sug.text, sug.router)"
              >
                <div class="sug-icon-wrap">
                  <component :is="sug.icon" :size="13" />
                </div>
                <span>{{ sug.text }}</span>
              </div>
            </div>
          </div>

          <!-- Message list -->
          <div v-else class="message-list">
            <div 
              v-for="(msg, index) in messages" 
              :key="index"
              class="message-row"
              :class="msg.role"
            >
              <!-- Avatar -->
              <div class="message-avatar">
                <div v-if="msg.role === 'user'" class="avatar-wrap user">
                  <User :size="14" />
                </div>
                <div v-else class="avatar-wrap assistant">
                  <Sparkles :size="14" />
                </div>
              </div>

              <!-- Message Contents -->
              <div class="message-content-area">
                <div class="message-sender-name">
                  {{ msg.role === 'user' ? 'You' : 'Copilot' }}
                </div>
                <div class="message-text">{{ msg.content }}</div>

                <!-- Structured Tool results -->
                <div v-if="msg.role === 'assistant' && msg.metadata" class="metadata-card">
                  <!-- Meeting Room Recommendation -->
                  <div v-if="msg.metadata.rooms && msg.metadata.rooms.length > 0" class="rooms-recommendation">
                    <p class="section-subtitle">Recommended rooms</p>
                    <div class="rooms-grid">
                      <div 
                        v-for="room in msg.metadata.rooms" 
                        :key="room.id" 
                        class="room-item-card"
                      >
                        <div class="room-header">
                          <span class="room-name">🚪 {{ room.name }}</span>
                          <span class="room-status" :class="room.available ? 'avail' : 'occ'">
                            {{ room.available ? 'Available' : 'Occupied' }}
                          </span>
                        </div>
                        <div class="room-details-grid">
                          <span>👥 Capacity: {{ room.capacity }} pax</span>
                          <span>📍 Loc: {{ room.location }}</span>
                        </div>
                        <div class="room-ai" v-if="room.aiMatchScore">
                          ✨ Match score: {{ room.aiMatchScore }}%
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Route results -->
                  <div v-if="msg.metadata.distance" class="route-result">
                    <div class="route-grid">
                      <div class="route-stat-item">
                        <span class="stat-lbl">🚗 Distance</span>
                        <span class="stat-val">{{ msg.metadata.distance }}</span>
                      </div>
                      <div class="route-stat-item">
                        <span class="stat-lbl">⏱️ Duration</span>
                        <span class="stat-val">{{ msg.metadata.duration }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- Conflict check results -->
                  <div v-if="msg.metadata.hasConflict !== undefined" class="conflict-result">
                    <div class="conflict-alert" :class="msg.metadata.hasConflict ? 'warning' : 'success'">
                      {{ msg.metadata.message }}
                    </div>
                  </div>
                </div>
                
                <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
              </div>
            </div>
          </div>

          <!-- Websocket Streaming progress indicator -->
          <div v-if="isExecuting" class="copilot-thinking-card">
            <div class="thinking-header">
              <RefreshCw :size="14" class="spin thinking-icon" />
              <span>{{ taskMessage }}</span>
            </div>
            <div class="thinking-progress-bar">
              <div class="progress-fill" :style="{ width: taskProgress + '%' }"></div>
            </div>
            <span class="progress-percent-text">{{ taskProgress }}%</span>
          </div>
        </div>

        <!-- Input Section (Seamless Floating Card inside Body) -->
        <div class="copilot-input-area-wrap">
          <div class="copilot-input-box">
            <textarea
              v-model="inputQuery"
              rows="2"
              :placeholder="t('dashboard.chat.inputPlaceholder')"
              class="copilot-textarea"
              @keydown.enter.prevent="submitQuery"
              :disabled="isExecuting"
            ></textarea>

            <div class="copilot-input-footer">
              <!-- Router Selector (CamelCase/Capitalized, No All-Caps) -->
              <div class="copilot-mode-selector">
                <span 
                  class="mode-pill" 
                  :class="{ active: selectedRouter === 'AUTO' }"
                  @click="selectedRouter = 'AUTO'"
                >
                  🔮 Auto
                </span>
                <span 
                  class="mode-pill" 
                  :class="{ active: selectedRouter === 'CODE' }"
                  @click="selectedRouter = 'CODE'"
                >
                  💻 Code
                </span>
                <span 
                  class="mode-pill" 
                  :class="{ active: selectedRouter === 'TOOL' }"
                  @click="selectedRouter = 'TOOL'"
                >
                  🔧 Tool
                </span>
                <span 
                  class="mode-pill" 
                  :class="{ active: selectedRouter === 'RAG' }"
                  @click="selectedRouter = 'RAG'"
                >
                  📚 Docs
                </span>
              </div>

              <button 
                class="copilot-send-btn" 
                @click="submitQuery"
                :disabled="!inputQuery.trim() || isExecuting"
              >
                <ArrowRight :size="15" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import request from '@utils/request'
import { submitTask, getTask } from '@api/task'
import { wsClient } from '@utils/websocket'
import { 
  MessageSquare, X, Sparkles, ChevronDown, 
  Trash2, ArrowRight, RefreshCw, FileText, Wrench, BookOpen, User 
} from 'lucide-vue-next'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  metadata?: any
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isOpen = ref(false)
const hasUnread = ref(false)
const inputQuery = ref('')
const selectedRouter = ref('AUTO')
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement | null>(null)

// WebSocket loading state
const isExecuting = ref(false)
const taskProgress = ref(0)
const taskMessage = ref('')

const toggleOpen = () => {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    hasUnread.value = false
    scrollToBottom()
  }
}

// Clear History
const clearHistory = () => {
  messages.value = []
  localStorage.removeItem('copilot_chat_history')
}

// Save to LocalStorage
const saveHistory = () => {
  localStorage.setItem('copilot_chat_history', JSON.stringify(messages.value))
}

const loadHistory = () => {
  const cached = localStorage.getItem('copilot_chat_history')
  if (cached) {
    try {
      messages.value = JSON.parse(cached)
    } catch {
      messages.value = []
    }
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

// Suggestions block
const suggestions = computed(() => [
  {
    text: t('dashboard.chat.sug.balance'),
    icon: FileText,
    router: 'CODE'
  },
  {
    text: t('dashboard.chat.sug.bookRoom'),
    icon: Wrench,
    router: 'TOOL'
  },
  {
    text: t('dashboard.chat.sug.policy'),
    icon: BookOpen,
    router: 'RAG'
  }
])

const useSuggestion = (text: string, routerVal: string) => {
  inputQuery.value = text
  selectedRouter.value = routerVal
}

// Connect and stream tasks via WebSocket inside Copilot
const runToolAgentWebSocket = async (queryText: string) => {
  isExecuting.value = ref(true).value
  taskProgress.value = 0
  taskMessage.value = t('tool.ai.connecting')

  let dbTaskId: number | null = null
  try {
    const submitRes: any = await submitTask({ taskType: 'TOOL', input: queryText })
    dbTaskId = submitRes?.data?.data?.id ?? submitRes?.data?.id ?? null
  } catch (err) {
    console.warn('task-service unavailable, falling back to mock WS flow')
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = dbTaskId
    ? `${protocol}//${host}/ws/task/progress?taskId=${dbTaskId}`
    : `${protocol}//${host}/ws/?taskId=fallback_${Date.now()}`

  wsClient.connect(wsUrl)

  let hasFetchedResult = false

  wsClient.on('message', async (rawData: any) => {
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData
    taskProgress.value = data.progress ?? 0
    taskMessage.value = data.message ?? ''

    if (data.status === 'completed' && !hasFetchedResult) {
      hasFetchedResult = true
      taskMessage.value = t('tool.ai.fetchingResult')
      
      let parsedOutput: any = null
      if (dbTaskId) {
        try {
          const taskRes: any = await getTask(dbTaskId)
          const record = taskRes?.data?.data ?? taskRes?.data
          if (record?.output) {
            try {
              parsedOutput = JSON.parse(record.output)
            } catch {
              parsedOutput = { rawText: record.output }
            }
          }
        } catch (e) {
          console.error(e)
        }
      }

      // Add system response message
      messages.value.push({
        role: 'assistant',
        content: data.message || t('tool.ai.intentMatched', { intent: 'Tool' }),
        timestamp: Date.now(),
        metadata: parsedOutput
      })
      saveHistory()

      // Dispatch event to active Tool page if it exists
      if (parsedOutput) {
        window.dispatchEvent(new CustomEvent('copilot-tool-result', { 
          detail: {
            payload: parsedOutput,
            query: queryText
          }
        }))
      }

      isExecuting.value = false
      scrollToBottom()
      wsClient.close?.()
    } else if (data.status === 'error') {
      isExecuting.value = false
      messages.value.push({
        role: 'assistant',
        content: data.message || t('tool.ai.error'),
        timestamp: Date.now()
      })
      saveHistory()
      scrollToBottom()
      wsClient.close?.()
    }
  })

  wsClient.on('error', () => {
    isExecuting.value = false
    taskMessage.value = t('tool.ai.connectionError')
    messages.value.push({
      role: 'assistant',
      content: t('tool.ai.connectionError'),
      timestamp: Date.now()
    })
    saveHistory()
    scrollToBottom()
  })

  wsClient.on('open', () => {
    taskMessage.value = t('tool.ai.connected')
    wsClient.send(JSON.stringify({
      taskType: 'TOOL',
      query: queryText,
      parameters: {}
    }))
  })
}

// Submit prompt query
const submitQuery = async () => {
  const query = inputQuery.value.trim()
  if (!query) return

  // 1. Add user message to log
  messages.value.push({
    role: 'user',
    content: query,
    timestamp: Date.now()
  })
  inputQuery.value = ''
  scrollToBottom()

  // 2. Classify intent
  isExecuting.value = true
  let targetRouter = selectedRouter.value
  try {
    if (targetRouter === 'AUTO') {
      const res: any = await request.post('/dashboard/route', { question: query })
      targetRouter = res?.data?.intent || res?.intent || 'RAG'
      console.log('Classified intent in Copilot:', targetRouter)
    }

    if (targetRouter === 'TOOL') {
      if (route.path !== '/app/tool') {
        await router.push('/app/tool')
      }
      runToolAgentWebSocket(query)
    } else if (targetRouter === 'CODE') {
      isExecuting.value = false
      messages.value.push({
        role: 'assistant',
        content: 'Intent classified as SQL generation. Redirecting to SQL Agent Workspace...',
        timestamp: Date.now()
      })
      saveHistory()
      scrollToBottom()

      await router.push({
        path: '/app/code',
        query: { query }
      })
    } else {
      isExecuting.value = false
      messages.value.push({
        role: 'assistant',
        content: 'Intent classified as Document Query. Filtering document library...',
        timestamp: Date.now()
      })
      saveHistory()
      scrollToBottom()

      await router.push({
        path: '/app/dept-docs',
        query: { query }
      })
    }
  } catch (e) {
    console.error('Copilot classification error:', e)
    isExecuting.value = false
    messages.value.push({
      role: 'assistant',
      content: 'Routing failed, redirecting to Document Library...',
      timestamp: Date.now()
    })
    saveHistory()
    scrollToBottom()

    await router.push({
      path: '/app/dept-docs',
      query: { query }
    })
  }
}

// Watch global query redirects from the Dashboard center input
watch(
  () => route.query.query,
  async (newQuery) => {
    if (newQuery) {
      if (route.path === '/app/tool') {
        isOpen.value = true
        inputQuery.value = newQuery as string
        submitQuery()
      }
    }
  },
  { immediate: true }
)

onMounted(() => {
  loadHistory()
})

onUnmounted(() => {
  wsClient.close?.()
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

/* Floating Action Button */
.copilot-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #111827;
  color: #ffffff;
  border: none;
  box-shadow: 0 4px 24px rgba(17, 24, 39, 0.2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  position: relative;
}

.copilot-btn:hover {
  transform: scale(1.05) translateY(-2px);
  background: #1f2937;
  box-shadow: 0 8px 30px rgba(17, 24, 39, 0.3);
}

.copilot-btn.active {
  background: #374151;
}

.unread-dot {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 10px;
  height: 10px;
  background-color: #ef4444;
  border-radius: 50%;
  border: 2px solid #ffffff;
}

/* Unified Card Container - Pure White Sheet */
.copilot-window {
  position: absolute;
  bottom: 72px;
  right: 0;
  width: 440px;
  height: 640px;
  background: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08), 0 4px 12px rgba(15, 23, 42, 0.02);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: all 0.3s;
}

/* Seamless Header (Merged with Card BG) */
.copilot-header {
  background: #ffffff;
  padding: 18px 24px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 10;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon-badge {
  width: 30px;
  height: 30px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  color: #ffffff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 8px rgba(99, 102, 241, 0.15);
}

.header-text-block {
  display: flex;
  flex-direction: column;
}

.header-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  color: #64748b;
  font-weight: 500;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.status-dot.online {
  background-color: #10b981;
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

/* Chat Body (Seamless White Background, divider lines removed) */
.copilot-chat-body {
  flex: 1;
  padding: 12px 24px;
  overflow-y: auto;
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Welcome Box */
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
  margin: 0 0 20px;
  line-height: 1.5;
  max-width: 300px;
}

.copilot-suggestions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.sug-pill {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
  background: #f8fafc;
  font-size: 12.5px;
  font-weight: 500;
  color: #334155;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.sug-pill:hover {
  background: #f1f5f9;
  border-color: #e2e8f0;
  color: #0f172a;
}

.sug-icon-wrap {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #ffffff;
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
}

/* Chat Message Rows (Dividers removed, uses clean spacing) */
.message-row {
  display: flex;
  gap: 14px;
  width: 100%;
}

.avatar-wrap {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-wrap.user {
  background: #f8fafc;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.avatar-wrap.assistant {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  color: #ffffff;
}

.message-content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

/* Sender Name - Standard Title Case (No global uppercase styling) */
.message-sender-name {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

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
  margin-top: 6px;
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

.rooms-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room-item-card {
  background: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  padding: 10px;
}

.room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.room-name {
  font-weight: 600;
  color: #1e293b;
  font-size: 12.5px;
}

.room-status {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}

.room-status.avail {
  background-color: #ecfdf5;
  color: #059669;
}

.room-status.occ {
  background-color: #fef2f2;
  color: #dc2626;
}

.room-details-grid {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #64748b;
}

.room-ai {
  margin-top: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #4f46e5;
}

.route-grid {
  display: flex;
  gap: 16px;
}

.route-stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-lbl {
  font-size: 11px;
  color: #64748b;
}

.stat-val {
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
}

.conflict-alert {
  padding: 10px;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 500;
}

.conflict-alert.warning {
  background-color: #fffbeb;
  color: #b45309;
  border: 1px solid #fde68a;
}

.conflict-alert.success {
  background-color: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}

/* Thinking Indicator Card */
.copilot-thinking-card {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 14px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.thinking-icon {
  color: #4f46e5;
}

.thinking-progress-bar {
  width: 100%;
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5 0%, #7c3aed 100%);
  border-radius: 2px;
  transition: width 0.2s ease;
}

.progress-percent-text {
  font-size: 11px;
  font-weight: 700;
  color: #4f46e5;
  align-self: flex-end;
}

/* Seamless Floating Input (No top border, transparent wrapper) */
.copilot-input-area-wrap {
  padding: 12px 24px 24px;
  background: #ffffff;
}

.copilot-input-box {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 12px 14px 10px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.copilot-input-box:focus-within {
  border-color: #4f46e5;
  box-shadow: 0 4px 24px rgba(99, 102, 241, 0.08);
}

.copilot-textarea {
  width: 100%;
  border: none;
  resize: none;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.5;
  color: #0f172a;
  outline: none;
  padding: 0;
}

.copilot-textarea::placeholder {
  color: #94a3b8;
}

.copilot-input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.copilot-mode-selector {
  display: flex;
  gap: 3px;
}

/* Mode pill styling (No global uppercase) */
.mode-pill {
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  background-color: #f1f5f9;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  text-transform: none;
}

.mode-pill:hover {
  background-color: #e2e8f0;
  color: #0f172a;
}

.mode-pill.active {
  background-color: #111827;
  color: #ffffff;
}

.copilot-send-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background-color: #111827;
  color: #ffffff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.copilot-send-btn:hover {
  background-color: #1f2937;
  transform: scale(1.05);
}

.copilot-send-btn:disabled {
  background-color: #f1f5f9;
  color: #94a3b8;
  cursor: not-allowed;
  transform: none;
}

/* Drawer open transition animations */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(24px) scale(0.96);
}

.spin {
  animation: loading-spin 1.2s linear infinite;
}

@keyframes loading-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
