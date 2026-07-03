<template>
  <div class="message-center">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">Messages & Notifications</h1>
        <p class="page-sub">Handle system-wide notifications, approvals, bug trace audits, and user messages.</p>
      </div>
      <div class="header-actions" style="display: flex; gap: 10px; align-items: center;">
        <el-button class="send-btn" @click="openSendDialog">
          <Plus :size="14" />
          Send Message
        </el-button>
        <el-button class="refresh-btn" @click="fetchData" :loading="loading">
          <RefreshCw :size="14" :class="{ 'spin': loading }" />
          Refresh
        </el-button>
      </div>
    </div>

    <!-- Main Workspace -->
    <div class="workspace-card">
      <el-row :gutter="0" class="workspace-row">
        <!-- Left Sidebar: Filters -->
        <el-col :span="5" class="filter-sidebar">
          <div class="filter-group">
            <div 
              class="filter-item" 
              :class="{ 'active': activeFilter === 'all' }"
              @click="changeFilter('all')"
            >
              <Inbox :size="16" />
              <span>All Messages</span>
              <span v-if="unreadCounts.all > 0" class="count-badge">{{ unreadCounts.all }}</span>
            </div>
            <div 
              class="filter-item" 
              :class="{ 'active': activeFilter === 'unread' }"
              @click="changeFilter('unread')"
            >
              <Mail :size="16" />
              <span>Unread</span>
              <span v-if="unreadCounts.unread > 0" class="count-badge danger">{{ unreadCounts.unread }}</span>
            </div>
            <div 
              class="filter-item" 
              :class="{ 'active': activeFilter === 'pending' }"
              @click="changeFilter('pending')"
            >
              <Clock :size="16" />
              <span>Pending Approvals</span>
              <span v-if="unreadCounts.pending > 0" class="count-badge warning">{{ unreadCounts.pending }}</span>
            </div>
            <div 
              class="filter-item" 
              :class="{ 'active': activeFilter === 'sent' }"
              @click="changeFilter('sent')"
            >
              <Send :size="16" />
              <span>Sent Messages</span>
            </div>
          </div>
        </el-col>

        <!-- Middle Column: Message List -->
        <el-col :span="9" class="message-list-col">
          <div v-if="filteredMessages.length === 0" class="empty-list">
            <Mail :size="48" class="empty-icon" />
            <h3>No Messages Found</h3>
            <p>There are no messages or notifications in this folder.</p>
          </div>
          <div v-else class="message-list">
            <div 
              v-for="msg in filteredMessages" 
              :key="msg.id" 
              class="message-item"
              :class="{ 
                'active': selectedMessage?.id === msg.id,
                'unread': msg.status === 0 && activeFilter !== 'sent'
              }"
              @click="selectMessage(msg)"
            >
              <div class="msg-avatar-wrapper">
                <div class="msg-avatar" :class="getAvatarClass(msg)">
                  {{ getSenderInitial(msg) }}
                </div>
                <div v-if="msg.status === 0 && activeFilter !== 'sent'" class="unread-dot"></div>
              </div>
              <div class="msg-content-preview">
                <div class="msg-meta-row">
                  <span class="msg-sender">
                    {{ activeFilter === 'sent' ? `To: ${msg.receiverRealName || msg.receiverName}` : (msg.senderRealName || msg.senderName) }}
                  </span>
                  <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
                </div>
                <div class="msg-title-row">
                  <span class="msg-title">{{ msg.title }}</span>
                  <span class="type-badge" :class="msg.notifyType.toLowerCase()">
                    {{ msg.notifyType }}
                  </span>
                </div>
                <p class="msg-summary">{{ msg.content }}</p>
                <div class="msg-status-row" v-if="msg.status >= 2">
                  <span class="status-badge" :class="getStatusClass(msg.status)">
                    {{ getStatusText(msg.status) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </el-col>

        <!-- Right Column: Message Detail -->
        <el-col :span="10" class="message-detail-col">
          <div v-if="!selectedMessage" class="empty-detail">
            <Inbox :size="48" class="empty-icon" />
            <h3>Select a Message</h3>
            <p>Select a message to view its details.</p>
          </div>
          <div v-else class="message-detail">
            <!-- Detail Header -->
            <div class="detail-header">
              <div class="detail-title-row">
                <h2>{{ selectedMessage.title }}</h2>
                <span class="type-badge large" :class="selectedMessage.notifyType.toLowerCase()">
                  {{ selectedMessage.notifyType }}
                </span>
              </div>
              <div class="detail-meta-grid">
                <div class="meta-item">
                  <span class="meta-label">From:</span>
                  <span class="meta-value highlight">{{ selectedMessage.senderRealName || selectedMessage.senderName }}</span>
                  <span class="meta-sub" v-if="selectedMessage.senderId > 0">@{{ selectedMessage.senderName }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">To:</span>
                  <span class="meta-value">{{ selectedMessage.receiverRealName || selectedMessage.receiverName }}</span>
                  <span class="meta-sub">@{{ selectedMessage.receiverName }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Date:</span>
                  <span class="meta-value">{{ formatFullDate(selectedMessage.createTime) }}</span>
                </div>
                <div class="meta-item" v-if="selectedMessage.status >= 2">
                  <span class="meta-label">Status:</span>
                  <span class="status-badge" :class="getStatusClass(selectedMessage.status)">
                    {{ getStatusText(selectedMessage.status) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Detail Body -->
            <div class="detail-body">
              <div class="message-text">
                <p>{{ selectedMessage.content }}</p>
              </div>

              <!-- Custom Renderer: RAG_APPLY -->
              <div v-if="selectedMessage.notifyType === 'RAG_APPLY' && parsedPayload" class="payload-container rag-card">
                <div class="payload-header">
                  <BookOpen :size="16" />
                  <span>RAG Permission Request Details</span>
                </div>
                <div class="payload-body">
                  <div class="info-grid">
                    <div class="info-cell">
                      <span class="info-label">Applicant:</span>
                      <span class="info-val">{{ parsedPayload.username }} (ID: {{ parsedPayload.userId }})</span>
                    </div>
                    <div class="info-cell">
                      <span class="info-label">Target Document:</span>
                      <span class="info-val code-font">{{ parsedPayload.docName }}</span>
                    </div>
                    <div class="info-cell">
                      <span class="info-label">Document ID:</span>
                      <span class="info-val">{{ parsedPayload.docId }}</span>
                    </div>
                    <div class="info-cell">
                      <span class="info-label">Required Security Level:</span>
                      <span class="info-val">
                        <span class="level-tag" :class="'level-' + parsedPayload.level">
                          Level {{ parsedPayload.level }}
                        </span>
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Custom Renderer: SQL_AUDIT -->
              <div v-if="selectedMessage.notifyType === 'SQL_AUDIT' && parsedPayload" class="payload-container sql-card">
                <div class="payload-header">
                  <Database :size="16" />
                  <span>SQL Interception Audit details</span>
                </div>
                <div class="payload-body">
                  <div class="audit-meta">
                    <span class="meta-lbl">Triggered By:</span>
                    <span class="meta-val">{{ parsedPayload.username }} (ID: {{ parsedPayload.userId }})</span>
                  </div>
                  <div class="audit-meta">
                    <span class="meta-lbl">Intercept Reason:</span>
                    <span class="meta-val warning-text">{{ parsedPayload.reason }}</span>
                  </div>
                  <div class="sql-code-block">
                    <div class="code-header">Generated SQL Query</div>
                    <pre><code>{{ parsedPayload.sql }}</code></pre>
                  </div>
                </div>
              </div>

              <!-- Custom Renderer: BUG_REPORT -->
              <div v-if="selectedMessage.notifyType === 'BUG_REPORT' && parsedPayload" class="payload-container bug-card">
                <div class="payload-header">
                  <Bug :size="16" />
                  <span>AI Agent Execution Trace Panel</span>
                </div>
                <div class="payload-body">
                  <el-collapse class="trace-collapse">
                    <el-collapse-item title="1. User Prompt" name="1">
                      <div class="trace-box">{{ parsedPayload.prompt }}</div>
                    </el-collapse-item>
                    <el-collapse-item title="2. RAG Retrieved Documents (Milvus Top-K)" name="2">
                      <pre class="trace-box pre-wrap">{{ parsedPayload.milvusTopK || 'No retrieval data.' }}</pre>
                    </el-collapse-item>
                    <el-collapse-item title="3. LLM Response Output" name="3">
                      <pre class="trace-box pre-wrap">{{ parsedPayload.response || 'No response content.' }}</pre>
                    </el-collapse-item>
                    <el-collapse-item title="4. Generated SQL Code" name="4" v-if="parsedPayload.generatedSql">
                      <pre class="trace-box sql-font"><code>{{ parsedPayload.generatedSql }}</code></pre>
                    </el-collapse-item>
                    <el-collapse-item title="5. System Error Output" name="5" v-if="parsedPayload.error">
                      <pre class="trace-box error-text">{{ parsedPayload.error }}</pre>
                    </el-collapse-item>
                  </el-collapse>
                </div>
              </div>

              <!-- HITL Actions (Only if status is Pending Approval (2)) -->
              <div v-if="selectedMessage.status === 2 && activeFilter !== 'sent'" class="approval-actions-box">
                <h4>Human-in-the-Loop Audit Decision</h4>
                <el-input
                  v-model="opinion"
                  placeholder="Enter approval opinion or remarks (optional)..."
                  type="textarea"
                  :rows="2"
                  class="opinion-input"
                />
                <div class="action-btn-row">
                  <el-button 
                    type="success" 
                    @click="submitApproval('APPROVE')" 
                    :loading="submittingAction"
                    class="btn-approve"
                  >
                    <Check :size="14" />
                    Approve / Release
                  </el-button>
                  <el-button 
                    type="danger" 
                    @click="submitApproval('DENY')" 
                    :loading="submittingAction"
                    class="btn-deny"
                  >
                    <X :size="14" />
                    Deny / Block
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- Send Message Dialog -->
    <el-dialog
      v-model="sendDialogVisible"
      title="Compose Message"
      width="520px"
      class="custom-dialog"
      :before-close="closeSendDialog"
    >
      <el-form :model="sendForm" :rules="sendRules" ref="sendFormRef" label-position="top">
        <el-form-item label="Recipient" prop="receiverId">
          <el-select v-model="sendForm.receiverId" placeholder="Select recipient..." style="width: 100%">
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="`${user.realName || user.username} (@${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Message Type" prop="notifyType">
          <el-select v-model="sendForm.notifyType" placeholder="Select type..." style="width: 100%" @change="onNotifyTypeChange">
            <el-option label="Chat Message" value="CHAT" />
            <el-option label="RAG Permission Escalation (Mock Request)" value="RAG_APPLY" />
            <el-option label="SQL Security Intercept (Mock Intercept)" value="SQL_AUDIT" />
            <el-option label="Bug / Hallucination Trace Report (Mock Bug)" value="BUG_REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Title" prop="title">
          <el-input v-model="sendForm.title" placeholder="Enter message title..." />
        </el-form-item>
        <el-form-item label="Content" prop="content">
          <el-input 
            v-model="sendForm.content" 
            type="textarea" 
            :rows="4" 
            placeholder="Type your message content here..." 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeSendDialog">Cancel</el-button>
          <el-button type="primary" @click="submitSendMessage" :loading="sendingMessage">Send</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@stores/modules/user'
import { 
  getNotifications, 
  markAsRead, 
  handleAction, 
  sendNotification, 
  getUsers 
} from '@/api/notification'
import { 
  Plus, RefreshCw, Inbox, Mail, Send, 
  Clock, Check, X, BookOpen, Database, Bug
} from 'lucide-vue-next'

const userStore = useUserStore()
const loading = ref(false)
const submittingAction = ref(false)
const sendingMessage = ref(false)

const messages = ref<any[]>([])
const selectedMessage = ref<any>(null)
const opinion = ref('')
const activeFilter = ref('all') // all, unread, pending, sent

// Unread Counts
const unreadCounts = ref({
  all: 0,
  unread: 0,
  pending: 0
})

// Dialog Setup
const sendDialogVisible = ref(false)
const userList = ref<any[]>([])
const sendFormRef = ref<any>(null)
const sendForm = ref({
  receiverId: null as number | null,
  notifyType: 'CHAT',
  title: '',
  content: '',
  payload: ''
})

const sendRules = {
  receiverId: [{ required: true, message: 'Please select a recipient', trigger: 'change' }],
  notifyType: [{ required: true, message: 'Please select a type', trigger: 'change' }],
  title: [{ required: true, message: 'Please enter a title', trigger: 'blur' }],
  content: [{ required: true, message: 'Please enter content', trigger: 'blur' }]
}

// Computeds
const filteredMessages = computed(() => {
  if (activeFilter.value === 'all') {
    return messages.value
  } else if (activeFilter.value === 'unread') {
    return messages.value.filter(m => m.status === 0)
  } else if (activeFilter.value === 'pending') {
    return messages.value.filter(m => m.status === 2)
  } else if (activeFilter.value === 'sent') {
    return messages.value // listNotifications handles sent query differently, which we reload
  }
  return messages.value
})

const parsedPayload = computed(() => {
  if (!selectedMessage.value?.payload) return null
  try {
    return JSON.parse(selectedMessage.value.payload)
  } catch (e) {
    console.error('Failed to parse message payload JSON', e)
    return null
  }
})

// Actions
const changeFilter = (filter: string) => {
  activeFilter.value = filter
  selectedMessage.value = null
  fetchData()
}

const fetchData = async () => {
  loading.value = true
  try {
    // 1. Fetch main notifications list
    const notifyTypeQuery = activeFilter.value === 'sent' ? 'SENT' : ''
    const statusQuery = activeFilter.value === 'unread' ? 0 : (activeFilter.value === 'pending' ? 2 : undefined)
    
    const data: any = await getNotifications({ status: statusQuery, notifyType: notifyTypeQuery })
    messages.value = data || []

    // 2. Fetch unread counts for badges (if not in Sent tab)
    if (activeFilter.value !== 'sent') {
      const allData: any = await getNotifications()
      const allList = allData || []
      unreadCounts.value.all = allList.length
      unreadCounts.value.unread = allList.filter((m: any) => m.status === 0).length
      unreadCounts.value.pending = allList.filter((m: any) => m.status === 2).length
    }
  } catch (e: any) {
    ElMessage.error('Failed to load messages: ' + e.message)
  } finally {
    loading.value = false
  }
}

const selectMessage = async (msg: any) => {
  selectedMessage.value = msg
  opinion.value = ''
  
  // Mark as read immediately if it's currently unread and we are in inbox
  if (msg.status === 0 && activeFilter.value !== 'sent') {
    try {
      await markAsRead(msg.id)
      msg.status = 1 // update state locally
      // refresh badges
      unreadCounts.value.unread = Math.max(0, unreadCounts.value.unread - 1)
    } catch (e) {
      console.error(e)
    }
  }
}

const submitApproval = async (action: 'APPROVE' | 'DENY') => {
  if (!selectedMessage.value) return
  submittingAction.value = true
  try {
    await handleAction({
      notificationId: selectedMessage.value.id,
      action,
      opinion: opinion.value
    })
    ElMessage.success(`Decision submitted: ${action === 'APPROVE' ? 'Approved' : 'Denied'}`)
    
    // update status locally
    selectedMessage.value.status = action === 'APPROVE' ? 3 : 4
    if (opinion.value) {
      selectedMessage.value.content += ` (Approval Opinion: ${opinion.value})`
    }
    
    // refresh unread count / pending badges
    fetchData()
  } catch (e: any) {
    ElMessage.error('Failed to submit decision: ' + e.message)
  } finally {
    submittingAction.value = false
  }
}

// Compose Dialog Actions
const openSendDialog = async () => {
  sendDialogVisible.value = true
  sendForm.value = {
    receiverId: null,
    notifyType: 'CHAT',
    title: '',
    content: '',
    payload: ''
  }
  
  try {
    const users: any = await getUsers()
    // Exclude current user from recipient selection
    userList.value = (users || []).filter((u: any) => u.username !== userStore.userInfo?.username)
  } catch (e: any) {
    ElMessage.error('Failed to load user list: ' + e.message)
  }
}

const closeSendDialog = () => {
  sendDialogVisible.value = false
  if (sendFormRef.value) {
    sendFormRef.value.resetFields()
  }
}

const onNotifyTypeChange = (type: string) => {
  // Pre-fill mock data for simulation types
  if (type === 'CHAT') {
    sendForm.value.title = ''
    sendForm.value.content = ''
    sendForm.value.payload = ''
  } else if (type === 'RAG_APPLY') {
    sendForm.value.title = 'RAG Permission Escalation Request'
    sendForm.value.content = 'User [zhangsan] requests temporary access to "Q1 2025 Credit Assessment Report" (Clearance: Level-3).'
    sendForm.value.payload = JSON.stringify({
      userId: 2,
      username: 'zhangsan',
      docId: 101,
      docName: 'Q1_2025_Credit_Assessment_Report.pdf',
      level: 3
    }, null, 2)
  } else if (type === 'SQL_AUDIT') {
    sendForm.value.title = 'Warning: Risky SQL Execution Request'
    sendForm.value.content = 'User [lisi] tried to execute a database modification query via Code Agent. Intercepted by system sandbox.'
    sendForm.value.payload = JSON.stringify({
      userId: 3,
      username: 'lisi',
      sql: 'DELETE FROM bank_ledger WHERE create_time < \'2025-01-01\'',
      reason: 'AST Security Interceptor: contains DELETE keyword'
    }, null, 2)
  } else if (type === 'BUG_REPORT') {
    sendForm.value.title = 'AI Hallucination Feedback Report'
    sendForm.value.content = 'User [zhangsan] reported an AI response hallucination and uploaded the execution trace.'
    sendForm.value.payload = JSON.stringify({
      prompt: 'What is the credit limit of Zhang San?',
      response: 'Zhang San currently has a credit limit of 10M, but has multiple bad assets under his name. Recommend downgrading...',
      milvusTopK: '1. Credit Manual Article 12: Downgrade rules...\n2. Credit Assessment: Zhang San\'s actual limit is 2M...',
      error: 'LLM output mismatch with Milvus retrieved facts.'
    }, null, 2)
  }
}

const submitSendMessage = () => {
  if (!sendFormRef.value) return
  sendFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      sendingMessage.value = true
      try {
        await sendNotification({
          receiverId: sendForm.value.receiverId!,
          title: sendForm.value.title,
          content: sendForm.value.content,
          notifyType: sendForm.value.notifyType,
          payload: sendForm.value.payload || undefined
        })
        ElMessage.success('Message sent successfully!')
        closeSendDialog()
        fetchData()
      } catch (e: any) {
        ElMessage.error('Failed to send message: ' + e.message)
      } finally {
        sendingMessage.value = false
      }
    }
  })
}

// View Utilities
const getSenderInitial = (msg: any) => {
  const name = activeFilter.value === 'sent' 
    ? (msg.receiverRealName || msg.receiverName) 
    : (msg.senderRealName || msg.senderName)
  return (name || 'U').charAt(0).toUpperCase()
}

const getAvatarClass = (msg: any) => {
  if (msg.senderId === 0) return 'system'
  const name = msg.senderRealName || msg.senderName || 'user'
  const val = name.charCodeAt(0) % 4
  return ['blue', 'emerald', 'indigo', 'purple'][val]
}

const getStatusClass = (status: number) => {
  return ['unread', 'read', 'pending', 'approved', 'denied'][status] || 'default'
}

const getStatusText = (status: number) => {
  return ['Unread', 'Read', 'Pending Approval', 'Approved', 'Denied'][status] || 'Unknown'
}

const formatTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const formatFullDate = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('en-US', { hour12: false })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.message-center {
  padding: 16px 0;
  max-width: 1200px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
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
  gap: 12px;
  align-items: center;
}

.send-btn {
  background: #111827 !important;
  border: 1px solid #111827 !important;
  border-radius: 9px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.send-btn:hover {
  background: #1f2937 !important;
  border-color: #1f2937 !important;
  color: #fff !important;
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

.send-btn :deep(span),
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

/* Workspace Layout */
.workspace-card {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  overflow: hidden;
  height: calc(100vh - 180px);
}

.workspace-row {
  height: 100%;
}

/* Left Sidebar */
.filter-sidebar {
  border-right: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 16px 8px;
  height: 100%;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: 8px;
  color: #475569;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-item:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.filter-item.active {
  background: #e2e8f0;
  color: #0f172a;
}

.count-badge {
  margin-left: auto;
  font-size: 11px;
  background: #cbd5e1;
  color: #334155;
  border-radius: 9999px;
  height: 18px;
  min-width: 18px;
  line-height: 18px;
  text-align: center;
  padding: 0 6px;
  font-weight: 600;
}

.count-badge.danger {
  background: #ef4444;
  color: #ffffff;
}

.count-badge.warning {
  background: #f59e0b;
  color: #ffffff;
}

/* Middle Column: Message List */
.message-list-col {
  border-right: 1px solid #e2e8f0;
  height: 100%;
  overflow-y: auto;
}

.empty-list,
.empty-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #94a3b8;
  padding: 32px;
  text-align: center;
  box-sizing: border-box;
}

.empty-list h3,
.empty-detail h3 {
  color: #475569;
  font-size: 16px;
  margin: 16px 0 8px;
  font-weight: 600;
}

.empty-list p,
.empty-detail p {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
  line-height: 1.5;
  max-width: 320px;
}

.empty-icon {
  margin-bottom: 12px;
}

.message-list {
  display: flex;
  flex-direction: column;
}

.message-item {
  display: flex;
  gap: 16px;
  padding: 18px 16px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.18s ease;
}

.message-item:hover {
  background: #f8fafc;
}

.message-item.active {
  background: #f0fdf4; /* subtle green highlight for active message */
  border-left: 3px solid #10b981;
}

.message-item.unread {
  background: #f1f5f9;
}

.msg-avatar-wrapper {
  position: relative;
}

.msg-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #ffffff;
  font-size: 16px;
}

.msg-avatar.system { background: #64748b; }
.msg-avatar.blue { background: #3b82f6; }
.msg-avatar.emerald { background: #10b981; }
.msg-avatar.indigo { background: #6366f1; }
.msg-avatar.purple { background: #a855f7; }

.unread-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 10px;
  height: 10px;
  background: #ef4444;
  border-radius: 50%;
  border: 2px solid #ffffff;
}

.msg-content-preview {
  flex: 1;
  min-width: 0;
}

.msg-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.msg-sender {
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
}

.msg-time {
  font-size: 11px;
  color: #94a3b8;
}

.msg-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.msg-title {
  font-weight: 500;
  color: #334155;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.type-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}

.type-badge.chat { background: #dbeafe; color: #1e40af; }
.type-badge.rag_apply { background: #fef3c7; color: #92400e; }
.type-badge.sql_audit { background: #fee2e2; color: #991b1b; }
.type-badge.bug_report { background: #f3e8ff; color: #6b21a8; }

.msg-summary {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8px;
}

.status-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 12px;
  display: inline-block;
}

.status-badge.pending { background: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.status-badge.approved { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
.status-badge.denied { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
.status-badge.unread { background: #eff6ff; color: #1d4ed8; }
.status-badge.read { background: #f1f5f9; color: #475569; }

/* Right Column: Message Detail */
.message-detail-col {
  height: 100%;
  overflow-y: auto;
  background: #ffffff;
}



.message-detail {
  padding: 24px;
}

.detail-header {
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 20px;
  margin-bottom: 20px;
}

.detail-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-title-row h2 {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.type-badge.large {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 6px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
}

.meta-label {
  color: #64748b;
  font-weight: 500;
}

.meta-value {
  color: #1e293b;
  font-weight: 600;
}

.meta-value.highlight {
  color: #10b981;
}

.meta-sub {
  color: #94a3b8;
  font-size: 12px;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-text {
  font-size: 14.5px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* Payloads */
.payload-container {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.payload-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
}

.payload-body {
  padding: 16px;
  background: #f8fafc;
}

.rag-card .payload-header { background: #fef3c7; color: #92400e; }
.sql-card .payload-header { background: #fee2e2; color: #991b1b; }
.bug-card .payload-header { background: #f3e8ff; color: #6b21a8; }

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.info-val {
  font-size: 13.5px;
  color: #1e293b;
  font-weight: 600;
}

.code-font {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  font-size: 12.5px;
}

.level-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
}

.level-tag.level-1 { background: #e2e8f0; color: #475569; }
.level-tag.level-2 { background: #dbeafe; color: #1e40af; }
.level-tag.level-3 { background: #fef3c7; color: #d97706; }

/* SQL Interception CSS */
.audit-meta {
  margin-bottom: 8px;
  font-size: 13.5px;
}

.meta-lbl {
  color: #64748b;
  margin-right: 6px;
}

.meta-val {
  font-weight: 600;
  color: #1e293b;
}

.warning-text {
  color: #ea580c;
}

.sql-code-block {
  margin-top: 12px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #cbd5e1;
}

.code-header {
  background: #e2e8f0;
  padding: 6px 12px;
  font-size: 11.5px;
  font-weight: 700;
  color: #475569;
  text-transform: uppercase;
}

.sql-code-block pre {
  margin: 0;
  background: #1e293b;
  color: #38bdf8;
  padding: 12px;
  overflow-x: auto;
}

.sql-code-block code {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

/* Bug Trace Panel CSS */
.trace-collapse {
  background: transparent !important;
  border: none !important;
}

:deep(.el-collapse-item__header) {
  background: #f1f5f9 !important;
  padding: 0 12px !important;
  border-radius: 4px !important;
  margin-bottom: 4px !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  color: #334155 !important;
  border: 1px solid #e2e8f0 !important;
}

:deep(.el-collapse-item__wrap) {
  background: transparent !important;
  border: none !important;
}

.trace-box {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
  margin: 4px 0 12px;
  max-height: 200px;
  overflow-y: auto;
}

.pre-wrap {
  white-space: pre-wrap;
  font-family: inherit;
}

.sql-font {
  background: #1e293b;
  color: #a7f3d0;
  font-family: Consolas, Monaco, monospace;
}

.error-text {
  color: #ef4444;
  font-family: Consolas, Monaco, monospace;
  background: #fef2f2;
  border-color: #fee2e2;
}

/* HITL Action Box */
.approval-actions-box {
  margin-top: 24px;
  padding: 20px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
}

.approval-actions-box h4 {
  font-size: 14.5px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 12px;
}

.opinion-input {
  margin-bottom: 16px;
}

.action-btn-row {
  display: flex;
  gap: 12px;
}

.btn-approve, .btn-deny {
  display: flex;
  align-items: center;
  gap: 6px;
  border-radius: 6px;
  flex: 1;
}

/* Compose Dialog */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.custom-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #f1f5f9;
  margin-right: 0;
  padding-bottom: 16px;
}

.custom-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: #0f172a;
}

.custom-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #f1f5f9;
  padding-top: 16px;
  margin-top: 20px;
}
</style>
