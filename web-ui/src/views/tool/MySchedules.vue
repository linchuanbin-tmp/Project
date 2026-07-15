<template>
  <div class="my-schedules-container">
    <!-- Page header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('mySchedules.title') }}</h1>
        <p class="page-sub">{{ $t('mySchedules.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-select
            v-model="timeFilter"
            class="time-filter-select"
        >
          <el-option :label="$t('mySchedules.filterAll')" value="all" />
          <el-option :label="$t('mySchedules.filterUpcoming')" value="upcoming" />
          <el-option :label="$t('mySchedules.filterPast')" value="past" />
        </el-select>
        <el-button class="refresh-btn" :loading="loading" @click="fetchMySchedules">
          <RefreshCw :size="14" style="margin-right: 6px;" /> {{ $t('common.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- Main content tabs -->
    <el-tabs v-model="activeTab" class="schedules-tabs" type="border-card">

      <!-- Tab 1: Meeting Room Bookings -->
      <el-tab-pane name="bookings">
        <template #label>
          <div class="tab-label">
            <Building :size="15" />
            <span>{{ $t('mySchedules.meetingRoomBookings') }}</span>
          </div>
        </template>

        <div v-loading="loading" class="tab-content-wrapper">
          <el-table
              v-if="bookings.length > 0"
              :data="bookings"
              style="width: 100%"
              class="custom-table"
          >
            <el-table-column :label="$t('mySchedules.room')" min-width="160">
              <template #default="{ row }">
                <div class="room-cell">
                  <span class="room-name">{{ row.roomName || $t('mySchedules.room') }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column :label="$t('mySchedules.location')" min-width="150">
              <template #default="{ row }">
                <div class="location-cell">
                  <MapPin :size="13" class="cell-icon" />
                  <span>{{ row.location || $t('mySchedules.na') }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="topic" :label="$t('mySchedules.topic')" min-width="160" />

            <el-table-column :label="$t('mySchedules.timePeriod')" min-width="260">
              <template #default="{ row }">
                <div class="time-cell">
                  <Clock :size="13" class="cell-icon" />
                  <span>{{ formatTimeSlot(row.startTime, row.endTime) }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column :label="$t('mySchedules.duration')" width="100">
              <template #default="{ row }">
                <span class="duration-badge">{{ calculateDuration(row.startTime, row.endTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column :label="$t('mySchedules.actions')" width="140" fixed="right" align="center">
              <template #default="{ row }">
                <el-tooltip
                    v-if="isPast(row)"
                    :content="$t('mySchedules.cannotCancelPast')"
                    placement="top"
                >
                  <span class="past-badge">{{ $t('mySchedules.pastSchedule') }}</span>
                </el-tooltip>
                <el-button
                    v-else
                    type="danger"
                    link
                    class="cancel-btn"
                    @click="handleCancel(row)"
                >
                  <Trash2 :size="13" style="margin-right: 4px;" /> {{ $t('common.cancel') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- Empty state -->
          <div v-else class="empty-state-card">
            <div class="empty-icon-wrap">
              <Building :size="24" />
            </div>
            <h5 class="empty-title">{{ $t('mySchedules.noBookings') }}</h5>
            <p class="empty-desc">{{ $t('mySchedules.noBookingsDesc') }}</p>
            <el-button type="primary" class="go-to-btn" @click="router.push('/app/tool')">
              {{ $t('mySchedules.bookRoom') }}
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Personal Schedules -->
      <el-tab-pane name="schedules">
        <template #label>
          <div class="tab-label">
            <CalendarDays :size="15" />
            <span>{{ $t('mySchedules.personalSchedules') }}</span>
          </div>
        </template>

        <div v-loading="loading" class="tab-content-wrapper">
          <el-table
              v-if="personalSchedules.length > 0"
              :data="personalSchedules"
              style="width: 100%"
              class="custom-table"
          >
            <el-table-column prop="topic" :label="$t('mySchedules.topic')" min-width="200" />

            <el-table-column :label="$t('mySchedules.timePeriod')" min-width="280">
              <template #default="{ row }">
                <div class="time-cell">
                  <Clock :size="13" class="cell-icon" />
                  <span>{{ formatTimeSlot(row.startTime, row.endTime) }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column :label="$t('mySchedules.duration')" width="110">
              <template #default="{ row }">
                <span class="duration-badge">{{ calculateDuration(row.startTime, row.endTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column :label="$t('mySchedules.actions')" width="140" fixed="right" align="center">
              <template #default="{ row }">
                <el-tooltip
                    v-if="isPast(row)"
                    :content="$t('mySchedules.cannotCancelPast')"
                    placement="top"
                >
                  <span class="past-badge">{{ $t('mySchedules.pastSchedule') }}</span>
                </el-tooltip>
                <el-button
                    v-else
                    type="danger"
                    link
                    class="cancel-btn"
                    @click="handleCancel(row)"
                >
                  <Trash2 :size="13" style="margin-right: 4px;" /> {{ $t('common.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- Empty state -->
          <div v-else class="empty-state-card">
            <div class="empty-icon-wrap">
              <CalendarDays :size="24" />
            </div>
            <h5 class="empty-title">{{ $t('mySchedules.noSchedules') }}</h5>
            <p class="empty-desc">{{ $t('mySchedules.noSchedulesDesc') }}</p>
            <el-button type="primary" class="go-to-btn" @click="router.push('/app/tool')">
              {{ $t('mySchedules.addSchedule') }}
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@utils/request'
import {
  CalendarDays,
  Clock,
  MapPin,
  Trash2,
  Building,
  RefreshCw
} from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const activeTab = ref('bookings')
const timeFilter = ref<'all' | 'upcoming' | 'past'>('all')
const allSchedules = ref<any[]>([])

// Check if a schedule is in the past (endTime is before now)
const isPast = (schedule: any): boolean => {
  if (!schedule.endTime) return false
  return new Date(schedule.endTime).getTime() < Date.now()
}

// Check if a schedule is upcoming (endTime is in the future)
const isUpcoming = (schedule: any): boolean => {
  if (!schedule.endTime) return true
  return new Date(schedule.endTime).getTime() >= Date.now()
}

// Filter schedules by time
const filteredSchedules = computed(() => {
  if (timeFilter.value === 'upcoming') {
    return allSchedules.value.filter(isUpcoming)
  } else if (timeFilter.value === 'past') {
    return allSchedules.value.filter(isPast)
  }
  return allSchedules.value
})

// Filter schedules into bookings (roomId > 0) and personal schedules (roomId === 0)
const bookings = computed(() => {
  return filteredSchedules.value.filter(s => s.roomId !== null && s.roomId > 0)
})

const personalSchedules = computed(() => {
  return filteredSchedules.value.filter(s => s.roomId === null || s.roomId === 0)
})

// Fetch my schedules
const fetchMySchedules = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/tool/my-schedules')
    const payload = res
    allSchedules.value = payload || []
  } catch (error: any) {
    console.error('Failed to fetch schedules:', error)
    ElMessage.error(t('mySchedules.loadError'))
  } finally {
    loading.value = false
  }
}

// Format start & end to standard read format "yyyy-MM-dd HH:mm to HH:mm"
const formatTimeSlot = (startStr: string, endStr: string): string => {
  if (!startStr || !endStr) return ''
  const start = new Date(startStr)
  const end = new Date(endStr)

  const pad = (n: number) => String(n).padStart(2, '0')
  const y = start.getFullYear()
  const m = pad(start.getMonth() + 1)
  const d = pad(start.getDate())
  const sh = pad(start.getHours())
  const smin = pad(start.getMinutes())
  const eh = pad(end.getHours())
  const emin = pad(end.getMinutes())

  // Check if same day
  const isSameDay = start.toDateString() === end.toDateString()
  if (isSameDay) {
    return `${y}-${m}-${d} ${sh}:${smin} to ${eh}:${emin}`
  } else {
    const ey = end.getFullYear()
    const em = pad(end.getMonth() + 1)
    const ed = pad(end.getDate())
    return `${y}-${m}-${d} ${sh}:${smin} to ${ey}-${em}-${ed} ${eh}:${emin}`
  }
}

// Calculate slot duration
const calculateDuration = (startStr: string, endStr: string): string => {
  if (!startStr || !endStr) return ''
  const diffMs = new Date(endStr).getTime() - new Date(startStr).getTime()
  if (diffMs <= 0) return '0m'
  const diffMins = Math.floor(diffMs / 60000)
  const h = Math.floor(diffMins / 60)
  const m = diffMins % 60
  if (h > 0) {
    return m > 0 ? `${h}h ${m}m` : `${h}h`
  }
  return `${m} mins`
}

// Cancel Booking / Delete Personal Schedule
const handleCancel = (row: any) => {
  const isBooking = row.roomId !== null && row.roomId > 0
  const actionText = isBooking ? t('mySchedules.cancelBookingDesc') : t('mySchedules.deleteScheduleDesc')
  const confirmTitle = isBooking ? t('mySchedules.cancelBooking') : t('mySchedules.deleteSchedule')

  ElMessageBox.confirm(
      t('mySchedules.confirmDeleteText', { action: actionText }),
      confirmTitle,
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
        customClass: 'custom-message-box'
      }
  ).then(async () => {
    loading.value = true
    try {
      await request.delete(`/tool/my-schedule/${row.id}`)
      ElMessage.success(isBooking ? t('mySchedules.cancelSuccess') : t('mySchedules.deleteSuccess'))
      fetchMySchedules()
    } catch (error: any) {
      console.error(error)
      ElMessage.error(error.message || t('mySchedules.operationFailed'))
    } finally {
      loading.value = false
    }
  }).catch(() => {
    // cancelled by user
  })
}

onMounted(() => {
  fetchMySchedules()
})
</script>

<script lang="ts">
export default {
  name: 'MySchedules'
}
</script>

<style scoped>
.my-schedules-container {
  padding: 16px 0;
  max-width: 1200px;
  font-family: 'Inter', 'Noto Sans SC', sans-serif;
}

/* Page Header */
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.refresh-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
}

.refresh-btn:hover {
  background: #f9fafb !important;
  border-color: #cbd5e1 !important;
  color: #111827 !important;
}

.time-filter-select {
  width: 140px;
}

:deep(.time-filter-select .el-input__wrapper) {
  height: 38px;
  min-height: 38px;
  border-radius: 9px;
  font-size: 13px;
  border: 1px solid #e5e7eb;
  box-shadow: none;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
}

:deep(.time-filter-select .el-input__wrapper:hover) {
  border-color: #cbd5e1;
}

.past-badge {
  font-size: 11px;
  font-weight: 500;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 3px 10px;
  border-radius: 6px;
  cursor: default;
  white-space: nowrap;
}
:deep(.el-tabs--border-card) {
  background: #ffffff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.015);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background-color: #f9fafb;
  border-bottom: 1px solid #f3f4f6;
  padding: 0 12px;
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) {
  color: #6b7280;
  font-weight: 500;
  font-size: 13.5px;
  height: 48px;
  line-height: 48px;
  transition: all 0.2s;
  border: none !important;
  margin: 0 4px;
  border-bottom: 2px solid transparent !important;
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  color: #111827;
  background-color: transparent !important;
  font-weight: 600;
  border-bottom: 2px solid #111827 !important;
}

:deep(.el-tabs__content) {
  padding: 24px;
  flex-grow: 1;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-content-wrapper {
  min-height: 300px;
}

/* Custom Table Styles */
.custom-table {
  --el-table-border-color: #f3f4f6;
  --el-table-header-bg-color: #f9fafb;
  --el-table-row-hover-bg-color: #f9fafb;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

:deep(.el-table th.el-table__cell) {
  font-weight: 600;
  color: #4b5563;
  font-size: 12.5px;
  height: 44px;
  padding: 0;
}

:deep(.el-table td.el-table__cell) {
  padding: 12px 0;
  font-size: 13.5px;
}

/* Custom cells */
.room-cell {
  font-weight: 600;
  color: #111827;
}

.location-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #4b5563;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #111827;
  font-family: Menlo, Consolas, monospace;
  font-size: 12.5px;
}

.cell-icon {
  color: #9ca3af;
  flex-shrink: 0;
}

.duration-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: #6366f1;
  background: #e0e7ff;
  padding: 3px 8px;
  border-radius: 6px;
}

.cancel-btn {
  color: #dc2626 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
}

.cancel-btn:hover {
  color: #b91c1c !important;
  text-decoration: underline;
}

/* Empty State Card */
.empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 340px;
  background: #fafafa;
  border: 1px dashed #e5e7eb;
  border-radius: 14px;
  text-align: center;
  padding: 40px 20px;
  box-sizing: border-box;
}

.empty-icon-wrap {
  width: 52px;
  height: 52px;
  background: #f3f4f6;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 6px 0;
}

.empty-desc {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 20px 0;
  max-width: 320px;
  line-height: 1.5;
}

.go-to-btn {
  background-color: #111827 !important;
  border: none !important;
  border-radius: 9px !important;
  height: 38px !important;
  font-weight: 500;
  padding: 0 20px !important;
}

.go-to-btn:hover {
  opacity: 0.88;
}
</style>
