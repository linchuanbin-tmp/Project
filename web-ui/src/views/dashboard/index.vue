<template>
  <div class="dashboard">

    <!-- Page header -->
    <div class="page-header">
      <h1 class="page-title">Dashboard</h1>
      <p class="page-sub">Welcome back, {{ userStore.userInfo?.username || 'User' }}. Here's what's happening.</p>
    </div>

    <!-- Admin Pending Approvals Alert -->
    <div v-if="isAdminOrDeptAdmin && pendingCount > 0" class="admin-alert-banner">
      <div class="banner-left">
        <AlertTriangle :size="20" class="alert-icon" />
        <div class="banner-text">
          <p class="banner-title">Pending Approvals Action Required</p>
          <p class="banner-desc">You have {{ pendingCount }} pending HITL requests (RAG permission or SQL execution audits) awaiting your review.</p>
        </div>
      </div>
      <button class="banner-btn" @click="router.push('/app/notification')">
        Review Now
      </button>
    </div>

    <!-- Quick access: 3-column grid -->
    <p class="section-label">Quick access</p>
    <div class="action-grid">
      <router-link
          v-for="action in actions"
          :key="action.path"
          :to="action.path"
          class="action-card"
      >
        <div class="action-icon-wrap" :style="{ background: action.bg }">
          <component :is="action.icon" :size="20" :stroke-width="1.5" :color="action.color" />
        </div>
        <p class="action-name">{{ action.name }}</p>
        <p class="action-desc">{{ action.desc }}</p>
        <div class="action-footer">
          <span class="action-link">Open <ArrowRight :size="12" :stroke-width="2" /></span>
        </div>
      </router-link>
    </div>

    <!-- Two-column Bottom Section: Coming Events & Service Status -->
    <div class="bottom-layout-grid">
      <!-- Coming Events -->
      <div class="bottom-grid-col">
        <p class="section-label">Coming Events</p>
        <div class="events-card">
          <div v-if="loadingSchedules" class="events-loading">
            <RefreshCw :size="16" class="spin" />
            <span>Loading schedules...</span>
          </div>
          <div v-else-if="comingEvents.length === 0" class="events-empty">
            <Calendar :size="24" class="empty-icon" />
            <p class="empty-title">No events scheduled today</p>
            <button class="schedule-btn" @click="router.push('/app/tool')">Book a Room</button>
          </div>
          <div v-else class="events-list">
            <div v-for="event in comingEvents" :key="event.id" class="event-item-premium">
              <div class="event-content-premium">
                <div class="event-meta-row-premium">
                  <div class="event-date-pill">
                    <Calendar :size="11" />
                    <span>{{ formatEventDate(event.startTime) }}</span>
                  </div>
                  <div class="event-time-premium">
                    <Clock :size="11" />
                    <span>{{ formatEventTime(event.startTime) }}</span>
                  </div>
                </div>
                <h4 class="event-title-premium">
                  <span class="event-category-dot" :class="getEventCategoryClass(event)"></span>
                  <span>{{ event.topic || 'Meeting Schedule' }}</span>
                </h4>
                <div class="event-details-row-premium">
                  <div class="event-detail-item">
                    <MapPin :size="12" class="detail-icon" />
                    <span>{{ event.roomId && event.roomId !== 0 ? event.roomName || 'Meeting Room' : 'Personal Schedule' }}</span>
                  </div>
                  <div class="event-detail-item" v-if="event.booker">
                    <User :size="12" class="detail-icon" />
                    <span>@{{ event.booker }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Service Status -->
      <div class="bottom-grid-col">
        <p class="section-label">Service status</p>
        <div class="status-bar-card">
          <div class="status-item" v-for="svc in services" :key="svc.name">
            <span class="status-dot" :class="svc.status"></span>
            <span class="status-name">{{ svc.name }}</span>
            <span class="status-badge" :class="svc.status">{{ svc.label }}</span>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import request from '@utils/request'
import { 
  Wrench, FileText, BookOpen, ArrowRight, 
  AlertTriangle, Calendar, RefreshCw, MapPin, User, Clock
} from 'lucide-vue-next'

const userStore = useUserStore()
const router = useRouter()

const pendingCount = ref(0)
const loadingSchedules = ref(false)
const allSchedules = ref<any[]>([])

const isAdminOrDeptAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_DEPT_ADMIN')
})

const getEventCategoryClass = (event: any) => {
  const topic = (event.topic || '').toLowerCase()
  if (topic.includes('credit')) return 'credit'
  if (topic.includes('compliance')) return 'compliance'
  return 'default'
}

const fetchPendingCount = async () => {
  if (isAdminOrDeptAdmin.value) {
    try {
      const res: any = await request.get('/user/notification/list', { params: { status: 2 } })
      const list = res?.data ?? res
      pendingCount.value = Array.isArray(list) ? list.length : 0
    } catch (e) {
      console.error('Failed to fetch pending approvals count', e)
    }
  }
}

const fetchSchedules = async () => {
  loadingSchedules.value = true
  try {
    const res: any = await request.get('/tool/my-schedules')
    const payload = res?.data ?? res
    allSchedules.value = payload || []
  } catch (e) {
    console.error('Failed to fetch schedules', e)
  } finally {
    loadingSchedules.value = false
  }
}

const comingEvents = computed(() => {
  if (allSchedules.value.length > 0) {
    return allSchedules.value.slice(0, 3)
  }
  // Return premium mock schedule if empty
  return [
    {
      id: 'mock-1',
      topic: 'Q3 Credit Audit Review Meeting',
      startTime: new Date(Date.now() + 4 * 3600 * 1000).toISOString(),
      roomName: 'Conference Room 302',
      booker: 'admin',
      roomId: 1
    },
    {
      id: 'mock-2',
      topic: 'Compliance Risk Assessment Panel',
      startTime: new Date(Date.now() + 28 * 3600 * 1000).toISOString(),
      roomName: 'Meeting Room 201',
      booker: 'system',
      roomId: 2
    }
  ]
})

const formatEventTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const formatEventDate = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

const actions = [
  {
    path: '/app/tool',
    name: 'Tool Agent',
    desc: 'Meeting rooms, schedule conflicts, and route planning via natural language.',
    icon: Wrench,
    bg: '#f0f9ff',
    color: '#0ea5e9',
  },
  {
    path: '/app/code',
    name: 'Code Agent',
    desc: 'Describe what data you need and let the agent write the query for you.',
    icon: FileText,
    bg: '#f0fdf4',
    color: '#22c55e',
  },
  {
    path: '/app/rag',
    name: 'RAG Agent',
    desc: 'Upload documents and ask questions. Powered by RAG for grounded answers.',
    icon: BookOpen,
    bg: '#fdf4ff',
    color: '#a855f7',
  },
]

const services = [
  { name: 'Gateway',    status: 'online',  label: 'Online' },
  { name: 'User',       status: 'online',  label: 'Online' },
  { name: 'Tool Agent', status: 'online',  label: 'Online' },
  { name: 'SQL Agent',  status: 'online',  label: 'Online' },
  { name: 'RAG Agent',  status: 'offline', label: 'Coming Soon' },
]

onMounted(() => {
  fetchPendingCount()
  fetchSchedules()
})
</script>

<style scoped>
.dashboard {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding-top: 16px;
}

/* ── Page Header ───────────────────────────────── */
.page-header {
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

/* ── Admin Warning Alert Banner ─────────────────── */
.admin-alert-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 32px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.alert-icon {
  color: #d97706;
  flex-shrink: 0;
}

.banner-title {
  font-size: 14.5px;
  font-weight: 700;
  color: #92400e;
  margin: 0 0 2px 0;
}

.banner-desc {
  font-size: 13px;
  color: #b45309;
  margin: 0;
}

.banner-btn {
  background-color: #111827;
  border: none;
  border-radius: 8px;
  color: #ffffff;
  font-size: 12.5px;
  font-weight: 500;
  padding: 8px 16px;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.banner-btn:hover {
  opacity: 0.88;
}

/* ── Section Label ────────────────────────────── */
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 14px 0;
}

/* ── Action Grid (Quick Access) ───────────────── */
.action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.action-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px 22px 20px;
  border: 1px solid #f0f0f0;
  text-decoration: none;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  transition: background 0.15s, border-color 0.15s;
}

.action-card:hover {
  background: #f9fafb;
  border-color: #e5e7eb;
}

.action-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.action-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 8px 0;
  letter-spacing: -0.2px;
}

.action-desc {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
  line-height: 1.55;
  flex: 1;
}

.action-footer {
  margin-top: 20px;
}

.action-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  transition: color 0.15s;
}

.action-card:hover .action-link {
  color: #111827;
}

/* ── Bottom Columns Layout ──────────────────────── */
.bottom-layout-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 36px;
}

.bottom-grid-col {
  display: flex;
  flex-direction: column;
}

/* ── Events Card ──────────────────────────────── */
.events-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  padding: 16px 20px;
  height: 280px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.events-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #9ca3af;
  flex: 1;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.events-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #9ca3af;
  flex: 1;
}

.empty-icon {
  margin-bottom: 8px;
}

.empty-title {
  font-size: 13.5px;
  font-weight: 500;
  color: #4b5563;
  margin-bottom: 12px;
}

.schedule-btn {
  background-color: #111827;
  border: none;
  border-radius: 8px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 500;
  padding: 8px 16px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.schedule-btn:hover {
  opacity: 0.88;
}

.events-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.event-item-premium {
  display: flex;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.event-item-premium:hover {
  background: #f1f5f9;
  border-color: #e2e8f0;
}

.event-category-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  flex-shrink: 0;
}

.event-category-dot.credit {
  background: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.event-category-dot.compliance {
  background: #a855f7;
  box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.15);
}

.event-category-dot.default {
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.15);
}

.event-content-premium {
  padding: 14px 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.event-meta-row-premium {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
}

.event-date-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  color: #475569;
  font-weight: 600;
}

.event-time-premium {
  display: flex;
  align-items: center;
  gap: 4px;
}

.event-title-premium {
  font-size: 13.5px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
}

.event-details-row-premium {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: #94a3b8;
}

.event-detail-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.event-detail-item span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-icon {
  color: #cbd5e1;
  flex-shrink: 0;
}

/* ── Service Status Card ──────────────────────── */
.status-bar-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  padding: 16px 20px;
  height: 280px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 12px;
  box-sizing: border-box;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #f9fafb;
  background-color: #fcfcfd;
}

.status-item:hover {
  background-color: #f9fafb;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-right: 8px;
}

.status-dot.online  { background: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,0.2); }
.status-dot.offline { background: #d1d5db; }

.status-name {
  font-size: 13.5px;
  color: #374151;
  font-weight: 500;
  flex: 1;
}

.status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
}

.status-badge.online  { background: #f0fdf4; color: #16a34a; }
.status-badge.offline { background: #f3f4f6; color: #9ca3af; }

/* ── Responsive Adaptation ───────────────────── */
@media (max-width: 768px) {
  .page-header {
    margin-bottom: 24px;
    padding-top: 10px;
  }

  .admin-alert-banner {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
  }

  .banner-btn {
    width: 100%;
    text-align: center;
  }

  .action-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .action-card {
    padding: 20px 18px 16px;
  }

  .bottom-layout-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
</style>