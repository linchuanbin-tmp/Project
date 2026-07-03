<template>
  <div class="resource-management">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('adminResources.title') }}</h1>
        <p class="page-sub">{{ $t('adminResources.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button 
          v-if="activeTab === 'rooms'"
          class="add-btn" 
          type="primary" 
          @click="openAddRoomDialog"
        >
          {{ $t('adminResources.addRoom') }}
        </el-button>
        <el-button 
          v-else
          class="add-btn" 
          type="primary" 
          @click="openAddScheduleDialog"
        >
          {{ $t('adminResources.createReservation') }}
        </el-button>
        <el-button class="refresh-btn" @click="handleRefresh" :loading="loading">
          <RefreshCw :size="14" :class="{ 'spin': loading }" />
          {{ $t('common.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- Tabs Container -->
    <div class="tabs-container">
      <el-tabs v-model="activeTab" class="custom-tabs" @tab-change="handleTabChange">
        
        <!-- Tab 1: Meeting Rooms -->
        <el-tab-pane :label="$t('adminResources.tabs.meetingRooms')" name="rooms">
          <div class="table-card">
            <el-table :data="rooms" v-loading="loading" style="width: 100%" class="custom-table">
              <!-- Room Name -->
              <el-table-column :label="$t('adminResources.roomName')" min-width="160">
                <template #default="{ row }">
                  <span class="room-name-text">{{ row.roomName }}</span>
                </template>
              </el-table-column>

              <!-- Location -->
              <el-table-column :label="$t('adminResources.location')" min-width="150">
                <template #default="{ row }">
                  <div class="location-wrap">
                    <span v-if="row.building" class="building-badge">{{ row.building }}</span>
                    <span class="floor-badge">Floor {{ row.floor }}</span>
                  </div>
                </template>
              </el-table-column>

              <!-- Capacity -->
              <el-table-column :label="$t('adminResources.capacity')" min-width="120">
                <template #default="{ row }">
                  <span class="capacity-text">{{ row.capacity }} people</span>
                </template>
              </el-table-column>

              <!-- Facilities -->
              <el-table-column :label="$t('adminResources.facilities')" min-width="220">
                <template #default="{ row }">
                  <div class="facilities-wrap">
                    <span 
                      v-for="facility in parseFacilities(row.facilities)" 
                      :key="facility" 
                      class="facility-badge"
                    >
                      {{ facility }}
                    </span>
                    <span v-if="!row.facilities" class="facility-badge empty">{{ $t('adminResources.none') }}</span>
                  </div>
                </template>
              </el-table-column>

              <!-- Status -->
              <el-table-column :label="$t('adminResources.status')" min-width="140">
                <template #default="{ row }">
                  <div class="status-cell">
                    <el-switch
                      v-model="row.status"
                      :active-value="1"
                      :inactive-value="0"
                      @change="(val) => handleRoomStatusToggle(row, val)"
                      class="custom-switch"
                    />
                    <span class="status-text-label" :class="{ 'is-active': row.status === 1 }">
                      {{ row.status === 1 ? $t('adminResources.active') : $t('adminResources.maintenance') }}
                    </span>
                  </div>
                </template>
              </el-table-column>

              <!-- Actions -->
              <el-table-column :label="$t('adminResources.actions')" min-width="180" align="right">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    class="action-btn edit-btn"
                    @click="openEditRoomDialog(row)"
                  >
                    {{ $t('common.edit') }}
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    class="action-btn delete-btn"
                    @click="confirmDeleteRoom(row)"
                  >
                    {{ $t('common.delete') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Booking Schedules -->
        <el-tab-pane :label="$t('adminResources.tabs.schedules')" name="schedules">
          <div class="table-card">
            <el-table :data="schedules" v-loading="loading" style="width: 100%" class="custom-table">
              <!-- Booker -->
              <el-table-column :label="$t('adminResources.booker')" min-width="150">
                <template #default="{ row }">
                  <span class="booker-name">@{{ row.booker }}</span>
                </template>
              </el-table-column>

              <!-- Room Linkage -->
              <el-table-column :label="$t('adminResources.assignedLocation')" min-width="160">
                <template #default="{ row }">
                  <span v-if="row.roomId === 0" class="room-linkage-text personal">
                    {{ $t('adminResources.personalSchedule') }}
                  </span>
                  <span v-else class="room-linkage-text room">
                    {{ getRoomName(row.roomId) }}
                  </span>
                </template>
              </el-table-column>

              <!-- Topic -->
              <el-table-column :label="$t('adminResources.topic')" min-width="180">
                <template #default="{ row }">
                  <span class="topic-text">{{ row.topic || '-' }}</span>
                </template>
              </el-table-column>

              <!-- Date & Time Duration -->
              <el-table-column :label="$t('adminResources.period')" min-width="300">
                <template #default="{ row }">
                  <div class="time-range-cell">
                    <span class="time-text">{{ formatDate(row.startTime) }}</span>
                    <span class="time-divider">{{ $t('adminResources.to') }}</span>
                    <span class="time-text">{{ formatDate(row.endTime) }}</span>
                  </div>
                </template>
              </el-table-column>

              <!-- Status -->
              <el-table-column :label="$t('adminResources.status')" min-width="120">
                <template #default="{ row }">
                  <span class="status-pill" :class="row.status === 1 ? 'active' : 'cancelled'">
                    {{ row.status === 1 ? $t('adminResources.confirmed') : $t('adminResources.cancelled') }}
                  </span>
                </template>
              </el-table-column>

              <!-- Actions -->
              <el-table-column :label="$t('adminResources.actions')" min-width="180" align="right">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    class="action-btn edit-btn"
                    @click="openEditScheduleDialog(row)"
                  >
                    {{ $t('common.edit') }}
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    class="action-btn delete-btn"
                    @click="confirmDeleteSchedule(row)"
                  >
                    {{ $t('common.cancel') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

      </el-tabs>
    </div>

    <!-- Room Form Dialog (Add / Edit Room) -->
    <el-dialog
      v-model="roomDialogVisible"
      :title="isEdit ? $t('adminResources.editRoom') : $t('adminResources.addRoom')"
      width="440px"
      class="custom-dialog"
      :before-close="closeRoomDialog"
    >
      <div class="dialog-body">
        <el-form :model="roomForm" label-position="top">
          <el-form-item :label="$t('adminResources.roomName')" required>
            <el-input v-model="roomForm.roomName" :placeholder="$t('adminResources.roomNamePlaceholder')" />
          </el-form-item>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('adminResources.building')">
                <el-input v-model="roomForm.building" :placeholder="$t('adminResources.buildingPlaceholder')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('adminResources.floor')" required>
                <el-input v-model="roomForm.floor" :placeholder="$t('adminResources.floorPlaceholder')" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item :label="$t('adminResources.capacity')" required>
            <el-input-number v-model="roomForm.capacity" :min="1" :max="1000" style="width: 100%" />
          </el-form-item>

          <!-- Facilities Selection -->
          <el-form-item :label="$t('adminResources.facilities')">
            <el-select
              v-model="selectedFacilities"
              multiple
              :placeholder="$t('adminResources.facilitiesPlaceholder')"
              style="width: 100%"
            >
              <el-option
                v-for="facility in availableFacilities"
                :key="facility"
                :label="facility"
                :value="facility"
              />
            </el-select>
          </el-form-item>

          <el-form-item :label="$t('adminResources.status')">
            <el-select v-model="roomForm.status" style="width: 100%">
              <el-option :value="1" :label="$t('adminResources.active')" />
              <el-option :value="0" :label="$t('adminResources.maintenance')" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeRoomDialog" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="handleRoomSubmit" :loading="submitLoading" class="dialog-btn-confirm">
            {{ $t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Schedule Form Dialog (Add / Edit Schedule) -->
    <el-dialog
      v-model="scheduleDialogVisible"
      :title="isEdit ? $t('adminResources.editBooking') : $t('adminResources.createReservation')"
      width="460px"
      class="custom-dialog"
      :before-close="closeScheduleDialog"
    >
      <div class="dialog-body">
        <el-form :model="scheduleForm" label-position="top">
          <!-- Booker -->
          <el-form-item :label="$t('adminResources.bookerAccount')" required>
            <el-input v-model="scheduleForm.booker" :placeholder="$t('adminResources.bookerPlaceholder')" />
          </el-form-item>

          <!-- Room Selection -->
          <el-form-item :label="$t('adminResources.assignedLocationRoom')" required>
            <el-select v-model="scheduleForm.roomId" :placeholder="$t('adminResources.selectLocation')" style="width: 100%">
              <el-option :value="0" :label="$t('adminResources.personalScheduleOption')" />
              <el-option 
                v-for="room in rooms" 
                :key="room.id" 
                :value="room.id!" 
                :label="`${room.roomName} (${room.building ? room.building + ', ' : ''}Floor ${room.floor} - Cap: ${room.capacity})`" 
              />
            </el-select>
          </el-form-item>

          <!-- Topic -->
          <el-form-item :label="$t('adminResources.topic')" required>
            <el-input v-model="scheduleForm.topic" :placeholder="$t('adminResources.topicPlaceholder')" />
          </el-form-item>

          <!-- Start & End Date Time Pickers -->
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$t('adminResources.startTime')" required>
                <el-date-picker
                  v-model="scheduleForm.startTime"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  :placeholder="$t('adminResources.startTimePlaceholder')"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('adminResources.endTime')" required>
                <el-date-picker
                  v-model="scheduleForm.endTime"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  :placeholder="$t('adminResources.endTimePlaceholder')"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- Status -->
          <el-form-item :label="$t('adminResources.status')">
            <el-select v-model="scheduleForm.status" style="width: 100%">
              <el-option :value="1" :label="$t('adminResources.confirmed')" />
              <el-option :value="0" :label="$t('adminResources.cancelled')" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeScheduleDialog" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="handleScheduleSubmit" :loading="submitLoading" class="dialog-btn-confirm">
            {{ $t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import request from '@utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshCw } from 'lucide-vue-next'

const { t } = useI18n()

interface MeetingRoom {
  id?: number
  roomName: string
  building?: string
  floor: string
  capacity: number
  facilities: string
  status: number
}

interface MeetingSchedule {
  id?: number
  roomId: number
  booker: string
  startTime: string
  endTime: string
  topic: string
  status: number
}

// State
const activeTab = ref('rooms')
const rooms = ref<MeetingRoom[]>([])
const schedules = ref<MeetingSchedule[]>([])
const loading = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const selectedFacilities = ref<string[]>([])
const availableFacilities = [
  'Projector',
  'Whiteboard',
  'Video Conference',
  'Audio System',
  'Smart Screen',
  'Wi-Fi',
  'Conference Phone'
]

// Dialog visibilities
const roomDialogVisible = ref(false)
const scheduleDialogVisible = ref(false)

// Forms reactive state
const roomForm = reactive<MeetingRoom>({
  id: undefined,
  roomName: '',
  building: '',
  floor: '3',
  capacity: 10,
  facilities: '',
  status: 1
})

const scheduleForm = reactive<MeetingSchedule>({
  id: undefined,
  roomId: 0,
  booker: '',
  startTime: '',
  endTime: '',
  topic: '',
  status: 1
})

// Fetch lists
const fetchRooms = async () => {
  try {
    const res: any = await request.get('/tool/admin/meeting-rooms')
    const payload = res?.data ?? res
    rooms.value = payload || []
  } catch (error: any) {
    console.error('Failed to load meeting rooms:', error)
  }
}

const fetchSchedules = async () => {
  try {
    const res: any = await request.get('/tool/admin/schedules')
    const payload = res?.data ?? res
    schedules.value = payload || []
  } catch (error: any) {
    console.error('Failed to load schedules:', error)
  }
}

const handleRefresh = async () => {
  loading.value = true
  if (activeTab.value === 'rooms') {
    await fetchRooms()
  } else {
    await fetchSchedules()
  }
  loading.value = false
}

const handleTabChange = async (tabName: any) => {
  loading.value = true
  if (tabName === 'rooms') {
    await fetchRooms()
  } else {
    // Need rooms loaded to map roomId → roomName
    await fetchRooms()
    await fetchSchedules()
  }
  loading.value = false
}

const parseFacilities = (facStr: string) => {
  if (!facStr) return []
  return facStr.split(',').map(s => s.trim()).filter(Boolean)
}

const getRoomName = (roomId: number) => {
  const room = rooms.value.find(r => r.id === roomId)
  return room ? room.roomName : `Room ID: ${roomId}`
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  // Format "2026-06-05T15:51:24" to readable format
  const date = new Date(dateStr.replace('T', ' '))
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true
  })
}

// Room Actions
const openAddRoomDialog = () => {
  isEdit.value = false
  roomForm.id = undefined
  roomForm.roomName = ''
  roomForm.building = ''
  roomForm.floor = '3'
  roomForm.capacity = 10
  roomForm.facilities = ''
  selectedFacilities.value = []
  roomForm.status = 1
  roomDialogVisible.value = true
}

const openEditRoomDialog = (row: MeetingRoom) => {
  isEdit.value = true
  roomForm.id = row.id
  roomForm.roomName = row.roomName
  roomForm.building = row.building || ''
  roomForm.floor = row.floor
  roomForm.capacity = row.capacity
  roomForm.facilities = row.facilities || ''
  selectedFacilities.value = row.facilities ? row.facilities.split(',').map(s => s.trim()).filter(Boolean) : []
  roomForm.status = row.status
  roomDialogVisible.value = true
}

const closeRoomDialog = () => {
  roomDialogVisible.value = false
}

const handleRoomSubmit = async () => {
  if (!roomForm.roomName.trim()) {
    ElMessage.warning(t('adminResources.validation.roomNameRequired'))
    return
  }
  if (roomForm.floor === undefined || roomForm.floor === null || (typeof roomForm.floor === 'string' && !roomForm.floor.trim())) {
    ElMessage.warning(t('adminResources.validation.floorRequired'))
    return
  }
  if (!roomForm.capacity || roomForm.capacity <= 0) {
    ElMessage.warning(t('adminResources.validation.capacityRequired'))
    return
  }

  // Convert selected facilities array back to comma-separated string for DB storage
  roomForm.facilities = selectedFacilities.value.join(',')

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await request.put('/tool/admin/meeting-rooms', roomForm)
      ElMessage.success(t('adminResources.roomUpdated'))
    } else {
      await request.post('/tool/admin/meeting-rooms', roomForm)
      ElMessage.success(t('adminResources.roomCreated'))
    }
    closeRoomDialog()
    await fetchRooms()
  } catch (error: any) {
    ElMessage.error(t('request.failed', { msg: error.message || 'Server error' }))
  } finally {
    submitLoading.value = false
  }
}

const handleRoomStatusToggle = async (row: MeetingRoom, val: number) => {
  try {
    await request.put('/tool/admin/meeting-rooms', {
      id: row.id,
      roomName: row.roomName,
      building: row.building,
      floor: row.floor,
      capacity: row.capacity,
      facilities: row.facilities,
      status: val
    })
    ElMessage.success(t('adminResources.roomStatusUpdated', { status: val === 1 ? t('adminResources.active') : t('adminResources.maintenance') }))
  } catch (error: any) {
    row.status = val === 1 ? 0 : 1
    ElMessage.error(t('adminResources.statusToggleFailed', { msg: error.message || 'Server error' }))
  }
}

const confirmDeleteRoom = (row: MeetingRoom) => {
  ElMessageBox.confirm(
    t('adminResources.confirmDeleteRoom', { name: row.roomName }),
    t('common.warning'),
    {
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    try {
      await request.delete(`/tool/admin/meeting-rooms/${row.id}`)
      ElMessage.success(t('adminResources.roomDeleted'))
      await fetchRooms()
    } catch (error: any) {
      ElMessage.error(t('request.failed', { msg: '' }))
    }
  }).catch(() => {})
}

// Schedule Actions
const openAddScheduleDialog = () => {
  isEdit.value = false
  scheduleForm.id = undefined
  scheduleForm.roomId = 0
  scheduleForm.booker = ''
  scheduleForm.startTime = ''
  scheduleForm.endTime = ''
  scheduleForm.topic = ''
  scheduleForm.status = 1
  scheduleDialogVisible.value = true
}

const openEditScheduleDialog = (row: MeetingSchedule) => {
  isEdit.value = true
  scheduleForm.id = row.id
  scheduleForm.roomId = row.roomId
  scheduleForm.booker = row.booker
  scheduleForm.startTime = row.startTime ? row.startTime.replace('T', ' ') : ''
  scheduleForm.endTime = row.endTime ? row.endTime.replace('T', ' ') : ''
  scheduleForm.topic = row.topic || ''
  scheduleForm.status = row.status
  scheduleDialogVisible.value = true
}

const closeScheduleDialog = () => {
  scheduleDialogVisible.value = false
}

const handleScheduleSubmit = async () => {
  if (!scheduleForm.booker.trim()) {
    ElMessage.warning(t('adminResources.validation.bookerRequired'))
    return
  }
  if (!scheduleForm.topic.trim()) {
    ElMessage.warning(t('adminResources.validation.topicRequired'))
    return
  }
  if (!scheduleForm.startTime || !scheduleForm.endTime) {
    ElMessage.warning(t('adminResources.validation.timeRequired'))
    return
  }

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await request.put('/tool/admin/schedules', scheduleForm)
      ElMessage.success(t('adminResources.reservationUpdated'))
    } else {
      await request.post('/tool/admin/schedules', scheduleForm)
      ElMessage.success(t('adminResources.reservationCreated'))
    }
    closeScheduleDialog()
    await fetchSchedules()
  } catch (error: any) {
    ElMessage.error(t('request.failed', { msg: error.message || 'Server error' }))
  } finally {
    submitLoading.value = false
  }
}

const confirmDeleteSchedule = (row: MeetingSchedule) => {
  ElMessageBox.confirm(
    t('adminResources.confirmDeleteReservation', { topic: row.topic }),
    t('common.warning'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    try {
      await request.delete(`/tool/admin/schedules/${row.id}`)
      ElMessage.success(t('adminResources.reservationCancelled'))
      await fetchSchedules()
    } catch (error: any) {
      ElMessage.error(t('request.failed', { msg: '' }))
    }
  }).catch(() => {})
}

onMounted(async () => {
  loading.value = true
  await fetchRooms()
  await fetchSchedules()
  loading.value = false
})
</script>

<style scoped>
.resource-management {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding: 16px 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
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
  gap: 12px;
}

.add-btn {
  background: #111827 !important;
  border: none !important;
  border-radius: 8px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px;
  padding: 0 16px !important;
}

.add-btn:hover {
  background: #1f2937 !important;
}

.refresh-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px;
  padding: 0 16px !important;
  display: flex;
  align-items: center;
  gap: 6px;
}

.refresh-btn:hover {
  background: #f9fafb !important;
  border-color: #d1d5db !important;
}

.spin {
  animation: loading-spin 1s infinite linear;
}

@keyframes loading-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Tabs */
.tabs-container {
  margin-top: 10px;
}

:deep(.custom-tabs .el-tabs__header) {
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 20px;
}

:deep(.custom-tabs .el-tabs__item) {
  font-size: 14px;
  font-weight: 500;
  color: #6b7280;
  height: 40px;
  line-height: 40px;
}

:deep(.custom-tabs .el-tabs__item.is-active) {
  color: #111827;
  font-weight: 600;
}

:deep(.custom-tabs .el-tabs__active-bar) {
  background-color: #111827;
  height: 2px;
}

.table-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.012);
  overflow: hidden;
  padding: 8px;
}

:deep(.custom-table) {
  --el-table-border-color: #f3f4f6;
  --el-table-header-bg-color: #f9fafb;
}

:deep(.el-table__header-wrapper th) {
  color: #374151;
  font-weight: 600;
  font-size: 13px;
  height: 48px;
}

:deep(.el-table__row td) {
  height: 60px;
}

.room-name-text {
  font-weight: 600;
  color: #111827;
  font-size: 14px;
}

.location-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.building-badge {
  background: #e0e7ff;
  color: #4f46e5;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.floor-badge {
  background: #f3f4f6;
  color: #4b5563;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.capacity-text {
  font-size: 13px;
  color: #4b5563;
}

.facilities-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.facility-badge {
  background: #f0f9ff;
  color: #0369a1;
  border: 1px solid #e0f2fe;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 500;
}

.facility-badge.empty {
  background: #f3f4f6;
  color: #9ca3af;
  border: 1px solid #e5e7eb;
}

/* Schedule Table styles */
.booker-name {
  font-weight: 600;
  color: #3b82f6;
  font-size: 13.5px;
}

.room-linkage-text {
  font-size: 13.5px;
  font-weight: 500;
}

.room-linkage-text.room {
  color: #111827;
}

.room-linkage-text.personal {
  color: #9ca3af;
  font-style: italic;
}

.topic-text {
  font-size: 13.5px;
  color: #374151;
}

.time-range-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.time-text {
  font-size: 13px;
  color: #4b5563;
  font-family: SFMono-Regular, Consolas, monospace;
}

.time-divider {
  font-size: 11.5px;
  color: #9ca3af;
  font-weight: 500;
}

.status-pill {
  font-size: 11.5px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 20px;
  display: inline-block;
}

.status-pill.active {
  background: #ecfdf5;
  color: #047857;
}

.status-pill.cancelled {
  background: #fef2f2;
  color: #b91c1c;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-text-label {
  font-size: 13px;
  font-weight: 500;
  color: #9ca3af;
  transition: color 0.15s;
}

.status-text-label.is-active {
  color: #10b981;
}

.custom-switch :deep(.el-switch__core) {
  border-radius: 20px;
  height: 20px;
}

.action-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  color: #374151 !important;
  font-size: 12.5px !important;
  font-weight: 500 !important;
  height: 32px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
}

.action-btn:hover:not(:disabled) {
  border-color: #111827 !important;
  color: #111827 !important;
  background: #f9fafb !important;
}

.delete-btn {
  border-color: #fecaca !important;
  color: #dc2626 !important;
}

.delete-btn:hover:not(:disabled) {
  background: #fef2f2 !important;
  border-color: #f87171 !important;
  color: #b91c1c !important;
}

/* Dialog */
.custom-dialog :deep(.el-dialog) {
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0,0,0,0.08);
}

.custom-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px 24px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.custom-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.dialog-body {
  padding: 24px 24px 0;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 13px;
  color: #374151;
  margin-bottom: 6px;
}

:deep(.el-input__wrapper) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: none !important;
  height: 40px;
}

:deep(.el-input-number .el-input__wrapper) {
  padding-left: 40px;
  padding-right: 40px;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #fff;
}

.dialog-btn-cancel {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
}

.dialog-btn-cancel:hover {
  background: #f9fafb !important;
}

.dialog-btn-confirm {
  background: #111827 !important;
  border: none !important;
  border-radius: 9px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
}

.dialog-btn-confirm:hover {
  background: #1f2937 !important;
}
</style>
