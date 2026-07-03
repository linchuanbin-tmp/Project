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

        <!-- Department -->
        <el-table-column label="Department" min-width="150">
          <template #default="{ row }">
            <span class="dept-text">{{ row.deptName || 'No Department' }}</span>
          </template>
        </el-table-column>

        <!-- Clearance Level -->
        <el-table-column label="Clearance" min-width="130">
          <template #default="{ row }">
            <span class="clearance-badge" :class="'level-' + (row.clearanceLevel || 1)">
              Level-{{ row.clearanceLevel || 1 }} ({{ getClearanceLabel(row.clearanceLevel || 1) }})
            </span>
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
        <el-table-column label="Status" min-width="110">
          <template #default="{ row }">
            <div class="status-cell">
              <el-switch
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                :before-change="() => beforeStatusChange(row)"
                @change="(val) => handleStatusChange(row, val)"
                class="custom-switch"
              />
              <span class="status-text-label" :class="{ 'is-active': row.status === 1 }">
                {{ row.status === 1 ? 'Active' : 'Disabled' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- Actions -->
        <el-table-column label="Actions" min-width="110" align="right">
          <template #default="{ row }">
            <el-button 
              size="small"
              class="action-btn"
              :disabled="row.username === userStore.userInfo?.username"
              @click="openEditDialog(row)"
            >
              Edit User
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Edit User Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="Edit User Access & Department"
      width="460px"
      class="custom-dialog"
      :before-close="closeDialog"
    >
      <div class="dialog-body">
        <p class="dialog-desc">Configure security configurations for <strong>{{ targetUser?.realName || targetUser?.username }}</strong>:</p>
        
        <el-form label-position="top">
          <!-- Role select -->
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

          <!-- Department select -->
          <el-form-item label="Department">
            <el-select v-model="selectedDeptId" placeholder="Select a department" class="custom-select" clearable>
              <el-option
                v-for="item in departments"
                :key="item.id"
                :label="item.deptName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>

          <!-- Clearance level select -->
          <el-form-item label="Clearance Level">
            <el-select v-model="selectedClearanceLevel" placeholder="Select clearance" class="custom-select">
              <el-option :value="1" label="Level-1 (Public)" />
              <el-option :value="2" label="Level-2 (Internal)" />
              <el-option :value="3" label="Level-3 (Confidential)" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeDialog" class="dialog-btn-cancel">Cancel</el-button>
          <el-button type="primary" @click="handleUserUpdate" :loading="submitLoading" class="dialog-btn-confirm">
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
import { getDepartmentsList } from '@/api/department'

const userStore = useUserStore()

// State
const users = ref<any[]>([])
const availableRoles = ref<any[]>([])
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)

const targetUser = ref<any>(null)
const selectedRoleId = ref<number | null>(null)
const selectedDeptId = ref<number | null>(null)
const selectedClearanceLevel = ref<number>(1)

const departments = ref<any[]>([])

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
    availableRoles.value = [
      { id: 1, roleCode: 'ROLE_ADMIN', roleName: 'System Administrator', description: 'Has all system management privileges' },
      { id: 2, roleCode: 'ROLE_USER', roleName: 'Employee', description: 'Has standard service calling privileges' },
      { id: 3, roleCode: 'ROLE_DEPT_ADMIN', roleName: 'Department Administrator', description: 'Manages department members and reviews RAG audits' }
    ]
  }
}

const translateRole = (role: string) => {
  if (role === '系统管理员' || role === 'ROLE_ADMIN') return 'System Administrator'
  if (role === '部门管理员' || role === 'ROLE_DEPT_ADMIN') return 'Department Administrator'
  if (role === '普通员工' || role === 'ROLE_USER') return 'Employee'
  return role
}

const translateDesc = (desc: string) => {
  if (!desc) return ''
  if (desc === '拥有系统所有管理权限' || desc.includes('管理') || desc.includes('ADMIN')) {
    return 'Has all system privileges'
  }
  if (desc.includes('Department') || desc.includes('DEPT')) {
    return 'Manages department members and reviews RAG document access audits'
  }
  return 'Access to knowledge base, code generation, and tools'
}

const getRoleClass = (role: string) => {
  if (role === 'ROLE_ADMIN') return 'admin'
  if (role === 'ROLE_DEPT_ADMIN') return 'dept-admin'
  return 'user'
}

const getClearanceLabel = (level: number) => {
  if (level === 3) return 'Confidential'
  if (level === 2) return 'Internal'
  return 'Public'
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
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
    row.status = val === 1 ? 0 : 1
    console.error('Failed to update status:', error)
  }
}

const openEditDialog = (row: any) => {
  if (row.username === userStore.userInfo?.username) {
    ElMessage.warning('For safety reasons, you cannot modify your own details.')
    return
  }
  targetUser.value = row

  // Find matching role ID
  const userRoleName = row.roles?.[0]
  if (userRoleName) {
    const matched = availableRoles.value.find(r => r.roleName === userRoleName || r.roleCode === userRoleName)
    selectedRoleId.value = matched ? matched.id : null
  } else {
    selectedRoleId.value = null
  }

  selectedDeptId.value = row.deptId || null
  selectedClearanceLevel.value = row.clearanceLevel || 1
  dialogVisible.value = true
}

const closeDialog = () => {
  dialogVisible.value = false
  targetUser.value = null
  selectedRoleId.value = null
  selectedDeptId.value = null
  selectedClearanceLevel.value = 1
}

const handleUserUpdate = async () => {
  if (!targetUser.value) return

  submitLoading.value = true
  try {
    // 1. Submit role change if specified
    if (selectedRoleId.value) {
      await request.post('/admin/user/role', {
        userId: targetUser.value.id,
        roleId: selectedRoleId.value
      })
    }

    // 2. Submit department change
    await request.put('/admin/user/dept', {
      userId: targetUser.value.id,
      deptId: selectedDeptId.value || null
    })

    // 3. Submit clearance change
    await request.put('/admin/user/clearance', {
      userId: targetUser.value.id,
      clearanceLevel: selectedClearanceLevel.value
    })

    ElMessage.success(`User settings updated successfully for @${targetUser.value.username}.`)
    closeDialog()
    await fetchUsers()
  } catch (error: any) {
    console.error('Failed to update user settings:', error)
    ElMessage.error(error.message || 'Failed to save changes')
  } finally {
    submitLoading.value = false
  }
}

const fetchDepartments = async () => {
  try {
    const res: any = await getDepartmentsList()
    departments.value = res || []
  } catch (error) {
    console.error('Failed to load departments list:', error)
  }
}

onMounted(() => {
  fetchUsers()
  fetchRoles()
  fetchDepartments()
})
</script>

<style scoped>
.user-management {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding: 16px 0;
}

/* ── Header ── */
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

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── Table Card ── */
.table-card {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  overflow: hidden;
}

.custom-table {
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #f9fafb;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f3f4f6;
  color: #4b5563;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  border: 1px solid #e5e7eb;
}

.name-details {
  display: flex;
  flex-direction: column;
}

.real-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.username {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 1px;
}

.dept-text {
  font-size: 13.5px;
  color: #374151;
  font-weight: 500;
}

.clearance-badge {
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  white-space: nowrap;
}

.clearance-badge.level-1 {
  background: #f0fdf4;
  color: #16a34a;
}

.clearance-badge.level-2 {
  background: #eff6ff;
  color: #3b82f6;
}

.clearance-badge.level-3 {
  background: #fff1f2;
  color: #f43f5e;
}

.role-badge {
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  margin-right: 4px;
}

.role-badge.admin {
  background: #fff1f2;
  color: #f43f5e;
}

.role-badge.dept-admin {
  background: #fdf4ff;
  color: #a855f7;
}

.role-badge.user {
  background: #f0fdfa;
  color: #0d9488;
}

.role-badge.default {
  background: #f3f4f6;
  color: #6b7280;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-text-label {
  font-size: 13px;
  color: #9ca3af;
}

.status-text-label.is-active {
  color: #374151;
  font-weight: 500;
}

.action-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  color: #374151 !important;
  font-size: 12.5px !important;
  font-weight: 500 !important;
}

.action-btn:hover {
  background: #f9fafb !important;
  border-color: #cbd5e1 !important;
  color: #111827 !important;
}

/* ── Dialog styles ── */
.custom-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
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
  height: auto !important;
  padding: 8px 12px !important;
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
