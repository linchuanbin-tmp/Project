<template>
  <el-row :gutter="20">
    <!-- Add calendar event -->
    <el-col :xs="24" :md="12">
      <div class="schedule-card-panel">
        <div class="panel-header">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" x2="16" y1="2" y2="6"/><line x1="8" x2="8" y1="2" y2="6"/><line x1="3" x2="21" y1="10" y2="10"/><path d="M8 14h.01"/><path d="M12 14h.01"/><path d="M16 14h.01"/><path d="M8 18h.01"/><path d="M12 18h.01"/><path d="M16 18h.01"/></svg>
          <span>Add Calendar Event</span>
        </div>
        <el-form :model="addScheduleForm" label-position="top">
          <el-form-item label="User Name">
            <el-input v-model="addScheduleForm.userId" placeholder="e.g. admin" />
          </el-form-item>
          <el-form-item label="Event ID">
            <el-input v-model="addScheduleForm.eventId" placeholder="e.g. meeting-101" />
          </el-form-item>
          <el-form-item label="Event Name">
            <el-input v-model="addScheduleForm.eventName" placeholder="e.g. Project Review" />
          </el-form-item>
          <el-form-item label="Time Range">
            <el-date-picker
                v-model="addScheduleForm.timeRange"
                type="datetimerange"
                range-separator="to"
                start-placeholder="Start Time"
                end-placeholder="End Time"
                style="width: 100%;"
            />
          </el-form-item>
          <el-form-item style="margin-bottom: 0; margin-top: 10px;">
            <el-button type="primary" @click="createSchedule" :loading="loading" style="width: 100%;">
              Add Event
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-col>

    <!-- Check conflict -->
    <el-col :xs="24" :md="12">
      <div class="schedule-card-panel">
        <div class="panel-header">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="panel-icon"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" x2="12" y1="9" y2="13"/><line x1="12" x2="12.01" y1="17" y2="17"/></svg>
          <span>Check Conflict / Availability</span>
        </div>
        <el-form :model="scheduleForm" label-position="top">
          <el-form-item label="Meeting Period">
            <el-date-picker
                v-model="scheduleForm.timeRange"
                type="datetimerange"
                range-separator="to"
                start-placeholder="Start Time"
                end-placeholder="End Time"
                style="width: 100%;"
            />
          </el-form-item>
          <el-form-item label="Attendees">
            <el-select
                v-model="scheduleForm.attendees"
                placeholder="Select attendees"
                multiple
                clearable
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
          <el-form-item style="margin-bottom: 0; margin-top: 10px;">
            <el-button type="primary" @click="checkConflict" :loading="loading" style="width: 100%;">
              Check Conflict
            </el-button>
          </el-form-item>
        </el-form>
        
        <div v-if="conflictResult" class="conflict-result-alert" style="margin-top: 18px;">
          <el-alert
              :title="conflictResult.message"
              :type="conflictResult.hasConflict ? 'warning' : 'success'"
              :closable="false"
              show-icon
          />
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { checkScheduleConflict } from '@api/tool'

const loading = ref(false)
const conflictResult = ref<any>(null)

// Forms
const scheduleForm = reactive({
  timeRange: [] as Date[],
  attendees: [] as string[]
})

const addScheduleForm = reactive({
  userId: 'admin',
  eventId: '',
  eventName: '',
  timeRange: [] as Date[]
})

const userOptions = [
  { label: 'Administrator (admin)', value: 'admin' },
  { label: 'Test User (user)', value: 'user' },
  { label: 'Zhang San', value: 'zhangsan' },
  { label: 'Li Si', value: 'lisi' }
]

const getToken = () => {
  return localStorage.getItem('token')
      || localStorage.getItem('access_token')
      || sessionStorage.getItem('token')
      || ''
}

const checkConflict = async () => {
  if (scheduleForm.timeRange.length !== 2) {
    ElMessage.warning('Please select a complete time range')
    return
  }

  loading.value = true
  try {
    const res: any = await checkScheduleConflict({
      startTime: scheduleForm.timeRange[0].toISOString(),
      endTime: scheduleForm.timeRange[1].toISOString(),
      attendees: scheduleForm.attendees
    })
    conflictResult.value = res
  } catch (error) {
    console.error(error)
    ElMessage.error('Conflict detection failed')
  } finally {
    loading.value = false
  }
}

const createSchedule = async () => {
  if (addScheduleForm.timeRange.length !== 2) {
    ElMessage.warning('Please select a complete time range')
    return
  }
  if (!addScheduleForm.eventId || !addScheduleForm.eventName) {
    ElMessage.warning('Please fill in both event ID and event name')
    return
  }

  loading.value = true
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
      ElMessage.success('Schedule added successfully')
      addScheduleForm.eventId = ''
      addScheduleForm.eventName = ''
      addScheduleForm.timeRange = []
    } else {
      ElMessage.error(data.message || 'Failed to add schedule')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('Add schedule request failed')
  } finally {
    loading.value = false
  }
}

// Expose interface for parent injection
const setScheduleData = (data: { timeRange?: Date[]; attendees?: string[]; conflictResult?: any }) => {
  if (data.timeRange) scheduleForm.timeRange = data.timeRange
  if (data.attendees) scheduleForm.attendees = data.attendees
  if (data.conflictResult !== undefined) conflictResult.value = data.conflictResult
}

defineExpose({ setScheduleData, checkConflict, conflictResult })
</script>

<style scoped>
.schedule-card-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.01);
  margin-bottom: 20px;
}
.panel-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  border-bottom: 1px solid #f3f4f6;
  padding-bottom: 10px;
}
.panel-icon {
  width: 16px;
  height: 16px;
  margin-right: 8px;
  color: #4f46e5;
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
  height: 42px;
  font-weight: 500;
  transition: all 0.15s;
  padding: 10px 20px;
}
:deep(.el-button--primary:hover) {
  opacity: 0.88;
  transform: translateY(-1px);
}
:deep(.el-button--primary:active) {
  transform: translateY(0);
}
</style>

