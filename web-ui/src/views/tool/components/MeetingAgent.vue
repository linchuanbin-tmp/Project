<template>
  <div class="meeting-agent-container">
    <div class="search-bar-wrap">
      <el-form :model="meetingForm" label-position="top" class="search-inline-form">
        <div class="form-field date-field">
          <el-form-item :label="$t('meeting.date')">
            <el-date-picker
                v-model="meetingForm.date"
                type="date"
                :placeholder="$t('meeting.datePlaceholder')"
                :disabled-date="disabledDate"
                style="width: 100%;"
            />
          </el-form-item>
        </div>
        
        <div class="form-field capacity-field">
          <el-form-item :label="$t('meeting.capacity')">
            <el-input-number v-model="meetingForm.capacity" :min="1" :max="100" style="width: 100%;" />
          </el-form-item>
        </div>

        <div class="form-field button-field">
          <el-button type="primary" class="query-btn" @click="queryMeetingRooms" :loading="loading">
            {{ $t('meeting.searchBtn') }}
          </el-button>
        </div>
      </el-form>
    </div>

    <div v-if="meetingRooms.length > 0" class="room-results-section">
      <h4 class="results-heading">{{ $t('meeting.available') }}</h4>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" v-for="room in meetingRooms" :key="room.id">
          <div class="room-card-new" :class="{ 'is-available': room.available, 'is-occupied': !room.available }">
            <div class="room-card-header">
              <h5 class="room-name">{{ room.name }}</h5>
              <span class="room-id">ID: {{ room.id }}</span>
            </div>
            
            <div class="room-body">
              <div class="room-meta">
                <div class="meta-item">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="meta-icon"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                  <span>{{ $t('meeting.capacity') }}: <strong>{{ room.capacity }}</strong> {{ $t('meeting.capacityUnit') }}</span>
                </div>
                <div class="meta-item">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="meta-icon"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>
                  <span>Location: <strong>{{ room.location }}</strong></span>
                </div>
              </div>
              
              <div class="equip-section" v-if="room.equipment && room.equipment.length > 0">
                <span class="equip-tag" v-for="eq in room.equipment" :key="eq">{{ eq }}</span>
              </div>
            </div>
            
            <div class="room-card-footer">
              <el-tag :type="room.available ? 'success' : 'danger'" effect="light" class="status-tag">
                {{ room.available ? $t('meeting.available') : $t('meeting.occupied') }}
              </el-tag>
              
              <el-button
                  v-if="room.available"
                  type="primary"
                  size="small"
                  class="book-btn"
                  @click="bookRoom(room)"
                  :loading="bookingRoomId === room.id"
              >
                {{ $t('meeting.bookBtn') }}
              </el-button>
              <el-button
                  v-else
                  type="info"
                  size="small"
                  disabled
                  class="book-btn occupied"
              >
                {{ $t('meeting.occupied') }}
              </el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    <div v-else class="empty-state-card">
      <div class="empty-icon-box">
        <Calendar class="empty-icon" :size="20" :stroke-width="1.8" />
      </div>
      <h5 class="empty-title">Find Available Rooms</h5>
      <p class="empty-desc">Select a date and capacity above to query available meeting rooms.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getMeetingRooms } from '@api/tool'
import { Calendar } from 'lucide-vue-next'

const { t } = useI18n()

const loading = ref(false)
const bookingRoomId = ref<string | null>(null)
const meetingRooms = ref<any[]>([])

const disabledDate = (time: Date) => {
  // Prevent choosing any date before today (8.64e7 ms is exactly 1 day)
  return time.getTime() < Date.now() - 8.64e7
}

const meetingForm = reactive({
  date: '' as any,
  capacity: 10
})

const getToken = () => {
  return localStorage.getItem('token')
      || localStorage.getItem('access_token')
      || sessionStorage.getItem('token')
      || ''
}

// Format Date to "yyyy-MM-dd HH:mm:ss"
const formatDateTime = (date: Date): string => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}:${s}`
}

const queryMeetingRooms = async () => {
  if (!meetingForm.date) {
    ElMessage.warning(t('meeting.selectDate'))
    return
  }
  loading.value = true
  try {
    const date = new Date(meetingForm.date)
    const startTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 9, 0, 0)
    const endTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 11, 0, 0)

    const res: any = await getMeetingRooms({
      startTime: formatDateTime(startTime),
      endTime: formatDateTime(endTime),
      capacity: meetingForm.capacity
    })
    meetingRooms.value = res || []
    ElMessage.success(`Found ${meetingRooms.value.length} meeting rooms`)
  } catch (error) {
    console.error(error)
    ElMessage.error(t('request.failed'))
  } finally {
    loading.value = false
  }
}

const bookRoom = async (room: any) => {
  if (!meetingForm.date) {
    ElMessage.warning(t('meeting.selectDate'))
    return
  }

  bookingRoomId.value = room.id
  try {
    const token = getToken()
    const date = new Date(meetingForm.date)
    const startTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 9, 0, 0)
    const endTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 11, 0, 0)

    const res = await fetch('/api/tool/meeting-room/book', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({
        roomId: room.id,
        booker: 'admin',
        startTime: formatDateTime(startTime),
        endTime: formatDateTime(endTime),
        topic: 'Meeting'
      })
    })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success(t('meeting.bookSuccess'))
      // Optimistic UI update
      const idx = meetingRooms.value.findIndex((r: any) => r.id === room.id)
      if (idx !== -1) {
        meetingRooms.value[idx].available = false
        meetingRooms.value[idx].statusText = 'Booked'
      }
      await queryMeetingRooms()
    } else {
      ElMessage.error(data.message || t('meeting.bookFailed'))
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(t('request.failed'))
  } finally {
    bookingRoomId.value = null
  }
}

// Expose interface for parent injection
const setMeetingData = (data: { date?: any; capacity?: number; rooms?: any[] }) => {
  if (data.date) meetingForm.date = data.date
  if (data.capacity) meetingForm.capacity = data.capacity
  if (data.rooms) meetingRooms.value = data.rooms
}

defineExpose({ setMeetingData, queryMeetingRooms, meetingRooms })
</script>

<style scoped>
.room-results-section {
  margin-top: 16px;
}
.results-heading {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 16px 0;
}
.room-card-new {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.01);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}
.room-card-new:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(0,0,0,0.04);
  border-color: #d1d5db;
}
.room-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  border-bottom: 1px solid #f3f4f6;
  padding-bottom: 8px;
}
.room-name {
  font-size: 14.5px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}
.room-id {
  font-size: 11px;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
}
.room-body {
  flex-grow: 1;
}
.room-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.meta-item {
  display: flex;
  align-items: center;
  font-size: 12.5px;
  color: #4b5563;
}
.meta-icon {
  width: 14px;
  height: 14px;
  margin-right: 8px;
  color: #9ca3af;
  flex-shrink: 0;
}
.equip-section {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}
.equip-tag {
  font-size: 10.5px;
  color: #4f46e5;
  background: rgba(79, 70, 229, 0.06);
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 500;
}
.room-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f3f4f6;
  padding-top: 12px;
}
.status-tag {
  border-radius: 6px;
  font-weight: 600;
}
.book-btn {
  border-radius: 8px !important;
  font-weight: 600 !important;
  padding: 6px 14px !important;
  height: 32px !important;
}
.book-btn.occupied {
  background-color: #f3f4f6 !important;
  color: #9ca3af !important;
  border-color: #e5e7eb !important;
}

/* Form controls styling deep overrides */
:deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  padding-bottom: 6px;
}
:deep(.el-input__wrapper), :deep(.el-input-number) {
  background: #f9fafb !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 10px !important;
  box-shadow: none !important;
  transition: all 0.15s;
}
:deep(.el-input__wrapper:hover), :deep(.el-input-number:hover) {
  border-color: #d1d5db !important;
}
:deep(.el-input__wrapper.is-focus), :deep(.el-input-number.is-focus) {
  border-color: #111827 !important;
  background: #fff !important;
  box-shadow: 0 0 0 3px rgba(17,24,39,0.08) !important;
}
:deep(.el-button--primary) {
  background-color: #111827 !important;
  border: none !important;
  border-radius: 10px !important;
  height: 38px !important;
  font-weight: 500;
  transition: all 0.15s;
  padding: 8px 18px !important;
  font-size: 13.5px !important;
}
:deep(.el-button--primary:hover) {
  opacity: 0.88;
  transform: translateY(-1px);
}
:deep(.el-button--primary:active) {
  transform: translateY(0);
}

/* Container wrapper to prevent wide stretching */
.meeting-agent-container {
  width: 100%;
  margin: 0;
}

.search-bar-wrap {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
}

.search-inline-form {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.form-field {
  flex: 1;
  min-width: 140px;
}

.date-field {
  flex: 2; /* Date field gets double width */
  min-width: 180px;
}

.capacity-field {
  flex: 1.2;
  min-width: 120px;
}

.button-field {
  flex: 1;
  min-width: 130px;
  display: flex;
  justify-content: flex-end;
}

:deep(.search-inline-form .el-form-item) {
  margin-bottom: 0 !important;
  width: 100%;
}

.query-btn {
  width: 100%;
}

/* Custom premium empty state card */
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
</style>

