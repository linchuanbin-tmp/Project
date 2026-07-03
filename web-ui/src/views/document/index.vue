<template>
  <div class="documents-page">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">Documents Library</h1>
        <p class="page-sub">Browse, request, and retrieve system manuals and department knowledge assets.</p>
      </div>
      <el-button class="refresh-btn" @click="fetchDocuments" :loading="loading">
        <RefreshCw :size="14" :class="{ 'spin': loading }" />
        Refresh
      </el-button>
    </div>

    <!-- Tabs Container -->
    <div class="tabs-card">
      <el-tabs v-model="activeTab" class="custom-tabs">
        <!-- Tab 1: System Documentation -->
        <el-tab-pane name="system">
          <template #label>
            <span class="tab-label-custom">
              <BookOpen :size="16" />
              System Documentation
            </span>
          </template>

          <div v-if="systemDocs.length === 0 && !loading" class="empty-tab-card">
            <FolderOpen :size="40" class="empty-icon" />
            <h4 class="empty-title">No System Documentation</h4>
            <p class="empty-desc">There are no global system guides available at this time.</p>
          </div>

          <div v-else class="documents-grid">
            <div 
              v-for="doc in systemDocs" 
              :key="doc.id" 
              class="doc-card"
            >
              <div class="doc-card-header">
                <div class="doc-icon-wrap system">
                  <BookOpen :size="18" />
                </div>
                <span class="security-badge level-1">Global</span>
              </div>
              <div class="doc-card-body">
                <h3 class="doc-title">{{ doc.title }}</h3>
                <p class="doc-date">Created on: {{ formatDate(doc.createTime) }}</p>
              </div>
              <div class="doc-card-footer">
                <el-button class="preview-btn" @click="openPreview(doc)">
                  <Eye :size="14" />
                  Preview Document
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Department Documents -->
        <el-tab-pane name="department">
          <template #label>
            <span class="tab-label-custom">
              <Briefcase :size="16" />
              Department Internal Documents
            </span>
          </template>

          <!-- Non-assigned department card -->
          <div v-if="!userStore.userInfo?.deptId" class="empty-tab-card">
            <Briefcase :size="40" class="empty-icon" />
            <h4 class="empty-title">No Department Allocated</h4>
            <p class="empty-desc">You must be assigned to a department to access department internal documents. Please contact your system administrator.</p>
          </div>

          <div v-else>
            <div v-if="departmentDocs.length === 0 && !loading" class="empty-tab-card">
              <FolderOpen :size="40" class="empty-icon" />
              <h4 class="empty-title">Empty Department Knowledge Base</h4>
              <p class="empty-desc">No internal documents have been allocated to the <strong>{{ userStore.userInfo?.deptName }}</strong> yet.</p>
            </div>

            <div v-else class="documents-grid">
              <div 
                v-for="doc in departmentDocs" 
                :key="doc.id" 
                class="doc-card"
                :class="{ 'is-locked': !doc.accessible }"
              >
                <!-- Document Card Header -->
                <div class="doc-card-header">
                  <div class="doc-icon-wrap" :class="{ 'locked': !doc.accessible }">
                    <Lock v-if="!doc.accessible" :size="18" />
                    <FileText v-else :size="18" />
                  </div>
                  <span class="security-badge" :class="'level-' + doc.securityLevel">
                    Level-{{ doc.securityLevel }} ({{ getClearanceLabel(doc.securityLevel) }})
                  </span>
                </div>

                <!-- Document Info -->
                <div class="doc-card-body">
                  <h3 class="doc-title">{{ doc.title }}</h3>
                  <p class="doc-date">Created on: {{ formatDate(doc.createTime) }}</p>
                </div>

                <!-- Document Card Footer -->
                <div class="doc-card-footer">
                  <el-button 
                    v-if="doc.accessible"
                    class="preview-btn"
                    @click="openPreview(doc)"
                  >
                    <Eye :size="14" />
                    Preview Document
                  </el-button>
                  <el-button 
                    v-else
                    type="warning"
                    class="request-access-btn"
                    @click="openRequestDialog(doc)"
                  >
                    <ShieldAlert :size="14" />
                    Request Access
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Document Preview Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="Knowledge Asset Preview"
      size="500px"
      class="doc-drawer"
      destroy-on-close
    >
      <div class="drawer-content" v-if="selectedDoc">
        <div class="drawer-header-info">
          <h2 class="drawer-doc-title">{{ selectedDoc.title }}</h2>
          <div class="drawer-meta-tags">
            <span class="security-badge" :class="'level-' + selectedDoc.securityLevel">
              Security: {{ selectedDoc.deptId ? 'Level-' + selectedDoc.securityLevel : 'Global' }}
            </span>
            <span class="dept-badge">
              Category: {{ selectedDoc.deptId ? userStore.userInfo?.deptName : 'System Guide' }}
            </span>
          </div>
        </div>

        <div class="drawer-section">
          <h4 class="section-title-label">Document Content Summary</h4>
          <div class="doc-summary-box">
            {{ selectedDoc.content }}
          </div>
        </div>

        <div class="drawer-section">
          <h4 class="section-title-label">RAG Vector Database Details</h4>
          <div class="rag-trace-box">
            <div class="trace-row">
              <span class="trace-lbl">Vector Database:</span>
              <span class="trace-val">Milvus (Index: IVF_FLAT)</span>
            </div>
            <div class="trace-row">
              <span class="trace-lbl">Similarity Metric:</span>
              <span class="trace-val">Cosine Similarity</span>
            </div>
            <div class="trace-row">
              <span class="trace-lbl">Access Clearance:</span>
              <span class="trace-val success-text">Access Granted (L{{ selectedDoc.securityLevel }})</span>
            </div>
            <div class="trace-row">
              <span class="trace-lbl">RAG Prompt Grounding:</span>
              <span class="trace-val">Enforced</span>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- Request Access Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="Request Document Access"
      width="440px"
      class="custom-dialog"
    >
      <div class="dialog-body" v-if="requestDoc">
        <p class="dialog-desc">
          You are requesting temporary access to the following confidential document:
        </p>
        <div class="request-doc-preview">
          <p class="req-title"><strong>{{ requestDoc.title }}</strong></p>
          <p class="req-meta">Requires Clearance: Level-{{ requestDoc.securityLevel }} (Your Clearance: Level-{{ userStore.userInfo?.clearanceLevel || 1 }})</p>
        </div>
        <p class="dialog-desc" style="margin-top: 16px;">
          This request will be routed directly to your Department Administrator (<strong>@{{ deptManager?.username || 'Department Manager' }}</strong>) for review and approval.
        </p>

        <el-form label-position="top">
          <el-form-item label="Reason for Request">
            <el-input 
              v-model="requestReason" 
              type="textarea" 
              :rows="3" 
              placeholder="E.g. Required for Q3 risk auditing reports."
              class="custom-textarea"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" class="dialog-btn-cancel">Cancel</el-button>
          <el-button 
            type="primary" 
            @click="handleRequestSubmit" 
            :loading="submitLoading" 
            class="dialog-btn-confirm"
          >
            Submit Request
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
import { 
  FileText, Lock, Eye, 
  FolderOpen, RefreshCw, ShieldAlert, Briefcase, BookOpen
} from 'lucide-vue-next'
import { getDeptDocuments, getDeptMembers } from '@/api/department'
import { sendNotification } from '@/api/notification'

const userStore = useUserStore()

const documents = ref<any[]>([])
const deptMembers = ref<any[]>([])
const loading = ref(false)
const submitLoading = ref(false)
const activeTab = ref('system')

const drawerVisible = ref(false)
const selectedDoc = ref<any>(null)

const dialogVisible = ref(false)
const requestDoc = ref<any>(null)
const requestReason = ref('')

// Filter System vs Department docs
const systemDocs = computed(() => {
  return documents.value.filter(doc => doc.deptId === null)
})

const departmentDocs = computed(() => {
  return documents.value.filter(doc => doc.deptId !== null)
})

// Find Department Administrator to route notification
const deptManager = computed(() => {
  return deptMembers.value.find(m => m.roles?.includes('ROLE_DEPT_ADMIN') || m.roles?.includes('ROLE_ADMIN'))
})

const fetchDocuments = async () => {
  loading.value = true
  try {
    const res: any = await getDeptDocuments()
    documents.value = res || []
  } catch (error: any) {
    console.error('Failed to load documents:', error)
    ElMessage.error('Failed to fetch documents list')
  } finally {
    loading.value = false
  }
}

const fetchDeptMembers = async () => {
  if (!userStore.userInfo?.deptId) return
  try {
    const res: any = await getDeptMembers()
    deptMembers.value = res || []
  } catch (error) {
    console.error('Failed to load department members:', error)
  }
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

const openPreview = (doc: any) => {
  selectedDoc.value = doc
  drawerVisible.value = true
}

const openRequestDialog = (doc: any) => {
  requestDoc.value = doc
  requestReason.value = ''
  dialogVisible.value = true
}

const handleRequestSubmit = async () => {
  if (!requestDoc.value) return
  
  const managerId = deptManager.value ? deptManager.value.id : 2
  
  submitLoading.value = true
  try {
    const payload = JSON.stringify({
      documentId: requestDoc.value.id,
      title: requestDoc.value.title,
      clearanceLevel: requestDoc.value.securityLevel,
      reason: requestReason.value || 'Required for standard business procedures.'
    })

    await sendNotification({
      receiverId: managerId,
      title: `RAG Permission Escalation Request`,
      content: `Employee @${userStore.userInfo?.username} requests temporary access to "${requestDoc.value.title}" (Security: Level-${requestDoc.value.securityLevel}).`,
      notifyType: 'RAG_APPLY',
      payload: payload
    })

    ElMessage.success('Escalation request submitted to your Department Administrator.')
    dialogVisible.value = false
  } catch (error: any) {
    console.error('Failed to submit request:', error)
    ElMessage.error(error.message || 'Failed to submit escalation request')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  fetchDocuments()
  fetchDeptMembers()
})
</script>

<style scoped>
.documents-page {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding: 16px 0;
}

/* ── Page Header ── */
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

/* ── Empty Tab Card ── */
.empty-tab-card {
  padding: 60px 40px;
  text-align: center;
  max-width: 440px;
  margin: 40px auto;
}

.empty-icon {
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 700;
  color: #374151;
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  margin: 0;
}

/* ── Documents Grid ── */
.documents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  padding-top: 16px;
}

.doc-card {
  background: #ffffff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 200px;
  transition: all 0.2s ease;
}

.doc-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.03);
}

.doc-card.is-locked {
  background: #fcfcfd;
}

.doc-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.doc-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eff6ff;
  color: #3b82f6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.doc-icon-wrap.system {
  background: #f0fdfa;
  color: #0d9488;
}

.doc-icon-wrap.locked {
  background: #fff1f2;
  color: #f43f5e;
}

.security-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  text-transform: uppercase;
}

.security-badge.level-1 {
  background: #f0fdf4;
  color: #16a34a;
}

.security-badge.level-2 {
  background: #eff6ff;
  color: #3b82f6;
}

.security-badge.level-3 {
  background: #fff1f2;
  color: #f43f5e;
}

.doc-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.doc-title {
  font-size: 14.5px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 6px 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.doc-date {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

.doc-card-footer {
  margin-top: 14px;
}

.preview-btn {
  width: 100%;
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-weight: 500 !important;
  height: 36px !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.preview-btn:hover {
  background: #f9fafb !important;
  border-color: #cbd5e1 !important;
  color: #111827 !important;
}

.request-access-btn {
  width: 100%;
  border-radius: 9px !important;
  height: 36px !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* ── Drawer & Dialog styles ── */
.doc-drawer :deep(.el-drawer__header) {
  margin: 0;
  padding: 24px;
  border-bottom: 1px solid #f3f4f6;
}

.doc-drawer :deep(.el-drawer__title) {
  font-weight: 600;
  color: #111827;
}

.drawer-content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.drawer-header-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drawer-doc-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  line-height: 1.3;
}

.drawer-meta-tags {
  display: flex;
  gap: 8px;
}

.dept-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  background: #f3f4f6;
  color: #4b5563;
}

.section-title-label {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  margin: 0 0 10px 0;
  letter-spacing: 0.5px;
}

.doc-summary-box {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  padding: 16px;
  font-size: 13.5px;
  color: #334155;
  line-height: 1.6;
}

.rag-trace-box {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trace-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.trace-lbl {
  color: #64748b;
}

.trace-val {
  font-weight: 500;
  color: #334155;
}

.success-text {
  color: #16a34a;
}

/* ── Custom Dialog ── */
.custom-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

.custom-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px;
  border-bottom: 1px solid #f3f4f6;
}

.custom-dialog :deep(.el-dialog__title) {
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
  margin: 0;
}

.request-doc-preview {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px 16px;
  margin: 14px 0;
}

.req-title {
  font-size: 14px;
  color: #111827;
  margin: 0 0 4px 0;
}

.req-meta {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
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
}

.dialog-btn-confirm:hover {
  opacity: 0.9;
}
</style>
