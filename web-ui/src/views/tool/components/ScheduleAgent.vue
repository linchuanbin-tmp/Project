<template>
  <div class="schedule-dashboard">
    <!-- Header Section -->
    <div class="scheduler-dashboard-header">
      <div class="header-title-section">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" x2="16" y1="2" y2="6"/><line x1="8" x2="8" y1="2" y2="6"/><line x1="3" x2="21" y1="10" y2="10"/><path d="M8 14h.01"/><path d="M12 14h.01"/><path d="M16 14h.01"/><path d="M8 18h.01"/><path d="M12 18h.01"/><path d="M16 18h.01"/></svg>
        <span class="header-title">{{ $t('schedule.title') }}</span>
      </div>
      <el-button type="primary" class="add-event-btn" @click="openAddDialog">
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="btn-icon"><path d="M5 12h14"/><path d="M12 5v14"/></svg>
        Add Event
      </el-button>
    </div>

    <!-- Top Filter Panel in stable CSS Grid -->
    <div class="scheduler-filter-bar">
      <div class="filter-item date-col">
        <span class="filter-label">Date</span>
        <el-date-picker
            v-model="filterDate"
            type="date"
            placeholder="Pick Date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :clearable="false"
            class="custom-picker"
            @change="onFiltersChange"
        />
      </div>
      <div class="filter-item attendees-col">
        <span class="filter-label">{{ $t('schedule.attendees') }}</span>
        <el-select
            v-model="scheduleForm.attendees"
            :placeholder="$t('schedule.selectAttendees')"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            class="custom-select"
            @change="onFiltersChange"
        >
          <el-option
              v-for="item in userOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
          />
        </el-select>
      </div>
      <div class="filter-item time-range-item">
        <span class="filter-label">{{ $t('schedule.meetingTime') }}</span>
        <el-time-picker
            v-model="scheduleForm.timeRange"
            is-range
            range-separator="to"
            :start-placeholder="$t('schedule.startTime')"
            :end-placeholder="$t('schedule.endTime')"
            format="HH:mm"
            value-format="HH:mm"
            :teleported="false"
            class="custom-time-picker"
            @change="onTimeRangeChange"
        />
      </div>
    </div>

    <!-- Loading Skeleton Overlay (shows during fetch) -->
    <div v-if="loading" class="scheduler-loading-overlay">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- Standalone Premium Empty State (matches MeetingAgent style) -->
    <div v-else-if="scheduleForm.attendees.length === 0" class="empty-state-card">
      <div class="empty-icon-box">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="empty-icon"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
      </div>
      <h5 class="empty-title">Check Schedule & Availability</h5>
      <p class="empty-desc">Select date and attendees above to load their interactive daily timeline track.</p>
    </div>

    <!-- Timeline Grid Container (Only visible when attendees are selected) -->
    <div v-else class="scheduler-grid-card">
      <div class="scheduler-header">
        <div class="attendee-column-header">{{ $t('schedule.attendees') }}</div>
        <div class="timeline-ruler-header">
          <!-- Declutter ruler: Show only even hours, keeping full grid lines -->
          <div v-for="hour in hoursScale" :key="hour" class="ruler-hour-label">
            {{ hour % 2 === 0 ? formatHourLabel(hour) : '' }}
          </div>
        </div>
      </div>

      <!-- Active Timeline Body -->
      <div class="scheduler-body">
        <!-- Render each selected attendee's timeline row -->
        <div v-for="username in scheduleForm.attendees" :key="username" class="scheduler-row">
          <div class="attendee-profile-cell">
            <span class="profile-avatar">{{ getInitials(username) }}</span>
            <el-tooltip :content="getDisplayName(username)" placement="top" effect="dark">
              <span class="profile-name">{{ getDisplayName(username) }}</span>
            </el-tooltip>
          </div>
          <div class="timeline-track-cell">
            <!-- Dotted hour grid lines in background -->
            <div class="grid-lines-layer">
              <div v-for="h in hoursScale" :key="h" class="grid-line-col"></div>
            </div>
            <!-- Absolute busy bars layer -->
            <div class="busy-blocks-layer">
              <el-tooltip
                  v-for="block in getAttendeeBlocks(username)"
                  :key="block.id"
                  effect="dark"
                  placement="top"
              >
                <template #content>
                  <div class="tooltip-schedule-detail">
                    <p class="tooltip-topic"><strong>Topic:</strong> {{ block.topic }}</p>
                    <p class="tooltip-time"><strong>Time:</strong> {{ block.timeStr }}</p>
                  </div>
                </template>
                <div
                    class="busy-bar-block"
                    :style="{ left: block.left + '%', width: block.width + '%' }"
                    @click="openDetailsDialog(block)"
                >
                  <span class="busy-bar-title">{{ block.topic }}</span>
                </div>
              </el-tooltip>
            </div>
          </div>
        </div>

        <!-- Proposed Meeting Target Row -->
        <div class="scheduler-row proposed-row" v-if="proposedBlock">
          <div class="attendee-profile-cell proposed-cell-header">
            <div class="proposed-badge-label" :class="conflictResult?.hasConflict ? 'badge-conflict' : 'badge-available'">
              {{ conflictResult?.hasConflict ? $t('schedule.hasConflict') : $t('schedule.noConflict') }}
            </div>
          </div>
          <div class="timeline-track-cell proposed-track">
            <!-- Grid lines behind -->
            <div class="grid-lines-layer">
              <div v-for="h in hoursScale" :key="h" class="grid-line-col"></div>
            </div>
            <!-- Proposed Time Block -->
            <el-tooltip effect="dark" placement="top">
              <template #content>
                <div>Proposed Meeting: {{ proposedBlock.timeStr }}</div>
              </template>
              <div
                  class="proposed-meeting-bar"
                  :class="conflictResult?.hasConflict ? 'state-conflict' : 'state-available'"
                  :style="{ left: proposedBlock.left + '%', width: proposedBlock.width + '%' }"
              >
                <div class="proposed-bar-content" v-if="proposedBlock.showText">
                  <span class="proposed-topic-text">{{ proposedBlock.timeStr }}</span>
                </div>
              </div>
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>

    <!-- Alert details beneath -->
    <div v-if="conflictResult" class="scheduler-result-banner">
      <el-alert
          :title="conflictResult.message"
          :type="conflictResult.hasConflict ? 'warning' : 'success'"
          :closable="false"
          show-icon
          class="custom-alert-card"
      />
    </div>

    <!-- Dialog for Adding Personal Calendar Event -->
    <el-dialog
        v-model="addDialogVisible"
        title="Add Calendar Event"
        width="460px"
        align-center
        destroy-on-close
        class="custom-schedule-dialog"
    >
      <el-form :model="addScheduleForm" label-position="top" class="dialog-schedule-form">
        <el-form-item label="User Name">
          <el-select
              v-model="addScheduleForm.userId"
              placeholder="Select user"
              filterable
              style="width: 100%;"
          >
            <el-option
                v-for="item in userOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Event ID">
          <el-input v-model="addScheduleForm.eventId" placeholder="e.g. meeting-101" />
        </el-form-item>
        <el-form-item label="Event Name">
          <el-input v-model="addScheduleForm.eventName" placeholder="e.g. Project Review" />
        </el-form-item>
        <el-form-item :label="$t('schedule.meetingTime')">
          <el-date-picker
              v-model="addScheduleForm.timeRange"
              type="datetimerange"
              range-separator="to"
              :start-placeholder="$t('schedule.startTime')"
              :end-placeholder="$t('schedule.endTime')"
              :teleported="false"
              style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-buttons">
          <el-button @click="addDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="createSchedule" :loading="submitLoading">Add Event</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Dialog for Detailed Event Preview (Apple/Outlook Card style) -->
    <el-dialog
        v-model="detailsDialogVisible"
        title="Event Details"
        width="400px"
        align-center
        class="custom-details-dialog"
    >
      <div class="event-details-content" v-if="selectedEvent">
        <div class="detail-row">
          <span class="detail-label">Topic</span>
          <span class="detail-value highlight-topic">{{ selectedEvent.topic }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Organizer</span>
          <span class="detail-value">{{ getDisplayName(selectedEvent.booker) }} ({{ selectedEvent.booker }})</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Time Period</span>
          <span class="detail-value">{{ selectedEvent.timeStr }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Duration</span>
          <span class="detail-value">{{ selectedEvent.durationStr }}</span>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer-buttons">
          <el-button type="primary" @click="detailsDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { checkScheduleConflict } from '@api/tool'
import request from '@utils/request'

const { t } = useI18n()

// Hour scale from 08:00 to 22:00
const hoursScale = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22]
const startHour = 8
const totalHours = 14 // 8:00 to 22:00 is 14 hours scale

const loading = ref(false)
const submitLoading = ref(false)
const addDialogVisible = ref(false)
const detailsDialogVisible = ref(false)
const selectedEvent = ref<any>(null)
const conflictResult = ref<any>(null)
const userOptions = ref<any[]>([])

const filterDate = ref(new Date().toISOString().split('T')[0])
const fetchedSchedules = ref<any[]>([])

// Forms (scheduleForm.timeRange holds string array: ['HH:mm', 'HH:mm'])
const scheduleForm = reactive({
  timeRange: [] as string[],
  attendees: [] as string[]
})

const addScheduleForm = reactive({
  userId: '',
  eventId: '',
  eventName: '',
  timeRange: [] as Date[]
})

const formatHourLabel = (hour: number) => {
  return `${hour.toString().padStart(2, '0')}:00`
}

const getInitials = (username: string) => {
  if (!username) return 'U'
  return username.substring(0, 2).toUpperCase()
}

const getDisplayName = (username: string) => {
  const match = userOptions.value.find(o => o.value === username)
  if (match) {
    return match.label.split(' (')[0]
  }
  return username
}

// Map database schedules into absolute left/width positions for the day
const getAttendeeBlocks = (username: string) => {
  const list = fetchedSchedules.value.filter(s => s.booker === username)
  const blocks: any[] = []

  for (const item of list) {
    const sDate = new Date(item.startTime)
    const eDate = new Date(item.endTime)
    
    const parsed = calculatePercentage(sDate, eDate)
    if (parsed) {
      const sh = sDate.getHours().toString().padStart(2, '0')
      const sm = sDate.getMinutes().toString().padStart(2, '0')
      const eh = eDate.getHours().toString().padStart(2, '0')
      const em = eDate.getMinutes().toString().padStart(2, '0')

      // Calculate meeting duration string
      const diffMins = Math.round((eDate.getTime() - sDate.getTime()) / (60 * 1000))
      let durationStr = `${diffMins} mins`
      if (diffMins >= 60) {
        const h = Math.floor(diffMins / 60)
        const m = diffMins % 60
        durationStr = m > 0 ? `${h}h ${m}m` : `${h}h`
      }

      blocks.push({
        id: item.id,
        topic: item.topic || 'Busy Slot',
        timeStr: `${sh}:${sm} - ${eh}:${em}`,
        durationStr,
        booker: item.booker,
        left: parsed.left,
        width: parsed.width
      })
    }
  }
  return blocks
}

const openDetailsDialog = (block: any) => {
  selectedEvent.value = block
  detailsDialogVisible.value = true
}

// Computes proposed meeting block percentages based on filterDate + timeRange strings
const proposedBlock = computed(() => {
  if (scheduleForm.timeRange && scheduleForm.timeRange.length === 2) {
    const start = new Date(`${filterDate.value}T${scheduleForm.timeRange[0]}:00`)
    const end = new Date(`${filterDate.value}T${scheduleForm.timeRange[1]}:00`)
    const parsed = calculatePercentage(start, end)
    if (parsed) {
      // Calculate duration in minutes to decide whether to render text
      const durationMins = (end.getTime() - start.getTime()) / (60 * 1000)
      return {
        timeStr: `${scheduleForm.timeRange[0]} - ${scheduleForm.timeRange[1]}`,
        left: parsed.left,
        width: parsed.width,
        showText: durationMins >= 90 // Only display text if meeting period is >= 90 minutes (1.5 hours)
      }
    }
  }
  return null
})

// Calculates placement ratio inside the 08:00 - 22:00 timeframe
const calculatePercentage = (start: Date, end: Date) => {
  const baseDateStr = filterDate.value
  const dayStart = new Date(`${baseDateStr}T08:00:00`)
  const dayEnd = new Date(`${baseDateStr}T22:00:00`)

  const startMs = start.getTime()
  const endMs = end.getTime()
  const boundaryStartMs = dayStart.getTime()
  const boundaryEndMs = dayEnd.getTime()

  // Out of bound check
  if (endMs <= boundaryStartMs || startMs >= boundaryEndMs) {
    return null
  }

  const activeStartMs = Math.max(startMs, boundaryStartMs)
  const activeEndMs = Math.min(endMs, boundaryEndMs)

  const leftOffsetMins = (activeStartMs - boundaryStartMs) / (60 * 1000)
  const widthMins = (activeEndMs - activeStartMs) / (60 * 1000)
  const totalMins = totalHours * 60

  return {
    left: (leftOffsetMins / totalMins) * 100,
    width: (widthMins / totalMins) * 100
  }
}

const onFiltersChange = () => {
  fetchSchedules()
  if (scheduleForm.timeRange && scheduleForm.timeRange.length === 2) {
    triggerConflictCheck()
  }
}

const onTimeRangeChange = () => {
  if (scheduleForm.timeRange && scheduleForm.timeRange.length === 2) {
    triggerConflictCheck()
  } else {
    conflictResult.value = null
  }
}

const fetchSchedules = async () => {
  if (scheduleForm.attendees.length === 0) {
    fetchedSchedules.value = []
    return
  }
  loading.value = true
  try {
    const res: any = await request.get('/tool/schedules', {
      params: {
        date: filterDate.value,
        users: scheduleForm.attendees.join(',')
      }
    })
    if (Array.isArray(res)) {
      fetchedSchedules.value = res
    }
  } catch (error) {
    console.error('Failed to load user schedules', error)
  } finally {
    loading.value = false
  }
}

const triggerConflictCheck = async () => {
  if (!scheduleForm.timeRange || scheduleForm.timeRange.length !== 2) return
  if (!scheduleForm.attendees || scheduleForm.attendees.length === 0) return

  try {
    const startStr = `${filterDate.value}T${scheduleForm.timeRange[0]}:00`
    const endStr = `${filterDate.value}T${scheduleForm.timeRange[1]}:00`
    
    const startTime = new Date(startStr)
    const endTime = new Date(endStr)

    const res: any = await checkScheduleConflict({
      startTime: startTime.toISOString(),
      endTime: endTime.toISOString(),
      attendees: scheduleForm.attendees
    })
    conflictResult.value = res
  } catch (error) {
    console.error(error)
  }
}

const fetchUsers = async () => {
  try {
    const res: any = await request.get('/user/list')
    if (Array.isArray(res)) {
      userOptions.value = res.map((user: any) => ({
        label: `${user.realName || user.username} (${user.username})`,
        value: user.username
      }))
      if (userOptions.value.length > 0) {
        addScheduleForm.userId = userOptions.value[0].value
      }
    }
  } catch (error) {
    console.error('Failed to load user list', error)
  }
}

const openAddDialog = () => {
  addScheduleForm.eventId = 'event-' + Math.floor(Math.random() * 1000)
  addScheduleForm.eventName = ''
  
  const dayStart = new Date(`${filterDate.value}T09:00:00`)
  const dayEnd = new Date(`${filterDate.value}T10:00:00`)
  addScheduleForm.timeRange = [dayStart, dayEnd]

  addDialogVisible.value = true
}

const getToken = () => {
  return localStorage.getItem('token')
      || localStorage.getItem('access_token')
      || sessionStorage.getItem('token')
      || ''
}

const createSchedule = async () => {
  if (addScheduleForm.timeRange.length !== 2) {
    ElMessage.warning(t('schedule.selectFullTimeRange'))
    return
  }
  if (!addScheduleForm.eventId || !addScheduleForm.eventName) {
    ElMessage.warning(t('schedule.fillEventInfo'))
    return
  }

  submitLoading.value = true
  try {
    const token = getToken()
    const res = await fetch('/api/tool/schedule/create', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({
        userId: addScheduleForm.userId,
        eventId: addScheduleForm.eventId,
        eventName: addScheduleForm.eventName,
        startTime: addScheduleForm.timeRange[0].toISOString(),
        endTime: addScheduleForm.timeRange[1].toISOString()
      })
    })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success('Schedule event added successfully')
      addDialogVisible.value = false
      fetchSchedules()
      if (scheduleForm.timeRange.length === 2) {
        triggerConflictCheck()
      }
    } else {
      ElMessage.error(data.message || t('request.failed'))
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(t('request.failed'))
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  fetchUsers()
})

// Expose interface for parent injection
const setScheduleData = (data: { timeRange?: Date[]; attendees?: string[]; conflictResult?: any }) => {
  if (data.timeRange) {
    const targetDate = data.timeRange[0].toISOString().split('T')[0]
    if (targetDate !== filterDate.value) {
      filterDate.value = targetDate
    }
    
    // Convert parent Date range to HH:mm string array
    const sh = data.timeRange[0].getHours().toString().padStart(2, '0')
    const sm = data.timeRange[0].getMinutes().toString().padStart(2, '0')
    const eh = data.timeRange[1].getHours().toString().padStart(2, '0')
    const em = data.timeRange[1].getMinutes().toString().padStart(2, '0')
    
    scheduleForm.timeRange = [`${sh}:${sm}`, `${eh}:${em}`]
  }
  if (data.attendees) scheduleForm.attendees = data.attendees
  if (data.conflictResult !== undefined) conflictResult.value = data.conflictResult
  onFiltersChange()
}

defineExpose({ setScheduleData, checkConflict: triggerConflictCheck, conflictResult })
</script>

<style scoped>
.schedule-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Dashboard Header Row */
.scheduler-dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}
.header-title-section {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
.panel-icon {
  width: 18px;
  height: 18px;
  color: #4f46e5;
}

/* Custom Filter Bar in Grid */
.scheduler-filter-bar {
  display: grid;
  grid-template-columns: 1fr 1.5fr 1.5fr;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  gap: 20px;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}
.filter-label {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  letter-spacing: 0.02em;
}

@media (max-width: 768px) {
  .scheduler-filter-bar {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* Standalone Premium Empty State (matches MeetingAgent style exactly) */
.empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  background: #fafafa;
  border: 1px dashed #e5e7eb;
  border-radius: 14px;
  text-align: center;
  margin-top: 10px;
  padding: 30px;
  box-sizing: border-box;
}
.empty-icon-box {
  width: 44px;
  height: 44px;
  background: #f3f4f6;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  margin-bottom: 14px;
}
.empty-icon {
  flex-shrink: 0;
}
.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 6px 0;
  letter-spacing: -0.2px;
}
.empty-desc {
  font-size: 12.5px;
  color: #9ca3af;
  max-width: 320px;
  margin: 0;
  line-height: 1.5;
}

.scheduler-loading-overlay {
  padding: 40px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  min-height: 200px;
}

/* Scheduler Board Layout */
.scheduler-grid-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.01);
}

.scheduler-header {
  display: flex;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  height: 44px;
  align-items: center;
}
.attendee-column-header {
  width: 200px;
  padding-left: 20px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  border-right: 1px solid #e5e7eb;
}
.timeline-ruler-header {
  flex: 1;
  display: flex;
  height: 100%;
}
.ruler-hour-label {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 500;
  color: #6b7280;
  position: relative;
}
.ruler-hour-label:not(:last-child)::after {
  content: "";
  position: absolute;
  right: 0;
  top: 30%;
  bottom: 30%;
  width: 1px;
  background: #e5e7eb;
}

.scheduler-body {
  display: flex;
  flex-direction: column;
}
.scheduler-row {
  display: flex;
  border-bottom: 1px solid #f3f4f6;
  min-height: 58px;
  align-items: stretch;
}
.attendee-profile-cell {
  width: 200px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
}
.profile-avatar {
  width: 28px;
  height: 28px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 11px;
  font-weight: 600;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-name {
  font-size: 13px;
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  max-width: 120px;
  display: inline-block;
}

.timeline-track-cell {
  flex: 1;
  position: relative;
  background: #ffffff;
}
.grid-lines-layer {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  pointer-events: none;
}
.grid-line-col {
  flex: 1;
  height: 100%;
}
.grid-line-col:not(:last-child) {
  border-right: 1px dashed #f3f4f6;
}

/* Busy blocks elements (indigo theme) */
.busy-blocks-layer {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
}
.busy-bar-block {
  position: absolute;
  height: 32px;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  color: #4f46e5;
  border-radius: 6px;
  display: flex;
  align-items: center;
  padding: 0 8px; /* Reduced padding from 10px to 8px */
  box-sizing: border-box;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.15s ease;
}
.busy-bar-block:hover {
  background: #e0e7ff;
  transform: scaleY(1.05);
}
.busy-bar-title {
  font-size: 11px;
  font-weight: 500;
  color: #4f46e5;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

/* Proposed Meeting Row styles */
.proposed-row {
  background: #fafafa;
  min-height: 64px;
  border-bottom: none;
}
.proposed-cell-header {
  background: #f9fafb;
}
.proposed-badge-label {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 12px;
  text-align: center;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  width: 100%;
}
.badge-conflict {
  background: #fef2f2;
  color: #ef4444;
}
.badge-available {
  background: #f0fdf4;
  color: #22c55e;
}

.proposed-track {
  background: #fbfbfb;
  display: flex;
  align-items: center;
}
.proposed-meeting-bar {
  position: absolute;
  height: 38px;
  border-radius: 8px;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center; /* Center the text */
  padding: 0 8px; /* Reduced padding from 14px to 8px */
  animation: pulseProposed 2s infinite ease-in-out;
}
.state-conflict {
  background: #fff5f5;
  border: 1px solid #fecaca;
  color: #e11d48;
}
.state-available {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #059669;
}
.proposed-topic-text {
  font-size: 11px; /* Reduced from 12px */
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  display: block;
}

:deep(.custom-picker.el-date-editor),
:deep(.custom-select),
:deep(.custom-time-picker.el-date-editor) {
  width: 100% !important;
}

@keyframes pulseProposed {
  0% { box-shadow: 0 0 0 0px rgba(239, 68, 68, 0.1); }
  50% { box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.18); }
  100% { box-shadow: 0 0 0 0px rgba(239, 68, 68, 0.1); }
}

/* Dialog Styling */
.custom-schedule-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #f3f4f6;
  margin-right: 0;
  padding: 20px 24px;
}
.custom-schedule-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.custom-schedule-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #f3f4f6;
  padding: 16px 24px;
}
.dialog-footer-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Detailed Event Dialog Styling */
.custom-details-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #f3f4f6;
  margin-right: 0;
  padding: 18px 24px;
}
.custom-details-dialog :deep(.el-dialog__title) {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
.custom-details-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #f3f4f6;
  padding: 14px 24px;
}
.event-details-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 10px 4px;
}
.detail-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.detail-label {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.detail-value {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.highlight-topic {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

/* Button UI overrides */
.add-event-btn {
  background: #111827 !important;
  border: none !important;
  border-radius: 10px !important;
  height: 38px !important;
  padding: 8px 16px !important;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}
.btn-icon {
  width: 14px;
  height: 14px;
}

/* Banner Alert styles */
.scheduler-result-banner {
  margin-top: 4px;
}
.custom-alert-card {
  border-radius: 10px !important;
  border: 1px solid #e5e7eb !important;
  padding: 12px 16px !important;
}

/* Form inputs styling */
:deep(.el-select .el-input__wrapper), 
:deep(.el-date-editor.el-input__wrapper) {
  background: #f9fafb !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 10px !important;
  box-shadow: none !important;
  transition: all 0.15s;
}
:deep(.el-select .el-input__wrapper:hover),
:deep(.el-date-editor.el-input__wrapper:hover) {
  border-color: #d1d5db !important;
}
:deep(.el-select .el-input.is-focus .el-input__wrapper),
:deep(.el-date-editor.el-input__wrapper.is-active) {
  border-color: #111827 !important;
  background: #ffffff !important;
  box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08) !important;
}

.tooltip-schedule-detail {
  padding: 4px;
}
.tooltip-topic {
  margin: 0 0 4px 0;
  font-size: 12px;
}
.tooltip-time {
  margin: 0;
  font-size: 11px;
  color: #d1d5db;
}
</style>
