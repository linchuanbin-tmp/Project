<template>
  <div class="tool-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">Tool Call Agent</h1>
        <p class="page-sub">Book meeting rooms, detect schedule conflicts, and plan routes using natural language.</p>
      </div>
    </div>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- Left panel: feature tabs -->
      <el-col :xs="24" :sm="24" :md="16">
        <el-tabs v-model="activeTab" type="border-card" class="tool-tabs">
          <!-- Meeting room booking -->
          <!-- Meeting room booking -->
          <el-tab-pane label="Meeting rooms" name="meeting">
            <MeetingAgent ref="meetingAgentRef" />
          </el-tab-pane>

          <!-- Schedule conflict detection -->
          <el-tab-pane label="Schedule check" name="schedule">
            <ScheduleAgent ref="scheduleAgentRef" />
          </el-tab-pane>

          <!-- Route planning -->
          <el-tab-pane label="Route planning" name="route">
            <RouteAgent ref="routeAgentRef" />
          </el-tab-pane>
        </el-tabs>
      </el-col>

      <!-- Right panel: AI assistant with real-time progress -->
      <el-col :xs="24" :sm="24" :md="8">
        <el-card class="ai-card">
          <template #header>
            <div class="card-header">
              <span>AI Assistant</span>
              <el-tag v-if="isExecuting" type="warning" effect="dark">Running</el-tag>
            </div>
          </template>

          <div class="chat-mode">
            <el-input
                v-model="naturalQuery"
                type="textarea"
                :rows="4"
                placeholder="e.g. 'Help me book a meeting room for June 2nd, capacity 10', 'Check if admin is free between June 2nd and 3rd', 'Plan a route from HKU to HK Airport'"
                :disabled="isExecuting"
            />

            <el-button
                type="primary"
                style="width: 100%; margin-top: 10px;"
                @click="executeWithWebSocket"
                :loading="isExecuting"
                :disabled="!naturalQuery.trim()"
            >
              {{ isExecuting ? 'Processing...' : 'Send to AI' }}
            </el-button>

            <!-- Real-time progress bar -->
            <div v-if="taskStatus" class="progress-section">
              <el-divider />
              <div class="progress-info">
                <span class="status-label">{{ taskMessage }}</span>
                <span class="progress-percent">{{ taskProgress }}%</span>
              </div>
              <el-progress
                  :percentage="taskProgress"
                  :status="taskStatus === 'completed' ? 'success' : taskStatus === 'error' ? 'exception' : ''"
                  :stroke-width="10"
                  striped
                  striped-flow
                  :duration="10"
              />
            </div>

            <!-- AI result (shown after task completes) -->
            <div v-if="aiResponse" class="ai-result">
              <el-divider />
              <h4>AI analysis</h4>

              <!-- Parsed intent from AI -->
              <div v-if="aiResponse.aiParsed">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="Intent">{{ aiResponse.aiParsed.intent || 'Query' }}</el-descriptions-item>
                  <el-descriptions-item label="Date">{{ aiResponse.aiParsed.date || 'Today' }}</el-descriptions-item>
                  <el-descriptions-item label="Time range">{{ aiResponse.aiParsed.timeRange || 'Not specified' }}</el-descriptions-item>
                  <el-descriptions-item label="Capacity">{{ aiResponse.aiParsed.capacity || 'Not specified' }}</el-descriptions-item>
                  <el-descriptions-item v-if="aiResponse.aiParsed.equipment" label="Equipment">{{ aiResponse.aiParsed.equipment?.join(', ') || 'None' }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- Recommended meeting rooms -->
              <div v-if="aiResponse.rooms" style="margin-top: 10px;">
                <h5>Recommended rooms</h5>
                <el-card v-for="room in aiResponse.rooms" :key="room.id" class="room-card" shadow="hover">
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span><strong>{{ room.name }}</strong> ({{ room.id }})</span>
                    <el-tag :type="room.available ? 'success' : 'danger'">
                      {{ room.available ? 'Available' : 'Occupied' }}
                    </el-tag>
                  </div>
                  <p style="margin: 5px 0; color: #666; font-size: 12px;">
                    Capacity: {{ room.capacity }} people | Location: {{ room.location }} | Equipment: {{ room.equipment?.join(', ') }}
                  </p>
                  <p v-if="room.aiMatchScore" style="margin: 0; color: #409EFF; font-size: 12px;">
                    🤖 AI Match: {{ room.aiMatchScore }}% - {{ room.aiReasoning }}
                  </p>
                </el-card>
              </div>

              <!-- Route planning result -->
              <div v-if="aiResponse.distance">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="Distance">{{ aiResponse.distance }}</el-descriptions-item>
                  <el-descriptions-item label="Duration">{{ aiResponse.duration }}</el-descriptions-item>
                  <el-descriptions-item label="Traffic">{{ aiResponse.trafficStatus }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- Schedule conflict result -->
              <div v-if="aiResponse.hasConflict !== undefined">
                <el-alert
                    :title="aiResponse.message"
                    :type="aiResponse.hasConflict ? 'warning' : 'success'"
                    show-icon
                />
              </div>

              <!-- Raw JSON response (collapsible) -->
              <el-collapse style="margin-top: 10px;">
                <el-collapse-item title="Raw response">
                  <pre style="font-size: 11px; background: #f5f7fa; padding: 8px;">{{ JSON.stringify(aiResponse, null, 2) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { executeTool } from '@api/tool'
import { wsClient } from '@utils/websocket'

// Sub-components import
import MeetingAgent from './components/MeetingAgent.vue'
import ScheduleAgent from './components/ScheduleAgent.vue'
import RouteAgent from './components/RouteAgent.vue'

const router = useRouter()
const activeTab = ref('meeting')

// Component template refs
const meetingAgentRef = ref<any>(null)
const scheduleAgentRef = ref<any>(null)
const routeAgentRef = ref<any>(null)

// Extract date, capacity, time range from natural language
const extractFromQuery = (query: string) => {
  const dateMatch = query.match(/(\d{1,2})月(\d{1,2})[日号]/)
  const capMatch = query.match(/(\d+)[人个位]/)
  const rangeMatch = query.match(/(\d{1,2})月(\d{1,2})[日号][至到](\d{1,2})月(\d{1,2})[日号]/)

  let timeRange = null
  if (rangeMatch) {
    const start = `2026-${rangeMatch[1].padStart(2,'0')}-${rangeMatch[2].padStart(2,'0')}`
    const end = `2026-${rangeMatch[3].padStart(2,'0')}-${rangeMatch[4].padStart(2,'0')}`
    timeRange = `${start} to ${end}`
  }

  return {
    date: dateMatch ? `2026-${dateMatch[1].padStart(2,'0')}-${dateMatch[2].padStart(2,'0')}` : null,
    capacity: capMatch ? Number(capMatch[1]) : null,
    timeRange: timeRange
  }
}

// --- WebSocket AI assistant ---
const naturalQuery = ref('')
const aiResponse = ref<any>(null)

const taskProgress = ref(0)
const taskStatus = ref('')
const taskMessage = ref('Waiting for task to start...')
const isExecuting = ref(false)
let hasFetchedResult = false

const generateTaskId = () => 'task_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)

const executeWithWebSocket = async () => {
  if (!naturalQuery.value.trim()) {
    ElMessage.warning('Please enter search query')
    return
  }

  const taskId = generateTaskId()
  isExecuting.value = true
  hasFetchedResult = false
  taskProgress.value = 0
  taskStatus.value = 'connected'
  taskMessage.value = 'Connecting to server...'
  aiResponse.value = null

  wsClient.close?.()

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/ws/?taskId=${taskId}`
  wsClient.connect(wsUrl)

  wsClient.on('message', (rawData: any) => {
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData

    taskProgress.value = data.progress ?? 0
    taskStatus.value = data.status ?? ''
    taskMessage.value = data.message ?? ''

    if (data.status === 'completed' && !hasFetchedResult) {
      hasFetchedResult = true
      taskMessage.value = 'Fetching result...'
      fetchTaskResult().then(() => {
        taskProgress.value = 100
        isExecuting.value = false
        ElMessage.success('Task execution completed!')
      }).catch(() => {
        isExecuting.value = false
      })
    } else if (data.status === 'error') {
      isExecuting.value = false
      taskMessage.value = data.message || 'Execution error'
      ElMessage.error(data.message || 'Execution error')
    }
  })

  wsClient.on('error', () => {
    taskStatus.value = 'error'
    taskMessage.value = 'Connection error occurred'
    isExecuting.value = false
  })

  wsClient.on('open', () => {
    taskMessage.value = 'Connected, sending task...'
    wsClient.send(JSON.stringify({
      taskType: 'AI',
      query: naturalQuery.value,
      parameters: {}
    }))
  })
}

const fetchTaskResult = async () => {
  try {
    const res: any = await executeTool({
      toolType: 'AI',
      parameters: {},
      naturalLanguage: naturalQuery.value
    })

    const payload = res?.data ?? res
    aiResponse.value = payload
    if (!payload) {
      ElMessage.warning('AI returned empty result')
      return
    }

    const intentRaw = payload.aiParsed?.intent || payload.intent || ''
    const intent = intentRaw.toLowerCase()

    let targetTab = 'meeting'
    if (intent.includes('route') || intent.includes('路线') || intent.includes('path') || intent.includes('map')) {
      targetTab = 'route'
    } else if (intent.includes('schedule') || intent.includes('冲突') || intent.includes('日程') || intent.includes('conflict')) {
      targetTab = 'schedule'
    } else if (intent.includes('meeting') || intent.includes('会议室') || intent.includes('room') || intent.includes('预订')) {
      targetTab = 'meeting'
    }

    activeTab.value = targetTab

    if (targetTab === 'route') {
      const from = payload.from || payload.aiParsed?.from || 'Office'
      const to = payload.to || payload.aiParsed?.to || 'Airport'
      const mode = payload.mode || payload.aiParsed?.mode || 'driving'

      let path = payload.path || []
      let startPoint = payload.startPoint || []
      let endPoint = payload.endPoint || []

      if (path.length === 0) {
        if (payload.distance || payload.duration) {
          startPoint = payload.startPoint || [114.137, 22.283]
          endPoint = payload.endPoint || [113.915, 22.309]
          path = [
            [114.137, 22.283], [114.115, 22.315], [114.075, 22.335],
            [114.020, 22.345], [113.965, 22.298], [113.915, 22.309]
          ]
        }
      }

      routeAgentRef.value?.setRouteData({
        from, to, mode, path, startPoint, endPoint, result: payload
      })

    } else if (targetTab === 'schedule') {
      const extracted = extractFromQuery(naturalQuery.value)
      if (!aiResponse.value.aiParsed) aiResponse.value.aiParsed = {}
      aiResponse.value.aiParsed.date = 'N/A'
      aiResponse.value.aiParsed.timeRange = extracted.timeRange || 'N/A'
      aiResponse.value.aiParsed.capacity = 'N/A'
      aiResponse.value.aiParsed.equipment = null

      let timeRange: Date[] = []
      let attendees: string[] = []

      if (extracted.timeRange) {
        const parts = extracted.timeRange.split(' to ')
        if (parts.length === 2) {
          timeRange = [new Date(parts[0] + 'T00:00:00'), new Date(parts[1] + 'T00:00:00')]
        }
      }

      const userMatch = naturalQuery.value.match(/(admin|user|zhangsan|lisi|张三|李四)/i)
      if (userMatch) {
        const userMap: Record<string, string> = {
          'admin': 'admin', 'user': 'user', 'zhangsan': 'zhangsan', '张三': 'zhangsan', 'lisi': 'lisi', '李四': 'lisi'
        }
        attendees = [userMap[userMatch[0].toLowerCase()] || userMatch[0]]
      }

      scheduleAgentRef.value?.setScheduleData({ timeRange, attendees })

      if (timeRange.length === 2 && attendees.length > 0) {
        await scheduleAgentRef.value?.checkConflict()
        if (aiResponse.value && scheduleAgentRef.value?.conflictResult) {
          aiResponse.value.hasConflict = scheduleAgentRef.value.conflictResult.hasConflict
          aiResponse.value.message = scheduleAgentRef.value.conflictResult.message
        }
      }

    } else if (targetTab === 'meeting') {
      const extracted = extractFromQuery(naturalQuery.value)
      const updateData: any = {}
      if (extracted.date) updateData.date = new Date(extracted.date + 'T00:00:00')
      if (extracted.capacity) updateData.capacity = extracted.capacity

      meetingAgentRef.value?.setMeetingData(updateData)
      await meetingAgentRef.value?.queryMeetingRooms()

      if (aiResponse.value) {
        if (!aiResponse.value.aiParsed) aiResponse.value.aiParsed = {}
        aiResponse.value.aiParsed.date = extracted.date || aiResponse.value.aiParsed.date || 'Today'
        aiResponse.value.aiParsed.capacity = extracted.capacity ? String(extracted.capacity) : (aiResponse.value.aiParsed.capacity || 'Not specified')
        
        aiResponse.value.rooms = (meetingAgentRef.value?.meetingRooms || []).map((room: any) => ({
          id: room.id,
          name: room.name,
          capacity: room.capacity,
          location: room.location,
          equipment: room.equipment,
          available: room.available,
          aiMatchScore: room.available ? 100 : 0,
          aiReasoning: room.available ? 'Matches criteria' : 'Unavailable for this period'
        }))
      }
    }
    ElMessage.success(`AI recognized as "${targetTab}" intent, result loaded`)
  } catch (error) {
    console.error('fetchTaskResult error:', error)
    ElMessage.error('Failed to get AI result')
  }
}

onUnmounted(() => {
  wsClient.close?.()
})
</script>

<style scoped>
.tool-container {
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
.page-title { font-size: 24px; font-weight: 700; color: #111827; margin: 0 0 6px 0; }
.page-sub { font-size: 14px; color: #9ca3af; margin: 0; }
:deep(.el-tabs--border-card) { background: #ffffff; border: 1px solid #f0f0f0; border-radius: 16px; box-shadow: 0 4px 24px rgba(0,0,0,0.015); overflow: hidden; height: 100%; display: flex; flex-direction: column; }
:deep(.el-tabs--border-card > .el-tabs__header) { background-color: #f9fafb; border-bottom: 1px solid #f3f4f6; padding: 0 12px; }
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) { color: #6b7280; font-weight: 500; font-size: 13.5px; height: 48px; line-height: 48px; transition: all 0.2s; border: none !important; margin: 0 4px; border-bottom: 2px solid transparent !important; }
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) { color: #111827; background-color: transparent !important; font-weight: 600; border-bottom: 2px solid #111827 !important; }
:deep(.el-tabs__content) { padding: 24px; flex-grow: 1; }
.tool-tabs { height: 100%; min-height: 600px; box-sizing: border-box; }
.ai-card { border-radius: 16px; border: 1px solid #f0f0f0; box-shadow: 0 4px 24px rgba(0,0,0,0.015); overflow: hidden; height: 100%; display: flex; flex-direction: column; box-sizing: border-box; }
:deep(.ai-card .el-card__header) { background: #f9fafb; border-bottom: 1px solid #f3f4f6; padding: 16px 20px; }
:deep(.ai-card .el-card__body) { flex-grow: 1; display: flex; flex-direction: column; overflow-y: auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-size: 14px; font-weight: 600; color: #111827; }
.chat-mode { display: flex; flex-direction: column; }
.chat-mode :deep(.el-textarea__inner) { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; font-size: 13.5px; transition: all 0.15s; padding: 12px; line-height: 1.5; }
.chat-mode :deep(.el-textarea__inner:focus) { border-color: #111827; background: #fff; box-shadow: 0 0 0 3px rgba(17,24,39,0.08) !important; }
.progress-section { margin-top: 18px; }
.progress-info { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; }
.status-label { color: #6b7280; font-weight: 500; }
.progress-percent { font-weight: 600; color: #111827; }
:deep(.el-progress-bar__inner) { background-color: #111827 !important; }
.ai-result { margin-top: 18px; }
.ai-result h4 { margin: 0 0 12px 0; font-size: 14px; font-weight: 600; color: #111827; letter-spacing: -0.2px; }
.ai-result h5 { margin: 18px 0 8px; font-size: 13px; font-weight: 600; color: #374151; }
:deep(.el-descriptions) { border-radius: 12px; overflow: hidden; border: 1px solid #f0f0f0; }
:deep(.el-descriptions__label) { background: #f9fafb !important; font-weight: 600; color: #4b5563; width: 100px; }
:deep(.el-descriptions__content) { color: #111827; }
.room-card { margin-bottom: 8px; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.01); transition: all 0.2s; padding: 4px; }
.room-card:hover { border-color: #cbd5e1; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
:deep(.el-collapse) { border: 1px solid #f0f0f0; border-radius: 12px; overflow: hidden; }
:deep(.el-collapse-item__header) { padding: 0 16px; font-size: 13px; font-weight: 500; color: #6b7280; background-color: #f9fafb; }
:deep(.el-collapse-item__content) { padding: 16px; background: #ffffff; }
:deep(.el-button--primary) { background-color: #111827 !important; border: none !important; border-radius: 10px !important; height: 42px; font-weight: 500; transition: all 0.15s; padding: 10px 20px; }
:deep(.el-button--primary:hover) { opacity: 0.88; transform: translateY(-1px); }
:deep(.el-button--primary:active) { transform: translateY(0); }
</style>
