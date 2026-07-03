<template>
  <div class="my-department">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ isAdmin ? 'Department Management' : 'My Department' }}</h1>
        <p class="page-sub">Manage organization structure, department entities, and employee rosters.</p>
      </div>
      <div class="header-actions" v-if="activeTab === 'roster' && selectedDeptId">
        <el-button class="add-members-btn" @click="openAddDialog">
          <Plus :size="16" />
          Add Employee
        </el-button>
        <el-button class="refresh-btn" @click="fetchMembers" :loading="loading">
          <RefreshCw :size="14" :class="{ 'spin': loading }" />
          Refresh
        </el-button>
      </div>
      <div class="header-actions" v-else-if="activeTab === 'directory' && isAdmin">
        <el-button class="add-members-btn" @click="openDeptDialog(null)">
          <Plus :size="16" />
          Create Department
        </el-button>
        <el-button class="refresh-btn" @click="fetchDepartments" :loading="deptListLoading">
          <RefreshCw :size="14" :class="{ 'spin': deptListLoading }" />
          Refresh
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
              Department Roster
            </span>
          </template>

          <div class="roster-selector-bar" v-if="isAdmin">
            <span class="page-sub-select">
              Select Department:
              <el-select 
                v-model="selectedDeptId" 
                placeholder="Choose Department" 
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
              Department: <strong>{{ userStore.userInfo?.deptName || 'Unassigned Department' }}</strong>
            </span>
          </div>

          <!-- Empty state if no department selected -->
          <div v-if="!selectedDeptId" class="no-dept-card">
            <Briefcase :size="48" class="no-dept-icon" />
            <h3 class="no-dept-title">No Department Selected</h3>
            <p class="no-dept-desc">Please select a department to load and configure its staff roster.</p>
          </div>

          <!-- Roster Table -->
          <div v-else class="table-container">
            <el-table :data="members" v-loading="loading" style="width: 100%" class="custom-table">
              <el-table-column label="Employee" min-width="200">
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

              <el-table-column label="Role" min-width="150">
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

              <el-table-column label="Clearance" min-width="150">
                <template #default="{ row }">
                  <span class="clearance-badge" :class="'level-' + (row.clearanceLevel || 1)">
                    Level-{{ row.clearanceLevel || 1 }} ({{ getClearanceLabel(row.clearanceLevel || 1) }})
                  </span>
                </template>
              </el-table-column>

              <el-table-column label="Actions" min-width="120" align="right">
                <template #default="{ row }">
                  <el-popconfirm
                    title="Are you sure to remove this employee from the department?"
                    confirm-button-text="Yes"
                    cancel-button-text="No"
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
                        Remove
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
              Departments Directory
            </span>
          </template>

          <div class="table-container">
            <el-table :data="departments" v-loading="deptListLoading" style="width: 100%" class="custom-table">
              <el-table-column label="Department ID" prop="id" width="130" />
              <el-table-column label="Department Name" min-width="200">
                <template #default="{ row }">
                  <span class="dept-dir-name">{{ row.deptName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Description" prop="description" min-width="300" />
              <el-table-column label="Actions" width="160" align="right">
                <template #default="{ row }">
                  <div class="action-btn-group">
                    <el-button size="small" @click="openDeptDialog(row)">
                      Edit
                    </el-button>
                    <el-popconfirm
                      title="Deleting this department will unassign all its members. Proceed?"
                      confirm-button-text="Yes"
                      cancel-button-text="No"
                      @confirm="handleDeleteDept(row.id)"
                    >
                      <template #reference>
                        <el-button size="small" type="danger" plain>
                          Delete
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
      title="Add Employee to Department"
      width="500px"
      class="custom-dialog"
    >
      <div class="dialog-body">
        <p class="dialog-desc">
          Select candidates currently unassigned to any department to join <strong>{{ getActiveDeptName() }}</strong>:
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
          <el-table-column label="User">
            <template #default="{ row }">
              <div class="user-info-cell compact">
                <span class="real-name">{{ row.realName || row.username }}</span>
                <span class="username">(@{{ row.username }})</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Role" width="140">
            <template #default="{ row }">
               <span class="role-badge user">{{ translateRole(row.roles?.[0] || 'ROLE_USER') }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="candidates.length === 0 && !candidatesLoading" class="empty-candidates">
          No unassigned candidates found in the system.
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDialogVisible = false" class="dialog-btn-cancel">Cancel</el-button>
          <el-button 
            type="primary" 
            @click="handleAddSubmit" 
            :loading="submitLoading" 
            :disabled="selectedUserIds.length === 0"
            class="dialog-btn-confirm"
          >
            Add Selected ({{ selectedUserIds.length }})
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Create / Edit Department Dialog -->
    <el-dialog
      v-model="deptDialogVisible"
      :title="deptForm.id ? 'Edit Department Details' : 'Create Department'"
      width="460px"
      class="custom-dialog"
    >
      <div class="dialog-body">
        <el-form :model="deptForm" label-position="top">
          <el-form-item label="Department Name" required>
            <el-input 
              v-model="deptForm.deptName" 
              placeholder="E.g. Retail Banking Department" 
              class="custom-input"
            />
          </el-form-item>
          <el-form-item label="Description">
            <el-input 
              v-model="deptForm.description" 
              type="textarea" 
              :rows="3" 
              placeholder="Brief description of the department's core responsibilities."
              class="custom-textarea"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="deptDialogVisible = false" class="dialog-btn-cancel">Cancel</el-button>
          <el-button 
            type="primary" 
            @click="handleDeptSubmit" 
            :loading="deptSubmitLoading" 
            class="dialog-btn-confirm"
            :disabled="!deptForm.deptName"
          >
            Save Details
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@stores/modules/user'
import { ElMessage } from 'element-plus'
import { Plus, RefreshCw, Briefcase, Users } from 'lucide-vue-next'
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
  return dept ? dept.deptName : 'Selected Department'
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
    ElMessage.error(error.message || 'Failed to load department roster')
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
    ElMessage.success('Employees added to department successfully.')
    addDialogVisible.value = false
    await fetchMembers()
  } catch (error: any) {
    console.error('Failed to add members:', error)
    ElMessage.error(error.message || 'Failed to add members')
  } finally {
    submitLoading.value = false
  }
}

const handleRemoveMember = async (row: any) => {
  if (!selectedDeptId.value) return
  try {
    await removeDeptMember(row.id, selectedDeptId.value)
    ElMessage.success(`Removed @${row.username} from department.`)
    await fetchMembers()
  } catch (error: any) {
    console.error('Failed to remove member:', error)
    ElMessage.error(error.message || 'Failed to remove member')
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
      ElMessage.success('Department details updated successfully.')
    } else {
      await createDepartment(deptForm.value as any)
      ElMessage.success('New department created successfully.')
    }
    deptDialogVisible.value = false
    await fetchDepartments()
  } catch (error: any) {
    console.error('Failed to save department:', error)
    ElMessage.error(error.message || 'Failed to save department details')
  } finally {
    deptSubmitLoading.value = false
  }
}

const handleDeleteDept = async (id: number) => {
  try {
    await deleteDepartment(id)
    ElMessage.success('Department deleted successfully.')
    if (selectedDeptId.value === id) {
      selectedDeptId.value = null
      members.value = []
    }
    await fetchDepartments()
  } catch (error: any) {
    console.error('Failed to delete department:', error)
    ElMessage.error(error.message || 'Failed to delete department')
  }
}

const translateRole = (role: string) => {
  if (role === '系统管理员' || role === 'ROLE_ADMIN') return 'System Admin'
  if (role === '部门管理员' || role === 'ROLE_DEPT_ADMIN') return 'Dept Admin'
  if (role === '普通员工' || role === 'ROLE_USER') return 'Employee'
  return role
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
}

.add-members-btn {
  background-color: #111827 !important;
  border: none !important;
  border-radius: 9px !important;
  color: #ffffff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: opacity 0.15s;
}

.add-members-btn:hover {
  opacity: 0.88;
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
