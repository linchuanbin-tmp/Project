<template>
  <div class="user-management">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('adminUsers.title') }}</h1>
        <p class="page-sub">{{ $t('adminUsers.subtitle') }}</p>
      </div>
      <el-button class="refresh-btn" @click="fetchUsers" :loading="loading">
        <RefreshCw :size="14" :class="{ 'spin': loading }" />
        {{ $t('common.refresh') }}
      </el-button>
    </div>

    <!-- Filters & Search Bar -->
    <div class="filter-container">
      <div class="filter-left">
        <el-input
          v-model="searchQuery"
          :placeholder="$t('adminUsers.searchPlaceholder')"
          clearable
          class="search-input"
        >
          <template #prefix>
            <Search :size="14" class="search-icon" />
          </template>
        </el-input>
      </div>

      <div class="filter-right">
        <el-select
          v-model="filterDept"
          :placeholder="$t('adminUsers.allDepartments')"
          clearable
          class="filter-select"
        >
          <el-option
            v-for="dept in departments"
            :key="dept.id"
            :label="dept.deptName"
            :value="dept.id"
          />
        </el-select>

        <el-select
          v-model="filterClearance"
          :placeholder="$t('adminUsers.allClearance')"
          clearable
          class="filter-select"
        >
          <el-option :value="1" :label="`Level-1 (${$t('adminUsers.clearancePublic')})`" />
          <el-option :value="2" :label="`Level-2 (${$t('adminUsers.clearanceInternal')})`" />
          <el-option :value="3" :label="`Level-3 (${$t('adminUsers.clearanceConfidential')})`" />
        </el-select>

        <el-select
          v-model="filterRole"
          :placeholder="$t('adminUsers.allRoles')"
          clearable
          class="filter-select"
        >
          <el-option value="ROLE_ADMIN" :label="$t('menu.administrator')" />
          <el-option value="ROLE_DEPT_ADMIN" :label="$t('menu.deptAdmin')" />
          <el-option value="ROLE_USER" :label="$t('menu.employee')" />
        </el-select>
      </div>
    </div>

    <!-- Main Table Card -->
    <div class="table-card">
      <el-table :data="filteredUsers" v-loading="loading" style="width: 100%" class="custom-table">
        <!-- Avatar & Username -->
        <el-table-column :label="$t('adminUsers.user')" min-width="180">
          <template #default="{ row }">
            <div class="user-info-cell">
              <div class="avatar-circle">
                {{ (row.realName === '管理员' ? $t('menu.administrator') : (row.realName || row.username)).charAt(0).toUpperCase() }}
              </div>
              <div class="name-details">
                <span class="real-name">{{ row.realName === '管理员' ? $t('menu.administrator') : (row.realName || '-') }}</span>
                <span class="username">@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- Department -->
        <el-table-column :label="$t('adminUsers.department')" min-width="150">
          <template #default="{ row }">
            <span class="dept-text">{{ row.deptName || $t('adminUsers.noDepartment') }}</span>
          </template>
        </el-table-column>

        <!-- Clearance Level -->
        <el-table-column :label="$t('adminUsers.clearance')" min-width="130">
          <template #default="{ row }">
            <span class="clearance-badge" :class="'level-' + (row.clearanceLevel || 1)">
              Level-{{ row.clearanceLevel || 1 }} ({{ getClearanceLabel(row.clearanceLevel || 1) }})
            </span>
          </template>
        </el-table-column>

        <!-- Roles -->
        <el-table-column :label="$t('adminUsers.roles')" min-width="140">
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
              {{ $t('adminUsers.noRole') }}
            </span>
          </template>
        </el-table-column>

        <!-- Status -->
        <el-table-column :label="$t('adminUsers.status')" min-width="110">
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
                {{ row.status === 1 ? $t('adminUsers.enabled') : $t('adminUsers.disabled') }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- Actions -->
        <el-table-column :label="$t('adminUsers.actions')" min-width="190" align="right">
          <template #default="{ row }">
            <el-button
              size="small"
              class="action-btn reset-btn"
              :disabled="row.username === userStore.userInfo?.username"
              @click="openResetPasswordDialog(row)"
            >
              {{ $t('adminUsers.resetPassword') }}
            </el-button>
            <el-button
              size="small"
              class="action-btn"
              :disabled="row.username === userStore.userInfo?.username"
              @click="openEditDialog(row)"
            >
              {{ $t('adminUsers.edit') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Edit User Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="$t('adminUsers.editUserTitle')"
      width="460px"
      class="custom-dialog"
      :before-close="closeDialog"
    >
      <div class="dialog-body">
        <p class="dialog-desc">{{ $t('adminUsers.editUserDesc', { name: targetUser?.realName || targetUser?.username }) }}</p>

        <el-form label-position="top">
          <!-- Role select -->
          <el-form-item :label="$t('adminUsers.systemRole')">
            <el-select v-model="selectedRoleId" :placeholder="$t('adminUsers.selectRole')" class="custom-select">
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
          <el-form-item :label="$t('adminUsers.department')">
            <el-select v-model="selectedDeptId" :placeholder="$t('adminUsers.selectDepartment')" class="custom-select" clearable>
              <el-option
                v-for="item in departments"
                :key="item.id"
                :label="item.deptName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>

          <!-- Clearance level select -->
          <el-form-item :label="$t('adminUsers.clearanceLevel')">
            <el-select v-model="selectedClearanceLevel" :placeholder="$t('adminUsers.selectClearance')" class="custom-select">
              <el-option :value="1" :label="`Level-1 (${$t('adminUsers.clearancePublic')})`" />
              <el-option :value="2" :label="`Level-2 (${$t('adminUsers.clearanceInternal')})`" />
              <el-option :value="3" :label="`Level-3 (${$t('adminUsers.clearanceConfidential')})`" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeDialog" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="handleUserUpdate" :loading="submitLoading" class="dialog-btn-confirm">
            {{ $t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog
      v-model="resetPasswordDialogVisible"
      :title="$t('adminUsers.resetPasswordTitle')"
      width="460px"
      class="custom-dialog"
      :before-close="closeResetPasswordDialog"
    >
      <div class="dialog-body">
        <p class="dialog-desc">{{ $t('adminUsers.resetPasswordDesc', { name: resetTargetUser?.realName || resetTargetUser?.username }) }}</p>

        <div v-if="tempPassword" class="temp-password-box">
          <p class="temp-password-label">{{ $t('adminUsers.tempPassword') }}</p>
          <div class="temp-password-row">
            <code class="temp-password-value">{{ tempPassword }}</code>
            <el-button size="small" class="copy-btn" @click="copyTempPassword">
              {{ tempPasswordCopied ? $t('adminUsers.copied') : $t('adminUsers.copy') }}
            </el-button>
          </div>
          <p class="temp-password-hint">{{ $t('adminUsers.tempPasswordHint') }}</p>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button v-if="!tempPassword" @click="closeResetPasswordDialog" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button v-if="!tempPassword" type="primary" @click="handleResetPassword" :loading="resetPasswordLoading" class="dialog-btn-confirm">
            {{ $t('adminUsers.confirmReset') }}
          </el-button>
          <el-button v-else type="primary" @click="closeResetPasswordDialog" class="dialog-btn-confirm">
            {{ $t('common.close') }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@stores/modules/user'
import { useI18n } from 'vue-i18n'
import request from '@utils/request'
import { ElMessage } from 'element-plus'
import { RefreshCw, Search } from 'lucide-vue-next'
import { getDepartmentsList } from '@/api/department'

const userStore = useUserStore()
const { t } = useI18n()

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

// Reset password state
const resetPasswordDialogVisible = ref(false)
const resetPasswordLoading = ref(false)
const resetTargetUser = ref<any>(null)
const tempPassword = ref('')
const tempPasswordCopied = ref(false)

const departments = ref<any[]>([])

// Filter State
const searchQuery = ref('')
const filterDept = ref<number | null>(null)
const filterClearance = ref<number | null>(null)
const filterRole = ref<string>('')

// Computed Filtered Users
const filteredUsers = computed(() => {
  return users.value.filter(user => {
    // 1. Search Query filter (matches realName or username case-insensitively)
    if (searchQuery.value) {
      const query = searchQuery.value.toLowerCase().trim()
      const matchRealName = (user.realName || '').toLowerCase().includes(query)
      const matchUsername = (user.username || '').toLowerCase().includes(query)
      if (!matchRealName && !matchUsername) return false
    }

    // 2. Department filter
    if (filterDept.value !== null && filterDept.value !== undefined && filterDept.value !== '') {
      if (user.deptId !== filterDept.value) return false
    }

    // 3. Clearance Level filter
    if (filterClearance.value !== null && filterClearance.value !== undefined && filterClearance.value !== '') {
      const clearance = user.clearanceLevel || 1
      if (clearance !== filterClearance.value) return false
    }

    // 4. Role filter
    if (filterRole.value) {
      const userRoles = user.roles || []
      if (!userRoles.includes(filterRole.value)) return false
    }

    return true
  })
})

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
  if (role === '系统管理员' || role === 'ROLE_ADMIN') return t('menu.administrator')
  if (role === '部门管理员' || role === 'ROLE_DEPT_ADMIN') return t('menu.deptAdmin')
  if (role === '普通员工' || role === 'ROLE_USER') return t('menu.employee')
  return role
}

const translateDesc = (desc: string) => {
  if (!desc) return ''
  if (desc === '拥有系统所有管理权限' || desc.includes('管理') || desc.includes('ADMIN')) {
    return t('adminUsers.roleDesc.admin')
  }
  if (desc.includes('Department') || desc.includes('DEPT')) {
    return t('adminUsers.roleDesc.deptAdmin')
  }
  return t('adminUsers.roleDesc.user')
}

const getRoleClass = (role: string) => {
  if (role === 'ROLE_ADMIN') return 'admin'
  if (role === 'ROLE_DEPT_ADMIN') return 'dept-admin'
  return 'user'
}

const getClearanceLabel = (level: number) => {
  if (level === 3) return t('adminUsers.clearanceConfidential')
  if (level === 2) return t('adminUsers.clearanceInternal')
  return t('adminUsers.clearancePublic')
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
    ElMessage.warning(t('adminUsers.cannotDisableSelf'))
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
    ElMessage.success(t('adminUsers.statusUpdated', { username: row.username }))
  } catch (error) {
    row.status = val === 1 ? 0 : 1
    console.error('Failed to update status:', error)
  }
}

const openEditDialog = (row: any) => {
  if (row.username === userStore.userInfo?.username) {
    ElMessage.warning(t('adminUsers.cannotModifySelf'))
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

    ElMessage.success(t('adminUsers.editSuccess', { username: targetUser.value.username }))
    closeDialog()
    await fetchUsers()
  } catch (error: any) {
    console.error('Failed to update user settings:', error)
    ElMessage.error(error.message || t('request.failed'))
  } finally {
    submitLoading.value = false
  }
}

// Reset password actions
const openResetPasswordDialog = (row: any) => {
  resetTargetUser.value = row
  tempPassword.value = ''
  tempPasswordCopied.value = false
  resetPasswordDialogVisible.value = true
}

const closeResetPasswordDialog = () => {
  resetPasswordDialogVisible.value = false
  resetTargetUser.value = null
  tempPassword.value = ''
  tempPasswordCopied.value = false
}

const handleResetPassword = async () => {
  if (!resetTargetUser.value) return
  resetPasswordLoading.value = true
  try {
    const res: any = await request.put('/admin/user/password/reset', {
      userId: resetTargetUser.value.id
    })
    tempPassword.value = res.tempPassword || res
    ElMessage.success(t('adminUsers.resetPasswordSuccess'))
  } catch (error: any) {
    ElMessage.error(error.message || t('request.failed'))
  } finally {
    resetPasswordLoading.value = false
  }
}

const copyTempPassword = async () => {
  try {
    await navigator.clipboard.writeText(tempPassword.value)
    tempPasswordCopied.value = true
    setTimeout(() => { tempPasswordCopied.value = false }, 2000)
  } catch {
    ElMessage.warning(t('adminUsers.copyFailed'))
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
  font-family: 'Inter', 'Noto Sans SC', sans-serif;
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
  flex-shrink: 0;
}

.name-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.real-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.username {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.reset-btn {
  color: #6366f1 !important;
  border-color: #e0e7ff !important;
}
.reset-btn:hover {
  background: #f5f3ff !important;
  border-color: #c4b5fd !important;
  color: #4f46e5 !important;
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

.filter-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.filter-left {
  flex: 1;
  min-width: 260px;
  max-width: 400px;
}

.filter-right {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input :deep(.el-input__wrapper) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: none !important;
  height: 38px;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #ffffff;
}

.search-icon {
  color: #9ca3af;
}

.filter-select {
  width: 180px;
}

.filter-select :deep(.el-input__wrapper) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: none !important;
  height: 38px;
}

.filter-select :deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #ffffff;
}

.temp-password-box {
  background: #f8faff;
  border: 1px solid #e0e7ff;
  border-radius: 12px;
  padding: 16px;
  margin-top: 8px;
}

.temp-password-label {
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
  margin: 0 0 10px 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.temp-password-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.temp-password-value {
  flex: 1;
  background: #fff;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  letter-spacing: 2px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
}

.copy-btn {
  height: 32px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid #6366f1;
  background: #6366f1 !important;
  color: #fff !important;
  font-size: 12.5px;
  flex-shrink: 0;
}

.copy-btn:hover {
  background: #4f46e5 !important;
  border-color: #4f46e5 !important;
}

.temp-password-hint {
  font-size: 12px;
  color: #9ca3af;
  margin: 10px 0 0 0;
  line-height: 1.5;
}
</style>
