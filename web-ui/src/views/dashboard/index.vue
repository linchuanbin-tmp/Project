<template>
  <div class="dashboard">

    <!-- Page header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('dashboard.title') }}</h1>
        <p class="page-sub">{{ $t('dashboard.welcome', { user: userStore.userInfo?.realName || userStore.userInfo?.username || 'User' }) }}</p>
      </div>
    </div>

    <!-- Admin Pending Approvals Banner -->
    <div v-if="isAdminOrDeptAdmin && pendingCount > 0" class="admin-alert-banner">
      <div class="banner-left">
        <AlertTriangle :size="16" class="alert-icon" />
        <div class="banner-text">
          <span class="banner-title">{{ $t('dashboard.pendingApprovals') }}</span>
          <span class="banner-desc">{{ $t('dashboard.pendingDesc', { count: pendingCount }) }}</span>
        </div>
      </div>
      <button class="banner-btn" @click="router.push('/app/notification')">
        {{ $t('dashboard.reviewNow') }}
      </button>
    </div>

    <!-- Row 1: Stat cards -->
    <div class="stat-cards-row">
      <div class="stat-card stat-card--spark" @click="router.push('/app/my-tasks')">
        <div class="stat-card-inner">
          <div class="stat-icon-box" style="background: #eff6ff;">
            <ClipboardList :size="16" :stroke-width="1.6" color="#3b82f6" />
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ taskStats.total || 0 }}</span>
            <span class="stat-label">{{ $t('dashboard.todayTasks') }}</span>
          </div>
          <div class="stat-spark">
            <Sparkline :points="taskSparkline" color="#3b82f6" />
            <el-tooltip placement="top" effect="light" :show-after="300" :hide-after="0">
              <template #content>
                <div class="spark-tooltip-pop">
                  <div v-for="(p, i) in taskSparkline" :key="i" class="spark-tooltip-row">
                    <span class="spark-tooltip-label">{{ p.label }}</span>
                    <span class="spark-tooltip-val">{{ p.value }}</span>
                  </div>
                </div>
              </template>
              <div class="spark-hit-area"></div>
            </el-tooltip>
          </div>
        </div>
      </div>

      <div class="stat-card stat-card--spark" @click="router.push('/app/my-schedules')">
        <div class="stat-card-inner">
          <div class="stat-icon-box" style="background: #f0fdf4;">
            <CalendarDays :size="16" :stroke-width="1.6" color="#22c55e" />
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ upcomingScheduleCount }}</span>
            <span class="stat-label">{{ $t('dashboard.upcomingEvents') }}</span>
          </div>
          <div class="stat-spark">
            <Sparkline :points="scheduleSparkline" color="#22c55e" />
            <el-tooltip placement="top" effect="light" :show-after="300" :hide-after="0">
              <template #content>
                <div class="spark-tooltip-pop">
                  <div v-for="(p, i) in scheduleSparkline" :key="i" class="spark-tooltip-row">
                    <span class="spark-tooltip-label">{{ p.label }}</span>
                    <span class="spark-tooltip-val">{{ p.value }}</span>
                  </div>
                </div>
              </template>
              <div class="spark-hit-area"></div>
            </el-tooltip>
          </div>
        </div>
      </div>

      <div class="stat-card stat-card--spark" @click="router.push('/app/notification')">
        <div class="stat-card-inner">
          <div class="stat-icon-box" style="background: #fff7ed;">
            <Bell :size="16" :stroke-width="1.6" color="#f97316" />
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ unreadCount }}</span>
            <span class="stat-label">{{ $t('dashboard.unreadMessages') }}</span>
          </div>
          <div class="stat-spark">
            <Sparkline :points="messageSparkline" color="#f97316" />
            <el-tooltip placement="top" effect="light" :show-after="300" :hide-after="0">
              <template #content>
                <div class="spark-tooltip-pop">
                  <div v-for="(p, i) in messageSparkline" :key="i" class="spark-tooltip-row">
                    <span class="spark-tooltip-label">{{ p.label }}</span>
                    <span class="spark-tooltip-val">{{ p.value }}</span>
                  </div>
                </div>
              </template>
              <div class="spark-hit-area"></div>
            </el-tooltip>
          </div>
        </div>
      </div>

      <div v-if="isAdminOrDeptAdmin" class="stat-card stat-card--spark" @click="router.push('/app/notification')">
        <div class="stat-card-inner">
          <div class="stat-icon-box" style="background: #fef2f2;">
            <AlertTriangle :size="16" :stroke-width="1.6" color="#ef4444" />
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ pendingCount }}</span>
            <span class="stat-label">{{ $t('dashboard.pendingReviews') }}</span>
          </div>
          <div class="stat-spark" v-if="pendingSparkline.length">
            <Sparkline :points="pendingSparkline" color="#ef4444" />
            <el-tooltip placement="top" effect="light" :show-after="300" :hide-after="0">
              <template #content>
                <div class="spark-tooltip-pop">
                  <div v-for="(p, i) in pendingSparkline" :key="i" class="spark-tooltip-row">
                    <span class="spark-tooltip-label">{{ p.label }}</span>
                    <span class="spark-tooltip-val">{{ p.value }}</span>
                  </div>
                </div>
              </template>
              <div class="spark-hit-area"></div>
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>

    <!-- Row 2: Calendar + Messages -->
    <div class="content-grid">
      <!-- Left: Mini Calendar + Daily Events -->
      <div class="content-card calendar-card">
        <div class="card-header">
          <h3 class="card-title">
            <Calendar :size="14" :stroke-width="1.8" />
            {{ $t('dashboard.scheduleTimeline') }}
          </h3>
          <span class="card-link" @click="router.push('/app/my-schedules')">View all &rarr;</span>
        </div>
        <div class="card-body cal-layout">
          <!-- Mini calendar -->
          <div class="cal-mini">
            <div class="calendar-nav">
              <button class="cal-nav-btn" @click="prevMonth"><ChevronLeft :size="12" :stroke-width="1.8" /></button>
              <span class="cal-month-label">{{ calendarMonthLabel }}</span>
              <button class="cal-nav-btn" @click="nextMonth"><ChevronRight :size="12" :stroke-width="1.8" /></button>
            </div>
            <div class="cal-dow-row">
              <span v-for="d in dayOfWeekLabels" :key="d" class="cal-dow">{{ d }}</span>
            </div>
            <div class="cal-grid">
              <button
                  v-for="(cell, idx) in calendarCells"
                  :key="idx"
                  class="cal-day"
                  :class="{
                    'cal-day--other': !cell.isCurrentMonth,
                    'cal-day--today': cell.isToday,
                    'cal-day--selected': selectedDate === cell.dateStr,
                    'cal-day--has-events': cell.hasEvents,
                  }"
                  :disabled="!cell.isCurrentMonth"
                  @click="selectDate(cell)"
              >
                <span class="cal-day-num">{{ cell.day }}</span>
                <span v-if="cell.hasEvents" class="cal-day-dot"></span>
              </button>
            </div>
          </div>
          <!-- Daily events -->
          <div class="cal-daily" :class="{ 'cal-daily--hidden': !selectedDate }">
            <div class="cal-events-date">{{ selectedDateLabel || 'Select a date' }}</div>
            <div v-if="selectedDateEvents.length > 0" class="cal-events-list">
              <div v-for="ev in selectedDateEvents" :key="ev.id" class="cal-event-item">
                <div class="cal-event-time">{{ formatTime(ev.startTime) }}</div>
                <div class="cal-event-content">
                  <span class="cal-event-topic">{{ ev.topic || 'Untitled' }}</span>
                  <span class="cal-event-meta">
                    {{ ev.roomId && ev.roomId !== 0 ? ev.roomName || 'Room' : 'Personal' }} &middot; {{ calcDuration(ev.startTime, ev.endTime) }}
                  </span>
                </div>
              </div>
            </div>
            <div v-else-if="selectedDate" class="cal-events-empty">
              No events on this day
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Messages Panel -->
      <div class="content-card messages-card">
        <div class="card-header">
          <h3 class="card-title">
            <Bell :size="14" :stroke-width="1.8" />
            {{ $t('dashboard.recentMessages') }}
          </h3>
          <span class="card-link" @click="router.push('/app/notification')">View all &rarr;</span>
        </div>
        <div class="card-body">
          <div v-if="recentMessages.length === 0" class="card-empty-compact">
            <Inbox :size="18" class="empty-icon" />
            <p class="empty-title">{{ $t('dashboard.noMessages') }}</p>
          </div>
          <div v-else class="messages-list">
            <div
                v-for="msg in recentMessages"
                :key="msg.id"
                class="message-item"
                :class="{ unread: msg.status === 1 }"
                @click="router.push('/app/notification')"
            >
              <div class="msg-avatar" :style="{ background: msg.avatarBg }">
                {{ msg.avatarInitial }}
              </div>
              <div class="msg-content">
                <div class="msg-top-row">
                  <span class="msg-sender">{{ msg.senderName }}</span>
                  <span class="msg-tag" :style="{ background: msg.tagBg, color: msg.tagColor }">{{ msg.typeLabel }}</span>
                  <span class="msg-time">{{ msg.timeAgo }}</span>
                </div>
                <span class="msg-title">{{ msg.title }}</span>
                <span class="msg-body">{{ msg.bodyPreview }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Row 3: Recent Usage + Recent Documents -->
    <div class="bottom-row">
      <div class="recent-usage-card">
        <h3 class="section-label">
          <Clock :size="14" :stroke-width="1.8" />
          {{ $t('dashboard.recentUsage') }}
        </h3>
        <div class="recent-usage-grid">
          <button class="usage-item" @click="router.push('/app/tool')">
            <div class="usage-icon" style="background: #f0f9ff; color: #0ea5e9;"><Building :size="15" :stroke-width="1.8" /></div>
            <div class="usage-text">
              <span class="usage-name">{{ $t('dashboard.bookRoom') }}</span>
              <span class="usage-desc">{{ $t('dashboard.usageBookRoom') }}</span>
            </div>
          </button>
          <button class="usage-item" @click="router.push('/app/code')">
            <div class="usage-icon" style="background: #f0fdf4; color: #22c55e;"><FileText :size="15" :stroke-width="1.8" /></div>
            <div class="usage-text">
              <span class="usage-name">{{ $t('dashboard.sqlQuery') }}</span>
              <span class="usage-desc">{{ $t('dashboard.usageSqlQuery') }}</span>
            </div>
          </button>
          <button class="usage-item" @click="router.push('/app/rag')">
            <div class="usage-icon" style="background: #fdf4ff; color: #a855f7;"><BookOpen :size="15" :stroke-width="1.8" /></div>
            <div class="usage-text">
              <span class="usage-name">{{ $t('dashboard.knowledgeSearch') }}</span>
              <span class="usage-desc">{{ $t('dashboard.usageKnowledgeSearch') }}</span>
            </div>
          </button>
          <button class="usage-item" @click="router.push('/app/my-schedules')">
            <div class="usage-icon" style="background: #fff7ed; color: #f97316;"><CalendarDays :size="15" :stroke-width="1.8" /></div>
            <div class="usage-text">
              <span class="usage-name">{{ $t('dashboard.mySchedules') }}</span>
              <span class="usage-desc">{{ $t('dashboard.usageMySchedules') }}</span>
            </div>
          </button>
        </div>
      </div>
      <div class="recent-docs-section">
        <h3 class="section-label">
          <FolderOpen :size="14" :stroke-width="1.8" />
          {{ $t('dashboard.recentDocs') }}
        </h3>
        <div class="recent-docs-list" v-if="recentDocuments.length > 0">
          <div
              v-for="doc in recentDocuments"
              :key="doc.id"
              class="recent-doc-item"
              @click="router.push('/app/dept-docs')"
          >
            <span class="recent-doc-title">{{ doc.title }}</span>
            <span class="recent-doc-time">{{ timeAgo(doc.createTime) }}</span>
          </div>
        </div>
        <span v-else class="recent-docs-empty">{{ $t('dashboard.noDocs') }}</span>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { getUnreadCount } from '@/api/notification'
import { getMyTasks } from '@/api/task'
import request from '@utils/request'
import {
  AlertTriangle, Calendar, RefreshCw, MapPin,
  Building, FileText, BookOpen, CalendarDays, Bell,
  ClipboardList, Inbox, ChevronLeft, ChevronRight, FolderOpen, Clock
} from 'lucide-vue-next'

const userStore = useUserStore()
const router = useRouter()
const { t } = useI18n()

const pendingCount = ref(0)
const unreadCount = ref(0)
const loadingSchedules = ref(false)
const allSchedules = ref<any[]>([])

const isAdminOrDeptAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_DEPT_ADMIN')
})

// ── Task Stats ──
const taskStats = ref({ total: 0, running: 0, success: 0, failed: 0 })
const taskSparkline = ref<Array<{ label: string; value: number }>>([])

const fetchTaskData = async () => {
  try {
    const res: any = await getMyTasks()
    const tasks = Array.isArray(res) ? res : (res?.data || res?.records || [])
    const today = new Date().toISOString().split('T')[0]
    const todayTasks = tasks.filter((t: any) => t.createdAt && t.createdAt.startsWith(today))
    taskStats.value = {
      total: todayTasks.length,
      running: todayTasks.filter((t: any) => t.status === 'RUNNING').length,
      success: todayTasks.filter((t: any) => t.status === 'SUCCESS').length,
      failed: todayTasks.filter((t: any) => t.status === 'FAIL').length,
    }

    // Build 4-day sparkline
    taskSparkline.value = buildSparkline(tasks, 'createdAt')
  } catch (e) {
    console.error('Failed to fetch tasks', e)
  }
}

// ── Schedules ──
const scheduleSparkline = ref<Array<{ label: string; value: number }>>([])

const fetchSchedules = async () => {
  loadingSchedules.value = true
  try {
    const res: any = await request.get('/tool/my-schedules')
    allSchedules.value = res || []
    scheduleSparkline.value = buildSparkline(allSchedules.value, 'startTime')
  } catch (e) {
    console.error('Failed to fetch schedules', e)
  } finally {
    loadingSchedules.value = false
  }
}

const upcomingScheduleCount = computed(() => {
  const now = Date.now()
  return allSchedules.value.filter((s: any) => new Date(s.endTime).getTime() >= now).length
})

// ── Apple-style Calendar ──
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth()) // 0-based
const selectedDate = ref<string | null>(null)

const dayOfWeekLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const calendarMonthLabel = computed(() => {
  const d = new Date(calendarYear.value, calendarMonth.value, 1)
  return d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
})

const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return ''
  const d = new Date(selectedDate.value)
  return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
})

const prevMonth = () => {
  if (calendarMonth.value === 0) {
    calendarMonth.value = 11
    calendarYear.value--
  } else {
    calendarMonth.value--
  }
}

const nextMonth = () => {
  if (calendarMonth.value === 11) {
    calendarMonth.value = 0
    calendarYear.value++
  } else {
    calendarMonth.value++
  }
}

const calendarCells = computed(() => {
  const year = calendarYear.value
  const month = calendarMonth.value
  const firstDay = new Date(year, month, 1)
  // Monday = 0 ... Sunday = 6
  let startDow = firstDay.getDay() - 1
  if (startDow < 0) startDow = 6

  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const daysInPrevMonth = new Date(year, month, 0).getDate()

  const today = new Date().toISOString().split('T')[0]
  const cells = []

  // Build a map: date -> hasEvents
  const eventDateSet = new Set<string>()
  for (const s of allSchedules.value) {
    if (s.startTime) {
      eventDateSet.add(s.startTime.split('T')[0])
    }
  }

  // Previous month fill
  for (let i = startDow - 1; i >= 0; i--) {
    const d = daysInPrevMonth - i
    const m = month === 0 ? 12 : month
    const y = month === 0 ? year - 1 : year
    const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({ day: d, isCurrentMonth: false, isToday: false, dateStr, hasEvents: eventDateSet.has(dateStr) })
  }

  // Current month
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({
      day: d,
      isCurrentMonth: true,
      isToday: dateStr === today,
      dateStr,
      hasEvents: eventDateSet.has(dateStr),
    })
  }

  // Next month fill to complete the last week
  const remaining = 7 - (cells.length % 7)
  if (remaining < 7) {
    for (let d = 1; d <= remaining; d++) {
      const m = month === 11 ? 1 : month + 2
      const y = month === 11 ? year + 1 : year
      const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
      cells.push({ day: d, isCurrentMonth: false, isToday: false, dateStr, hasEvents: eventDateSet.has(dateStr) })
    }
  }

  return cells
})

const selectedDateEvents = computed(() => {
  if (!selectedDate.value) return []
  return allSchedules.value
    .filter((s: any) => s.startTime && s.startTime.startsWith(selectedDate.value!))
    .sort((a: any, b: any) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())
})

const selectDate = (cell: any) => {
  selectedDate.value = cell.dateStr
}

// Auto-select today on load
watch(() => allSchedules.value, () => {
  const today = new Date().toISOString().split('T')[0]
  if (!selectedDate.value) selectedDate.value = today
})

const formatTime = (timeStr: string) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const calcDuration = (s: string, e: string) => {
  if (!s || !e) return ''
  const m = Math.round((new Date(e).getTime() - new Date(s).getTime()) / 60000)
  if (m >= 60) {
    const h = Math.floor(m / 60)
    const rem = m % 60
    return rem > 0 ? `${h}h ${rem}m` : `${h}h`
  }
  return `${m}m`
}

// ── Recent Messages ──
const recentMessages = ref<any[]>([])
const messageSparkline = ref<Array<{ label: string; value: number }>>([])
const pendingSparkline = ref<Array<{ label: string; value: number }>>([])

const TYPE_CONFIG: Record<string, { label: string; tagBg: string; tagColor: string; avatarBg: string }> = {
  MEETING:    { label: 'Meeting',     tagBg: '#f0f9ff', tagColor: '#0ea5e9', avatarBg: '#e0f2fe' },
  NOTICE:     { label: 'Notice',      tagBg: '#fff7ed', tagColor: '#f97316', avatarBg: '#ffedd5' },
  APPROVAL:   { label: 'Approval',    tagBg: '#fef2f2', tagColor: '#ef4444', avatarBg: '#fee2e2' },
  MESSAGE:    { label: 'Message',     tagBg: '#f0fdf4', tagColor: '#16a34a', avatarBg: '#dcfce7' },
}

const fetchRecentMessages = async () => {
  try {
    const res: any = await request.get('/user/notification/list')
    const list = Array.isArray(res) ? res : (res?.data || res?.records || [])
    messageSparkline.value = buildSparkline(list, 'createTime')
    pendingSparkline.value = buildSparkline(list.filter((m: any) => m.status === 2), 'createTime')
    const sorted = list
      .sort((a: any, b: any) => new Date(b.createTime || b.createdAt || 0).getTime() - new Date(a.createTime || a.createdAt || 0).getTime())
      .slice(0, 3)

    recentMessages.value = sorted.map((m: any) => {
      const cfg = TYPE_CONFIG[m.notifyType] || TYPE_CONFIG.MESSAGE
      const senderName = m.senderRealName || m.senderName || 'System'
      const avatarInitial = senderName === 'System' ? 'S' : (senderName).charAt(0).toUpperCase()

      // Build body preview from content (strip tags, truncate)
      const plainContent = (m.content || '').replace(/<[^>]*>/g, '').replace(/\\n/g, ' ').trim()
      const bodyPreview = plainContent.length > 80 ? plainContent.slice(0, 80) + '...' : plainContent

      return {
        ...m,
        senderName,
        avatarInitial,
        avatarBg: cfg.avatarBg,
        typeLabel: cfg.label,
        tagBg: cfg.tagBg,
        tagColor: cfg.tagColor,
        bodyPreview: bodyPreview || 'No content',
      }
    })
  } catch (e) {
    console.error('Failed to fetch messages', e)
  }
}

// ── Pending count ──
const fetchPendingCount = async () => {
  if (isAdminOrDeptAdmin.value) {
    try {
      const res: any = await request.get('/user/notification/list', { params: { status: 2 } })
      const list = res
      pendingCount.value = Array.isArray(list) ? list.length : 0
    } catch (e) {
      console.error('Failed to fetch pending count', e)
    }
  }
}

const fetchUnreadCount = async () => {
  try {
    const count = await getUnreadCount()
    unreadCount.value = typeof count === 'number' ? count : (count as any) || 0
  } catch (e) {
    console.error('Failed to fetch unread count', e)
  }
}

const timeAgo = (dateStr: string) => {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return t('dashboard.justNow')
  if (mins < 60) return t('dashboard.minutesAgo', { n: mins })
  const hours = Math.floor(mins / 60)
  if (hours < 24) return t('dashboard.hoursAgo', { n: hours })
  const days = Math.floor(hours / 24)
  return t('dashboard.daysAgo', { n: days })
}

const buildSparkline = (items: any[], dateField: string) => {
  const days: Array<{ label: string; value: number }> = []
  for (let i = 3; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000)
    const dateStr = d.toISOString().split('T')[0]
    const label = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    const count = items.filter((item: any) => {
      const itemDate = item[dateField]
      return itemDate && itemDate.startsWith(dateStr)
    }).length
    days.push({ label, value: count })
  }
  return days
}

// ── Recent Documents ──
const recentDocuments = ref<any[]>([])

const fetchRecentDocuments = async () => {
  try {
    const res: any = await request.get('/user/document/list')
    const docs = Array.isArray(res) ? res : (res?.data || res?.records || [])
    recentDocuments.value = docs
      .sort((a: any, b: any) => new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime())
      .slice(0, 3)
  } catch (e) {
    console.error('Failed to fetch documents', e)
  }
}

onMounted(() => {
  fetchPendingCount()
  fetchUnreadCount()
  fetchTaskData()
  fetchSchedules()
  fetchRecentMessages()
  fetchRecentDocuments()
})
</script>

<script lang="ts">
import { computed } from 'vue'
import { defineComponent, h } from 'vue'

// Inline Sparkline mini component
const Sparkline = defineComponent({
  name: 'Sparkline',
  props: {
    points: { type: Array as () => Array<{ label: string; value: number }>, default: () => [] },
    color: { type: String, default: '#3b82f6' },
  },
  setup(props) {
    const pathD = computed(() => {
      const pts = props.points
      if (pts.length < 2) return ''
      const values = pts.map(p => p.value)
      const max = Math.max(...values, 1)
      const w = 44
      const h = 20
      const pad = 2
      const stepX = (w - pad * 2) / (pts.length - 1)
      const scaleY = (h - pad * 2) / max
      const coords = pts.map((p, i) => ({
        x: pad + i * stepX,
        y: h - pad - (Math.max(p.value, 0) * scaleY),
      }))
      if (coords.length === 2) {
        return `M ${coords[0].x} ${coords[0].y} L ${coords[1].x} ${coords[1].y}`
      }
      // Smooth cubic bezier
      let d = `M ${coords[0].x} ${coords[0].y}`
      for (let i = 1; i < coords.length; i++) {
        const prev = coords[i - 1]
        const curr = coords[i]
        const cpx1 = prev.x + (curr.x - prev.x) * 0.5
        const cpx2 = curr.x - (curr.x - prev.x) * 0.5
        d += ` C ${cpx1} ${prev.y}, ${cpx2} ${curr.y}, ${curr.x} ${curr.y}`
      }
      return d
    })
    const areaD = computed(() => {
      if (pathD.value.length === 0) return ''
      return `${pathD.value} L 42 20 L 2 20 Z`
    })
    return () => h('svg', {
      width: 44, height: 20, viewBox: '0 0 44 20',
      style: { display: 'block' },
    }, [
      h('defs', {}, [
        h('linearGradient', { id: `spark-fill-${props.color.replace('#','')}`, x1: '0', y1: '0', x2: '0', y2: '1' }, [
          h('stop', { offset: '0%', style: `stop-color:${props.color};stop-opacity:0.15` }),
          h('stop', { offset: '100%', style: `stop-color:${props.color};stop-opacity:0` }),
        ]),
      ]),
      h('path', {
        d: areaD.value,
        fill: `url(#spark-fill-${props.color.replace('#','')})`,
      }),
      h('path', {
        d: pathD.value,
        fill: 'none',
        stroke: props.color,
        'stroke-width': '1',
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        'stroke-opacity': '0.35',
      }),
    ])
  },
})

export default { name: 'Dashboard', components: { Sparkline } }
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
  padding: 16px 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ── Page Header ── */
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

/* ── Admin Banner ── */
.admin-alert-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 12px;
  padding: 12px 18px;
  margin-bottom: 24px;
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.alert-icon { color: #d97706; flex-shrink: 0; }

.banner-text {
  display: flex;
  gap: 6px;
  align-items: center;
}

.banner-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #92400e;
}

.banner-desc {
  font-size: 12.5px;
  color: #b45309;
}

.banner-btn {
  background: #111827;
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 7px 14px;
  cursor: pointer;
  white-space: nowrap;
}

.banner-btn:hover { opacity: 0.88; }

/* ── Stat Cards Row ── */
.stat-cards-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.15s;
}

.stat-card:hover {
  border-color: #e5e7eb;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon-box {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stat-label {
  font-size: 11.5px;
  font-weight: 500;
  color: #9ca3af;
}

.stat-card--spark .stat-card-inner {
  gap: 8px;
}

.stat-spark {
  position: relative;
  margin-left: auto;
  flex-shrink: 0;
}

.spark-hit-area {
  position: absolute;
  top: -8px;
  bottom: -8px;
  left: -4px;
  right: -4px;
  cursor: help;
}

.spark-tooltip-pop {
  padding: 1px 0;
}

.spark-tooltip-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 28px;
  padding: 4px 0;
}

.spark-tooltip-row:not(:last-child) {
  border-bottom: 1px solid #f3f4f6;
}

.spark-tooltip-label {
  color: #9ca3af;
  font-weight: 400;
  font-size: 11px;
}

.spark-tooltip-val {
  color: #111827;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  font-size: 16px;
  line-height: 1;
}

/* ── Content Grid ── */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 24px;
  align-items: stretch;
}

.content-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 22px 0;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title svg {
  color: #6b7280;
}

.card-link {
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
  cursor: pointer;
  transition: color 0.12s;
  user-select: none;
}

.card-link:hover { color: #111827; }

.card-body {
  flex: 1;
  padding: 18px 22px 22px;
  overflow-y: auto;
}

.card-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #9ca3af;
  font-size: 12.5px;
  flex: 1;
  min-height: 200px;
}

.card-empty-compact {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  flex: 1;
  min-height: 200px;
}

.empty-icon { color: #d1d5db; margin-bottom: 6px; }

.empty-title {
  font-size: 13px;
  font-weight: 500;
  color: #4b5563;
  margin: 0 0 4px 0;
}

.empty-desc {
  font-size: 11.5px;
  color: #9ca3af;
  margin: 0;
  max-width: 200px;
  line-height: 1.4;
}

.spin { animation: spin 1s linear infinite; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── Calendar ── */
.cal-layout {
  display: flex;
  gap: 20px;
  align-items: center;
}

.cal-mini {
  flex-shrink: 0;
  width: 178px;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.cal-nav-btn {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}

.cal-nav-btn:hover {
  background: #f3f4f6;
  color: #111827;
}

.cal-month-label {
  font-size: 12px;
  font-weight: 600;
  color: #111827;
}

.cal-dow-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 1px;
}

.cal-dow {
  font-size: 9px;
  font-weight: 600;
  color: #cbd5e1;
  text-align: center;
  padding: 2px 0;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
}

.cal-day {
  position: relative;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
  font-family: inherit;
  padding: 0;
}

.cal-day:hover { background: #f3f4f6; }

.cal-day--other {
  cursor: default;
}

.cal-day--other:hover { background: transparent; }

.cal-day-num {
  font-size: 11px;
  font-weight: 500;
  color: #374151;
  line-height: 1;
}

.cal-day--other .cal-day-num { color: #e5e7eb; }

.cal-day--today {
  background: #f0f9ff;
}

.cal-day--today .cal-day-num {
  color: #0284c7;
  font-weight: 700;
}

.cal-day--today.cal-day--selected {
  background: #111827 !important;
}

.cal-day--today.cal-day--selected .cal-day-num {
  color: #fff;
}

.cal-day--selected {
  background: #111827 !important;
}

.cal-day--selected .cal-day-num {
  color: #fff;
  font-weight: 600;
}

.cal-day-dot {
  position: absolute;
  bottom: 3px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #6366f1;
}

.cal-day--selected .cal-day-dot { background: #a5b4fc; }

/* Daily events panel */
.cal-daily {
  flex: 1;
  min-width: 0;
  border-left: 1px solid #f3f4f6;
  padding-left: 18px;
  align-self: flex-start;
}
.cal-daily--hidden {
  border-left-color: transparent;
}

.cal-events-date {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}

.cal-events-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.cal-events-empty {
  font-size: 12px;
  color: #cbd5e1;
  margin-top: 4px;
}

.cal-event-item {
  display: flex;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  transition: background 0.1s;
}

.cal-event-item:hover {
  background: #f9fafb;
}

.cal-event-time {
  color: #9ca3af;
  font-family: Menlo, Consolas, monospace;
  font-size: 11px;
  white-space: nowrap;
  width: 38px;
  flex-shrink: 0;
  text-align: right;
}

.cal-event-content {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.cal-event-topic {
  font-size: 12px;
  color: #111827;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cal-event-meta {
  font-size: 10.5px;
  color: #9ca3af;
}

/* ── Messages List ── */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
  background: #f9fafb;
  margin-bottom: 6px;
}

.message-item:last-child { margin-bottom: 0; }

.message-item:hover { background: #f3f4f6; }

.message-item.unread {
  background: #f8faff;
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.1);
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: #1e40af;
}

.msg-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.msg-top-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.msg-sender {
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  flex-shrink: 0;
}

.msg-tag {
  font-size: 9.5px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
  letter-spacing: 0.3px;
}

.msg-time {
  font-size: 10.5px;
  color: #cbd5e1;
  flex-shrink: 0;
  margin-left: auto;
}

.msg-title {
  font-size: 12.5px;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-item.unread .msg-title {
  font-weight: 600;
  color: #111827;
}

.msg-body {
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ── Bottom Row (Recent Usage + Recent Docs) ── */
.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.recent-usage-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  padding: 16px 20px;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 14px 0;
}

.recent-usage-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.usage-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: none;
  background: #f9fafb;
  cursor: pointer;
  transition: all 0.12s;
  text-align: left;
  font-family: inherit;
}

.usage-item:hover {
  background: #f3f4f6;
}

.usage-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.usage-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.usage-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #111827;
}

.usage-desc {
  font-size: 10.5px;
  color: #9ca3af;
  line-height: 1.3;
}

/* Recent Documents */
.recent-docs-section {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  padding: 14px 18px;
}

.recent-docs-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-doc-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s;
}

.recent-doc-item:hover {
  background: #f9fafb;
}

.recent-doc-title {
  font-size: 12.5px;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
  margin-right: 12px;
}

.recent-doc-time {
  font-size: 11px;
  color: #cbd5e1;
  flex-shrink: 0;
}

.recent-docs-empty {
  font-size: 12px;
  color: #cbd5e1;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .stat-cards-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .content-grid {
    grid-template-columns: 1fr;
  }
  .bottom-row {
    grid-template-columns: 1fr;
  }
  .recent-usage-grid {
    grid-template-columns: 1fr;
  }
}
</style>
