<template>
  <div class="user-management">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">User Management</h1>
        <p class="page-sub">Configure staff access levels, assign roles, and enable or disable employee accounts.</p>
      </div>
      <el-button class="refresh-btn" @click="fetchUsers" :loading="loading">
        <RefreshCw :size="14" :class="{ 'spin': loading }" />
        Refresh
      </el-button>
    </div>

    <!-- Main Table Card -->
    <div class="table-card">
      <el-table :data="users" v-loading="loading" style="width: 100%" class="custom-table">
        <!-- Avatar & Username -->
        <el-table-column label="User" min-width="180">
          <template #default="{ row }">
            <div class="user-info-cell">
              <div class="avatar-circle">
                {{ (row.realName === '管理员' ? 'Administrator' : (row.realName || row.username)).charAt(0).toUpperCase() }}
              </div>
              <div class="name-details">
                <span class="real-name">{{ row.realName === '管理员' ? 'Administrator' : (row.realName || '-') }}</span>
                <span class="username">@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- Roles -->
        <el-table-column label="Role" min-width="140">
          <template #default="{ row }">
            <span 
              v-for="role in row.roles" 
              :key="role" 
              class="role-badge"
              :class="getRoleClass(role)"
            >
              {{ translateRole(role) }}
            </span>
            <span v-if="!row.roles || row.roles.length === 0" class="role-badge default">
              No Role
            </span>
          </template>
        </el-table-column>

        <!-- Status -->
        <el-table-column label="Status" min-width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              active-text="Active"
              inactive-text="Disabled"
              inline-prompt
              :before-change="() => beforeStatusChange(row)"
              @change="(val) => handleStatusChange(row, val)"
              class="custom-switch"
            />
          </template>
        </el-table-column>

        <!-- Created Time -->
        <el-table-column label="Created Date" min-width="160">
          <template #default="{ row }">
            <span class="date-text">{{ formatDate(row.createTime) }}</span>
          </template>
        </el-table-column>

        <!-- Actions -->
        <el-table-column label="Actions" min-width="130" align="right">
          <template #default="{ row }">
            <el-button 
              size="small"
              class="action-btn"
              :disabled="row.username === userStore.userInfo?.username"
              @click="openRoleDialog(row)"
            >
              Change Role
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Role Assignment Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="Assign Role"
      width="420px"
      class="custom-dialog"
      :before-close="closeDialog"
    >
      <div class="dialog-body">
        <p class="dialog-desc">Select the authorization role to assign to <strong>{{ targetUser?.realName === '管理员' ? 'Administrator' : (targetUser?.realName || targetUser?.username) }}</strong>:</p>
        
        <el-form label-position="top">
          <el-form-item label="System Role">
            <el-select v-model="selectedRoleId" placeholder="Select a role" class="custom-select">
              <el-option
                v-for="item in availableRoles"
                :key="item.id"
                :label="translateRole(item.roleName)"
                :value="item.id"
              >
                <div class="role-option">
                  <span class="role-opt-name">{{ translateRole(item.roleName) }}</span>
                  <span class="role-opt-desc">{{ translateDesc(item.description) }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeDialog" class="dialog-btn-cancel">Cancel</el-button>
          <el-button type="primary" @click="handleRoleSubmit" :loading="submitLoading" class="dialog-btn-confirm">
            Save Changes
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@stores/modules/user'
import request from '@utils/request'
import { ElMessage } from 'element-plus'
import { RefreshCw } from 'lucide-vue-next'

const userStore = useUserStore()

// State
const users = ref<any[]>([])
const availableRoles = ref<any[]>([])
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)

const targetUser = ref<any>(null)
const selectedRoleId = ref<number | null>(null)

// Actions & Methods
const fetchUsers = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/admin/users')
    users.value = res || []
  } catch (error) {
    console.error('Failed to load users:', error)
  } finally {
    loading.value = false
  }
}

const fetchRoles = async () => {
  try {
    const res: any = await request.get('/admin/roles')
    availableRoles.value = res || []
  } catch (error) {
    console.error('Failed to load roles:', error)
    // Fallback to static definitions if API is unavailable
    availableRoles.value = [
      { id: 1, roleCode: 'ROLE_ADMIN', roleName: 'System Administrator', description: 'Has all system management privileges' },
      { id: 2, roleCode: 'ROLE_USER', roleName: 'Employee', description: 'Has standard service calling privileges' }
    ]
  }
}

const translateRole = (role: string) => {
  if (role === '系统管理员' || role === 'ROLE_ADMIN') return 'System Administrator'
  if (role === '普通员工' || role === 'ROLE_USER') return 'Employee'
  return role
}

const translateDesc = (desc: string) => {
  if (!desc) return ''
  if (desc === '拥有系统所有管理权限' || desc.includes('管理') || desc.includes('ADMIN')) {
    return 'Has all system management privileges'
  }
  if (desc === '拥有微服务基础调用权限' || desc.includes('微服务') || desc.includes('USER')) {
    return 'Has standard service calling privileges'
  }
  return desc
}

// Self-Protection check on status change
const beforeStatusChange = (row: any): boolean => {
  if (row.username === userStore.userInfo?.username) {
    ElMessage.warning('For safety reasons, you cannot disable your own administrator account.')
    return false
  }
  return true
}

const handleStatusChange = async (row: any, val: any) => {
  try {
    await request.put('/admin/user/status', {
      userId: row.id,
      status: val
    })
    ElMessage.success(`Status updated successfully for @${row.username}.`)
  } catch (error) {
    // Revert state on failure
    row.status = val === 1 ? 0 : 1
    console.error('Failed to update status:', error)
  }
}

const openRoleDialog = (row: any) => {
  if (row.username === userStore.userInfo?.username) {
    ElMessage.warning('For safety reasons, you cannot modify your own administrator role.')
    return
  }
  targetUser.value = row
  // Pre-select the user's role if possible
  const userRoleName = row.roles?.[0]
  if (userRoleName) {
    const matched = availableRoles.value.find(r => r.roleName === userRoleName)
    selectedRoleId.value = matched ? matched.id : null
  } else {
    selectedRoleId.value = null
  }
  dialogVisible.value = true
}

const closeDialog = () => {
  dialogVisible.value = false
  targetUser.value = null
  selectedRoleId.value = null
}

const handleRoleSubmit = async () => {
  if (!targetUser.value || !selectedRoleId.value) return

  submitLoading.value = true
  try {
    await request.post('/admin/user/role', {
      userId: targetUser.value.id,
      roleId: selectedRoleId.value
    })
    ElMessage.success(`Role updated successfully for @${targetUser.value.username}.`)
    closeDialog()
    await fetchUsers() // Reload user list
  } catch (error) {
    console.error('Failed to assign role:', error)
  } finally {
    submitLoading.value = false
  }
}

// Styling & Format helpers
const getRoleClass = (roleName: string) => {
  if (roleName.includes('管理员') || roleName.toLowerCase().includes('admin')) {
    return 'admin'
  }
  return 'user'
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  fetchUsers()
  fetchRoles()
})
</script>

<style scoped>
.user-management {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding-top: 16px;
}

/* ── Header ─────────────────────────────────────── */
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

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 8px 14px !important;
  height: 36px;
  transition: all 0.15s;
}

.refresh-btn:hover {
  border-color: #d1d5db !important;
  color: #111827 !important;
  background: #f9fafb !important;
}

.refresh-btn svg.spin {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── Table Card ─────────────────────────────────── */
.table-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  overflow: hidden;
  padding: 8px;
}

/* Custom Table styling */
.custom-table :deep(.el-table__header-wrapper) th {
  background: #fcfcfd;
  color: #4b5563;
  font-weight: 600;
  font-size: 12.5px;
  border-bottom: 1px solid #f3f4f6;
  padding: 12px 16px;
}

.custom-table :deep(.el-table__row) td {
  padding: 14px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.custom-table :deep(.el-table__row:last-child) td {
  border-bottom: none;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-circle {
  width: 32px;
  height: 32px;
  background: #111827;
  border-radius: 9px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.name-details {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.real-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #111827;
}

.username {
  font-size: 11.5px;
  color: #9ca3af;
}

/* Badges */
.role-badge {
  display: inline-block;
  font-size: 11.5px;
  font-weight: 500;
  padding: 2.5px 8px;
  border-radius: 20px;
  margin-right: 4px;
}

.role-badge.admin {
  background: #eef2ff;
  color: #4f46e5;
  border: 1px solid #e0e7ff;
}

.role-badge.user {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #d1fae5;
}

.role-badge.default {
  background: #f3f4f6;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.date-text {
  font-size: 13px;
  color: #6b7280;
}

/* Custom Switch overrides */
.custom-switch :deep(.el-switch__core) {
  border-radius: 20px;
  height: 22px;
}

.custom-switch :deep(.el-switch__label) {
  font-size: 11px;
  font-weight: 500;
  color: #9ca3af;
}

.custom-switch :deep(.el-switch__label.is-active) {
  color: #374151;
}

.action-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  color: #374151 !important;
  font-size: 12.5px !important;
  font-weight: 500 !important;
  transition: all 0.15s;
}

.action-btn:hover:not(:disabled) {
  border-color: #111827 !important;
  color: #111827 !important;
  background: #f9fafb !important;
}

/* ── Dialog ─────────────────────────────────────── */
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

.dialog-desc {
  font-size: 13.5px;
  color: #4b5563;
  line-height: 1.5;
  margin: 0 0 20px 0;
}

.custom-select {
  width: 100%;
}

.custom-select :deep(.el-input__wrapper) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: none !important;
  height: 44px;
}

.custom-select :deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #fff;
}

.role-option {
  display: flex;
  flex-direction: column;
  padding: 4px 0;
}

.role-opt-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #111827;
  line-height: 1.3;
}

.role-opt-desc {
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.3;
  margin-top: 1px;
}

:deep(.el-select-dropdown__item) {
  height: 54px !important;
  padding: 6px 12px !important;
}

.dialog-btn-cancel {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 8px 16px !important;
  height: 38px;
}

.dialog-btn-cancel:hover {
  background: #f9fafb !important;
  border-color: #d1d5db !important;
}

.dialog-btn-confirm {
  background: #111827 !important;
  border: none !important;
  border-radius: 9px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 8px 16px !important;
  height: 38px;
  transition: opacity 0.15s;
}

.dialog-btn-confirm:hover {
  opacity: 0.9;
}
</style>
