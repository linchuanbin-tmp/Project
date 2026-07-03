<template>
  <div class="my-department">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ isAdmin ? $t('deptAdmin.title') : $t('menu.myDept') }}</h1>
        <p class="page-sub">{{ $t('deptAdmin.subtitle') }}</p>
      </div>
      <div class="header-actions" v-if="activeTab === 'roster' && selectedDeptId" style="display: flex; gap: 10px; align-items: center;">
        <el-button class="add-members-btn" @click="openAddDialog">
          <Plus :size="14" />
          {{ $t('deptAdmin.addEmployee') }}
        </el-button>
        <el-button class="refresh-btn" @click="fetchMembers" :loading="loading">
          <RefreshCw :size="14" :class="{ 'spin': loading }" />
          {{ $t('common.refresh') }}
        </el-button>
      </div>
      <div class="header-actions" v-else-if="activeTab === 'directory' && isAdmin" style="display: flex; gap: 10px; align-items: center;">
        <el-button class="add-members-btn" @click="openDeptDialog(null)">
          <Plus :size="14" />
          {{ $t('deptAdmin.createDept') }}
        </el-button>
        <el-button class="refresh-btn" @click="fetchDepartments" :loading="deptListLoading">
          <RefreshCw :size="14" :class="{ 'spin': deptListLoading }" />
          {{ $t('common.refresh') }}
        </el-button>
      </div>
    </div>

    <!-- Tabs for Administrator -->
    <div class="tabs-card">
      <el-tabs v-model="activeTab" class="custom-tabs" @tab-change="handleTabChange">
        <!-- Roster Management Tab -->
        <el-tab-pane name="roster">
          <template #label>
            <span class="tab-label-custom">
              <Users :size="16" />
              {{ $t('deptAdmin.roster') }}
            </span>
          </template>

          <div class="roster-selector-bar" v-if="isAdmin">
            <span class="page-sub-select">
              {{ $t('deptAdmin.selectDept') }}:
              <el-select
                v-model="selectedDeptId"
                :placeholder="$t('deptAdmin.chooseDept')"
                size="small" 
                style="width: 240px; margin-left: 8px;" 
                @change="handleDeptChange"
                class="header-dept-select"
              >
                <el-option
                  v-for="dept in departments"
                  :key="dept.id"
                  :label="dept.deptName"
                  :value="dept.id"
                />
              </el-select>
            </span>
          </div>

          <div class="roster-selector-bar" v-else>
            <span class="page-sub">
              {{ $t('deptAdmin.deptLabel') }}: <strong>{{ userStore.userInfo?.deptName || $t('deptAdmin.unassigned') }}</strong>
            </span>
          </div>

          <!-- Empty state if no department selected -->
          <div v-if="!selectedDeptId" class="no-dept-card">
            <Briefcase :size="48" class="no-dept-icon" />
            <h3 class="no-dept-title">{{ $t('deptAdmin.noDeptSelected') }}</h3>
            <p class="no-dept-desc">{{ $t('deptAdmin.noDeptDesc') }}</p>
          </div>

          <!-- Roster Table -->
          <div v-else class="table-container">
            <el-table :data="members" v-loading="loading" style="width: 100%" class="custom-table">
              <el-table-column :label="$t('deptAdmin.employee')" min-width="200">
                <template #default="{ row }">
                  <div class="user-info-cell">
                    <div class="avatar-circle">
                      {{ row.realName ? row.realName.charAt(0).toUpperCase() : row.username.charAt(0).toUpperCase() }}
                    </div>
                    <div class="name-details">
                      <span class="real-name">{{ row.realName || '-' }}</span>
                      <span class="username">@{{ row.username }}</span>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <el-table-column :label="$t('deptAdmin.role')" min-width="150">
                <template #default="{ row }">
                  <span 
                    v-for="role in row.roles" 
                    :key="role" 
                    class="role-badge"
                    :class="getRoleClass(role)"
                  >
                    {{ translateRole(role) }}
                  </span>
                </template>
              </el-table-column>

              <el-table-column :label="$t('deptAdmin.clearance')" min-width="150">
                <template #default="{ row }">
                  <span class="clearance-badge" :class="'level-' + (row.clearanceLevel || 1)">
                    Level-{{ row.clearanceLevel || 1 }} ({{ getClearanceLabel(row.clearanceLevel || 1) }})
                  </span>
                </template>
              </el-table-column>

              <el-table-column :label="$t('common.actions')" min-width="120" align="right">
                <template #default="{ row }">
                  <el-popconfirm
                    :title="$t('deptAdmin.confirmRemove')"
                    :confirm-button-text="$t('common.yes')"
                    :cancel-button-text="$t('common.no')"
                    @confirm="handleRemoveMember(row)"
                  >
                    <template #reference>
                      <el-button
                        size="small"
                        type="danger"
                        plain
                        class="remove-btn"
                        :disabled="row.username === userStore.userInfo?.username"
                      >
                        {{ $t('common.remove') }}
                      </el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Department Directory Tab (Admin Only) -->
        <el-tab-pane name="directory" v-if="isAdmin">
          <template #label>
            <span class="tab-label-custom">
              <Briefcase :size="16" />
              {{ $t('deptAdmin.directory') }}
            </span>
          </template>

          <div class="table-container">
            <el-table :data="departments" v-loading="deptListLoading" style="width: 100%" class="custom-table">
              <el-table-column :label="$t('deptAdmin.deptId')" prop="id" width="130" />
              <el-table-column :label="$t('deptAdmin.deptName')" min-width="200">
                <template #default="{ row }">
                  <span class="dept-dir-name">{{ row.deptName }}</span>
                </template>
              </el-table-column>
              <el-table-column :label="$t('deptAdmin.description')" prop="description" min-width="300" />
              <el-table-column :label="$t('common.actions')" width="160" align="right">
                <template #default="{ row }">
                  <div class="action-btn-group">
                    <el-button size="small" @click="openDeptDialog(row)">
                      {{ $t('common.edit') }}
                    </el-button>
                    <el-popconfirm
                      :title="$t('deptAdmin.confirmDeleteDept')"
                      :confirm-button-text="$t('common.yes')"
                      :cancel-button-text="$t('common.no')"
                      @confirm="handleDeleteDept(row.id)"
                    >
                      <template #reference>
                        <el-button size="small" type="danger" plain>
                          {{ $t('common.delete') }}
                        </el-button>
                      </template>
                    </el-popconfirm>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Roster Candidate Selection Dialog -->
    <el-dialog
      v-model="addDialogVisible"
      :title="$t('deptAdmin.addEmployeeTitle')"
      width="500px"
      class="custom-dialog"
    >
      <div class="dialog-body">
        <p class="dialog-desc">
          {{ $t('deptAdmin.selectCandidatesDesc', { dept: getActiveDeptName() }) }}:
        </p>

        <el-table 
          ref="candidateTableRef" 
          :data="candidates" 
          v-loading="candidatesLoading" 
          style="width: 100%" 
          max-height="300"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column :label="$t('deptAdmin.user')">
            <template #default="{ row }">
              <div class="user-info-cell compact">
                <span class="real-name">{{ row.realName || row.username }}</span>
                <span class="username">(@{{ row.username }})</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('deptAdmin.role')" width="140">
            <template #default="{ row }">
               <span class="role-badge user">{{ translateRole(row.roles?.[0] || 'ROLE_USER') }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="candidates.length === 0 && !candidatesLoading" class="empty-candidates">
          {{ $t('deptAdmin.noCandidates') }}
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDialogVisible = false" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            @click="handleAddSubmit"
            :loading="submitLoading"
            :disabled="selectedUserIds.length === 0"
            class="dialog-btn-confirm"
          >
            {{ $t('deptAdmin.addSelected', { count: selectedUserIds.length }) }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Create / Edit Department Dialog -->
    <el-dialog
      v-model="deptDialogVisible"
      :title="deptForm.id ? $t('deptAdmin.editDeptTitle') : $t('deptAdmin.createDeptTitle')"
      width="460px"
      class="custom-dialog"
    >
      <div class="dialog-body">
        <el-form :model="deptForm" label-position="top">
          <el-form-item :label="$t('deptAdmin.deptName')" required>
            <el-input
              v-model="deptForm.deptName"
              :placeholder="$t('deptAdmin.deptNamePlaceholder')"
              class="custom-input"
            />
          </el-form-item>
          <el-form-item :label="$t('deptAdmin.description')">
            <el-input
              v-model="deptForm.description"
              type="textarea"
              :rows="3"
              :placeholder="$t('deptAdmin.descriptionPlaceholder')"
              class="custom-textarea"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="deptDialogVisible = false" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            @click="handleDeptSubmit"
            :loading="deptSubmitLoading"
            class="dialog-btn-confirm"
            :disabled="!deptForm.deptName"
          >
            {{ $t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { ElMessage } from 'element-plus'
import { Plus, RefreshCw, Briefcase, Users } from 'lucide-vue-next'

const { t } = useI18n()
import { 
  getDeptMembers, 
  getDeptCandidates, 
  addDeptMembers, 
  removeDeptMember,
  getDepartmentsList,
  createDepartment,
  updateDepartment,
  deleteDepartment
} from '@/api/department'

const userStore = useUserStore()

const members = ref<any[]>([])
const candidates = ref<any[]>([])
const departments = ref<any[]>([])
const loading = ref(false)
const candidatesLoading = ref(false)
const submitLoading = ref(false)
const addDialogVisible = ref(false)
const selectedUserIds = ref<number[]>([])

const selectedDeptId = ref<number | null>(null)
const activeTab = ref('roster')

// Department CRUD dialog states
const deptDialogVisible = ref(false)
const deptListLoading = ref(false)
const deptSubmitLoading = ref(false)
const deptForm = ref({
  id: null as number | null,
  deptName: '',
  description: ''
})

const isAdmin = computed(() => {
  return userStore.userInfo?.roles?.includes('ROLE_ADMIN')
})

const getActiveDeptName = () => {
  const dept = departments.value.find(d => d.id === selectedDeptId.value)
  return dept ? dept.deptName : t('deptAdmin.selectedDept')
}

const handleDeptChange = async () => {
  await fetchMembers()
}

const handleTabChange = async (tabName: any) => {
  if (tabName === 'directory') {
    await fetchDepartments()
  } else {
    await fetchDepartments()
    if (isAdmin.value && departments.value.length > 0 && !selectedDeptId.value) {
      selectedDeptId.value = departments.value[0].id
    }
    await fetchMembers()
  }
}

const fetchDepartments = async () => {
  deptListLoading.value = true
  try {
    const res: any = await getDepartmentsList()
    departments.value = res || []
  } catch (error) {
    console.error('Failed to load departments list:', error)
  } finally {
    deptListLoading.value = false
  }
}

const fetchMembers = async () => {
  if (!selectedDeptId.value) return
  loading.value = true
  try {
    const res: any = await getDeptMembers(selectedDeptId.value)
    members.value = res || []
  } catch (error: any) {
    console.error('Failed to load department members:', error)
    ElMessage.error(error.message || t('request.failed', { msg: '' }))
  } finally {
    loading.value = false
  }
}

const fetchCandidates = async () => {
  candidatesLoading.value = true
  try {
    const res: any = await getDeptCandidates()
    candidates.value = res || []
  } catch (error: any) {
    console.error('Failed to load candidates:', error)
  } finally {
    candidatesLoading.value = false
  }
}

const openAddDialog = async () => {
  addDialogVisible.value = true
  selectedUserIds.value = []
  await fetchCandidates()
}

const handleSelectionChange = (selection: any[]) => {
  selectedUserIds.value = selection.map(item => item.id)
}

const handleAddSubmit = async () => {
  if (selectedUserIds.value.length === 0 || !selectedDeptId.value) return
  submitLoading.value = true
  try {
    await addDeptMembers(selectedUserIds.value, selectedDeptId.value)
    ElMessage.success(t('deptAdmin.employeeAdded'))
    addDialogVisible.value = false
    await fetchMembers()
  } catch (error: any) {
    console.error('Failed to add members:', error)
    ElMessage.error(error.message || t('request.failed', { msg: '' }))
  } finally {
    submitLoading.value = false
  }
}

const handleRemoveMember = async (row: any) => {
  if (!selectedDeptId.value) return
  try {
    await removeDeptMember(row.id, selectedDeptId.value)
    ElMessage.success(t('deptAdmin.employeeRemoved', { username: row.username }))
    await fetchMembers()
  } catch (error: any) {
    console.error('Failed to remove member:', error)
    ElMessage.error(error.message || t('request.failed', { msg: '' }))
  }
}

// Department Entity CRUD Actions
const openDeptDialog = (row: any | null) => {
  if (row) {
    deptForm.value = {
      id: row.id,
      deptName: row.deptName,
      description: row.description || ''
    }
  } else {
    deptForm.value = {
      id: null,
      deptName: '',
      description: ''
    }
  }
  deptDialogVisible.value = true
}

const handleDeptSubmit = async () => {
  if (!deptForm.value.deptName) return
  deptSubmitLoading.value = true
  try {
    if (deptForm.value.id) {
      await updateDepartment(deptForm.value as any)
      ElMessage.success(t('deptAdmin.deptUpdated'))
    } else {
      await createDepartment(deptForm.value as any)
      ElMessage.success(t('deptAdmin.deptCreated'))
    }
    deptDialogVisible.value = false
    await fetchDepartments()
  } catch (error: any) {
    console.error('Failed to save department:', error)
    ElMessage.error(error.message || t('request.failed', { msg: '' }))
  } finally {
    deptSubmitLoading.value = false
  }
}

const handleDeleteDept = async (id: number) => {
  try {
    await deleteDepartment(id)
    ElMessage.success(t('deptAdmin.deptDeleted'))
    if (selectedDeptId.value === id) {
      selectedDeptId.value = null
      members.value = []
    }
    await fetchDepartments()
  } catch (error: any) {
    console.error('Failed to delete department:', error)
    ElMessage.error(error.message || t('request.failed', { msg: '' }))
  }
}

const translateRole = (role: string) => {
  if (role === '系统管理员' || role === 'ROLE_ADMIN') return t('deptAdmin.roleSystemAdmin')
  if (role === '部门管理员' || role === 'ROLE_DEPT_ADMIN') return t('deptAdmin.roleDeptAdmin')
  if (role === '普通员工' || role === 'ROLE_USER') return t('deptAdmin.roleEmployee')
  return role
}

const getRoleClass = (role: string) => {
  if (role === 'ROLE_ADMIN') return 'admin'
  if (role === 'ROLE_DEPT_ADMIN') return 'dept-admin'
  return 'user'
}

const getClearanceLabel = (level: number) => {
  if (level === 3) return t('deptAdmin.clearanceConfidential')
  if (level === 2) return t('deptAdmin.clearanceInternal')
  return t('deptAdmin.clearancePublic')
}

onMounted(async () => {
  await fetchDepartments()
  if (isAdmin.value) {
    // Admin: select first department by default
    if (departments.value.length > 0) {
      selectedDeptId.value = departments.value[0].id
    }
  } else {
    // Dept Admin: lock to their own department
    selectedDeptId.value = userStore.userInfo?.deptId || null
  }
  await fetchMembers()
})
</script>

<style scoped>
.my-department {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding: 16px 0;
}

/* ── Header ── */
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
  gap: 12px;
  align-items: center;
}

.add-members-btn {
  background-color: #111827 !important;
  border: 1px solid #111827 !important;
  border-radius: 9px !important;
  color: #ffffff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.add-members-btn:hover {
  background-color: #1f2937 !important;
  border-color: #1f2937 !important;
  color: #ffffff !important;
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
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.refresh-btn:hover {
  background: #f9fafb !important;
  border-color: #cbd5e1 !important;
  color: #111827 !important;
}

.add-members-btn :deep(span),
.refresh-btn :deep(span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 100%;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── Tabs & Renders ── */
.tabs-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  padding: 24px;
}

.custom-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #f3f4f6;
}

.custom-tabs :deep(.el-tabs__active-bar) {
  background-color: #111827;
  height: 2px;
}

.custom-tabs :deep(.el-tabs__item) {
  font-size: 14.5px;
  color: #6b7280;
  padding: 0 20px 12px;
  font-weight: 500;
}

.custom-tabs :deep(.el-tabs__item.is-active) {
  color: #111827;
  font-weight: 600;
}

.tab-label-custom {
  display: flex;
  align-items: center;
  gap: 8px;
}

.roster-selector-bar {
  margin-bottom: 24px;
  padding: 4px 0;
}

.page-sub-select {
  font-size: 14px;
  color: #4b5563;
  display: flex;
  align-items: center;
}

.header-dept-select :deep(.el-input__wrapper) {
  border-radius: 6px;
  background-color: #f9fafb;
}

.dept-dir-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.action-btn-group {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* ── No Department Card ── */
.no-dept-card {
  padding: 60px 40px;
  text-align: center;
  max-width: 500px;
  margin: 40px auto 0;
}

.no-dept-icon {
  color: #9ca3af;
  margin: 0 auto 20px;
}

.no-dept-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 10px 0;
}

.no-dept-desc {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.6;
  margin: 0;
}

/* ── Table Container ── */
.table-container {
  border-radius: 12px;
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

.user-info-cell.compact {
  gap: 6px;
}

.user-info-cell.compact .username {
  margin: 0;
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

.clearance-badge {
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  white-space: nowrap;
  display: inline-block;
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
  display: inline-block;
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

.remove-btn {
  border-radius: 8px !important;
}

/* ── Dialog & Input styles ── */
.custom-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.custom-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px;
  border-bottom: 1px solid #f3f4f6;
}

.custom-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.dialog-body {
  padding: 24px 24px 16px;
}

.dialog-desc {
  font-size: 13.5px;
  color: #4b5563;
  line-height: 1.5;
  margin: 0 0 20px 0;
}

.custom-input :deep(.el-input__wrapper) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: none !important;
  height: 44px;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #fff;
}

.custom-textarea :deep(.el-textarea__inner) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: none !important;
  padding: 10px 14px;
}

.custom-textarea :deep(.el-textarea__inner:focus) {
  border-color: #111827;
  background: #fff;
}

.empty-candidates {
  text-align: center;
  padding: 30px;
  color: #9ca3af;
  font-size: 13.5px;
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

.dialog-btn-confirm:hover:not(:disabled) {
  opacity: 0.9;
}
</style>
