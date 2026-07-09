<template>
  <div class="tool-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('tool.pageTitle') }}</h1>
        <p class="page-sub">{{ $t('tool.pageSub') }}</p>
      </div>
    </div>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- Full width panel: feature tabs -->
      <el-col :xs="24" :sm="24" :md="24">
        <el-tabs v-model="activeTab" type="border-card" class="tool-tabs">
          <!-- Meeting room booking -->
          <el-tab-pane :label="$t('tool.tabs.meeting')" name="meeting">
            <MeetingAgent ref="meetingAgentRef" />
          </el-tab-pane>

          <!-- Schedule conflict detection -->
          <el-tab-pane :label="$t('tool.tabs.schedule')" name="schedule">
            <ScheduleAgent ref="scheduleAgentRef" />
          </el-tab-pane>

          <!-- Route planning -->
          <el-tab-pane :label="$t('tool.tabs.route')" name="route">
            <RouteAgent ref="routeAgentRef" />
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

// Sub-components import
import MeetingAgent from './components/MeetingAgent.vue'
import ScheduleAgent from './components/ScheduleAgent.vue'
import RouteAgent from './components/RouteAgent.vue'

const { t } = useI18n()
const activeTab = ref('meeting')

// Component template refs
const meetingAgentRef = ref<any>(null)
const scheduleAgentRef = ref<any>(null)
const routeAgentRef = ref<any>(null)

// Extract standard yyyy-MM-dd date from English months text
const tryParseEnglishDate = (str: string): string | null => {
  const months = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december']
  const shortMonths = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']
  
  const text = str.toLowerCase()
  let monthIndex = -1
  let day = -1
  
  for (let i = 0; i < 12; i++) {
    if (text.includes(months[i])) {
      monthIndex = i
      break
    }
  }
  
  if (monthIndex === -1) {
    for (let i = 0; i < 12; i++) {
      if (text.includes(shortMonths[i])) {
        monthIndex = i
        break
      }
    }
  }
  
  if (monthIndex !== -1) {
    const dayMatch = text.match(/\b(\d{1,2})(?:st|nd|rd|th)?\b/)
    if (dayMatch) {
      day = Number(dayMatch[1])
    }
  }
  
  if (monthIndex !== -1 && day !== -1) {
    const year = 2026
    const mStr = String(monthIndex + 1).padStart(2, '0')
    const dStr = String(day).padStart(2, '0')
    return `${year}-${mStr}-${dStr}`
  }
  
  return null
}

// Extract date, capacity, time range from natural language
const extractFromQuery = (query: string) => {
  // Chinese matches
  const dateMatchCh = query.match(/(\d{1,2})月(\d{1,2})[日号]/)
  const capMatchCh = query.match(/(\d+)[人个位]/)
  const rangeMatchCh = query.match(/(\d{1,2})月(\d{1,2})[日号][至到](\d{1,2})月(\d{1,2})[日号]/)

  // English matches
  const capMatchEn = query.match(/\b(\d+)\s*(people|person|users?|pax)\b/i)
  const timeRangeMatchEn = query.match(/\b(\d{1,2}):(\d{2})\s*(?:-|to)\s*(\d{1,2}):(\d{2})\b/i)

  const standardDate = tryParseEnglishDate(query)

  let date = null
  if (dateMatchCh) {
    date = `2026-${dateMatchCh[1].padStart(2,'0')}-${dateMatchCh[2].padStart(2,'0')}`
  } else if (standardDate) {
    date = standardDate
  }

  let capacity = null
  if (capMatchCh) {
    capacity = Number(capMatchCh[1])
  } else if (capMatchEn) {
    capacity = Number(capMatchEn[1])
  }

  let timeRange = null
  if (rangeMatchCh) {
    const start = `2026-${rangeMatchCh[1].padStart(2,'0')}-${rangeMatchCh[2].padStart(2,'0')}`
    const end = `2026-${rangeMatchCh[3].padStart(2,'0')}-${rangeMatchCh[4].padStart(2,'0')}`
    timeRange = `${start} to ${end}`
  } else if (timeRangeMatchEn) {
    const sh = timeRangeMatchEn[1].padStart(2, '0')
    const sm = timeRangeMatchEn[2]
    const eh = timeRangeMatchEn[3].padStart(2, '0')
    const em = timeRangeMatchEn[4]
    timeRange = `${sh}:${sm} to ${eh}:${em}`
  }

  return {
    date,
    capacity,
    timeRange
  }
}

// --- Event binding to receive results from Global Copilot ---
const handleCopilotToolResult = async (event: Event) => {
  const customEvent = event as CustomEvent
  const { payload, query } = customEvent.detail || {}
  if (!payload) return

  try {
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
      const extracted = extractFromQuery(query || '')
      const aiParams = payload.aiParsed?.parameters || {}
      
      const parsedTimeRangeStr = aiParams.timeRange || extracted.timeRange || t('tool.ai.unspecified')
      const parsedAttendees = aiParams.attendees || []

      let timeRange: Date[] = []
      let attendees: string[] = parsedAttendees

      if (parsedTimeRangeStr && parsedTimeRangeStr !== t('tool.ai.unspecified')) {
        const parts = parsedTimeRangeStr.split(' to ')
        if (parts.length === 2) {
          timeRange = [new Date(parts[0] + 'T00:00:00'), new Date(parts[1] + 'T00:00:00')]
        }
      }

      if (attendees.length === 0 && query) {
        const userMatch = query.match(/(admin|user|zhangsan|lisi|张三|李四)/i)
        if (userMatch) {
          const userMap: Record<string, string> = {
            'admin': 'admin', 'user': 'user', 'zhangsan': 'zhangsan', '张三': 'zhangsan', 'lisi': 'lisi', '李四': 'lisi'
          }
          attendees = [userMap[userMatch[0].toLowerCase()] || userMatch[0]]
        }
      }

      scheduleAgentRef.value?.setScheduleData({ timeRange, attendees })

      if (timeRange.length === 2 && attendees.length > 0) {
        await scheduleAgentRef.value?.checkConflict()
      }

    } else if (targetTab === 'meeting') {
      const extracted = extractFromQuery(query || '')
      const aiParams = payload.aiParsed?.parameters || {}
      
      const updateData: any = {}
      const aiDate = aiParams.date || extracted.date
      let finalDateStr = extracted.date
      
      if (aiDate) {
        if (aiDate.toLowerCase() === 'today') {
          finalDateStr = new Date().toISOString().split('T')[0]
        } else if (aiDate.toLowerCase() === 'tomorrow') {
          const tom = new Date()
          tom.setDate(tom.getDate() + 1)
          finalDateStr = tom.toISOString().split('T')[0]
        } else if (/^\d{4}-\d{2}-\d{2}$/.test(aiDate)) {
          finalDateStr = aiDate
        } else {
          const parsed = tryParseEnglishDate(aiDate)
          finalDateStr = parsed || aiDate
        }
      }
      
      const finalCapacity = aiParams.capacity ? Number(aiParams.capacity) : extracted.capacity

      if (finalDateStr) updateData.date = new Date(finalDateStr + 'T00:00:00')
      if (finalCapacity) updateData.capacity = finalCapacity

      let finalStartTime = '09:00'
      let finalEndTime = '11:00'
      const parsedTimeRangeStr = aiParams.timeRange || extracted.timeRange
      if (parsedTimeRangeStr && parsedTimeRangeStr.includes(' to ')) {
        const parts = parsedTimeRangeStr.split(' to ')
        if (parts.length === 2) {
          finalStartTime = parts[0]
          finalEndTime = parts[1]
        }
      }
      updateData.startTime = finalStartTime
      updateData.endTime = finalEndTime

      meetingAgentRef.value?.setMeetingData(updateData)
      await meetingAgentRef.value?.queryMeetingRooms()
    }
  } catch (error) {
    console.error('Failed to sync Copilot tool result:', error)
  }
}

onMounted(() => {
  window.addEventListener('copilot-tool-result', handleCopilotToolResult)
})

onUnmounted(() => {
  window.removeEventListener('copilot-tool-result', handleCopilotToolResult)
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
</style>
