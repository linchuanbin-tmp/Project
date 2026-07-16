<template>
  <div class="message-center">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('notification.title') }}</h1>
        <p class="page-sub">{{ $t('notification.subtitle') }}</p>
      </div>
      <div class="header-actions" style="display: flex; gap: 10px; align-items: center;">
        <el-button class="send-btn" @click="openSendDialog">
          <Plus :size="14" />
          {{ $t('notification.sendMessage') }}
        </el-button>
        <el-button class="refresh-btn" @click="fetchData" :loading="loading">
          <RefreshCw :size="14" :class="{ 'spin': loading }" />
          {{ $t('common.refresh') }}
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
              <span>{{ $t('notification.allMessages') }}</span>
              <span v-if="unreadCounts.all > 0" class="count-badge">{{ unreadCounts.all }}</span>
            </div>
            <div
              class="filter-item"
              :class="{ 'active': activeFilter === 'unread' }"
              @click="changeFilter('unread')"
            >
              <Mail :size="16" />
              <span>{{ $t('notification.unread') }}</span>
              <span v-if="unreadCounts.unread > 0" class="count-badge danger">{{ unreadCounts.unread }}</span>
            </div>
            <div
              class="filter-item"
              :class="{ 'active': activeFilter === 'pending' }"
              @click="changeFilter('pending')"
            >
              <Clock :size="16" />
              <span>{{ $t('notification.pendingApprovals') }}</span>
              <span v-if="unreadCounts.pending > 0" class="count-badge warning">{{ unreadCounts.pending }}</span>
            </div>
            <div
              class="filter-item"
              :class="{ 'active': activeFilter === 'sent' }"
              @click="changeFilter('sent')"
            >
              <Send :size="16" />
              <span>{{ $t('notification.sentMessages') }}</span>
            </div>
          </div>
        </el-col>

        <!-- Middle Column: Message List -->
        <el-col :span="9" class="message-list-col">
          <div v-if="filteredMessages.length === 0" class="empty-list">
            <Mail :size="48" class="empty-icon" />
            <h3>{{ $t('notification.noMessages') }}</h3>
            <p>{{ $t('notification.noMessagesDesc') }}</p>
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
            <h3>{{ $t('notification.selectMessage') }}</h3>
            <p>{{ $t('notification.selectMessageDesc') }}</p>
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
                  <span class="meta-label">{{ $t('notification.from') }}:</span>
                  <span class="user-capsule-premium sender">
                    <span class="capsule-name">{{ selectedMessage.senderRealName || selectedMessage.senderName }}</span>
                    <span class="capsule-handle" v-if="selectedMessage.senderId > 0">@{{ selectedMessage.senderName }}</span>
                  </span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">{{ $t('notification.to') }}:</span>
                  <span class="user-capsule-premium receiver">
                    <span class="capsule-name">{{ selectedMessage.receiverRealName || selectedMessage.receiverName }}</span>
                    <span class="capsule-handle">@{{ selectedMessage.receiverName }}</span>
                  </span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">{{ $t('notification.date') }}:</span>
                  <span class="meta-value">{{ formatFullDate(selectedMessage.createTime) }}</span>
                </div>
                <div class="meta-item" v-if="selectedMessage.status >= 2">
                  <span class="meta-label">{{ $t('notification.status') }}:</span>
                  <span class="status-badge" :class="getStatusClass(selectedMessage.status)">
                    {{ getStatusText(selectedMessage.status) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Detail Body -->
            <div class="detail-body">
              
              <!-- Thread Messages Timeline Container -->
              <div class="thread-timeline">
                <div 
                  v-for="msgItem in threadMessages" 
                  :key="msgItem.id" 
                  class="timeline-bubble-item" 
                  :class="{ 
                    'sent-by-me': msgItem.senderName === userStore.userInfo?.username,
                    'ticket-bubble': msgItem.notifyType === 'SUPPORT_TICKET' || msgItem.notifyType === 'BUG_REPORT'
                  }"
                >
                  <div class="bubble-header">
                    <span class="bubble-sender-name">
                      {{ msgItem.senderName === userStore.userInfo?.username ? $t('notification.sentMessages') : (msgItem.senderRealName || msgItem.senderName) }}
                    </span>
                    <span class="bubble-time">{{ formatTime(msgItem.createTime) }}</span>
                  </div>
                  <div class="bubble-content-wrap">
                    <p class="bubble-text">{{ msgItem.content }}</p>

                    <!-- Render payload ONLY on the message that contains it -->
                    <!-- Custom Renderer: RAG_APPLY -->
                    <div v-if="msgItem.notifyType === 'RAG_APPLY' && msgItem.payload && parseSinglePayload(msgItem.payload)" class="payload-container rag-card">
                      <div class="payload-header">
                        <BookOpen :size="16" />
                        <span>RAG Permission Request Details</span>
                      </div>
                      <div class="payload-body">
                        <div class="info-grid">
                          <div class="info-cell">
                            <span class="info-label">Applicant:</span>
                            <span class="info-val">{{ parseSinglePayload(msgItem.payload).username }} (ID: {{ parseSinglePayload(msgItem.payload).userId }})</span>
                          </div>
                          <div class="info-cell">
                            <span class="info-label">Target Document:</span>
                            <span class="info-val code-font">{{ parseSinglePayload(msgItem.payload).docName }}</span>
                          </div>
                          <div class="info-cell">
                            <span class="info-label">Document ID:</span>
                            <span class="info-val">{{ parseSinglePayload(msgItem.payload).docId }}</span>
                          </div>
                          <div class="info-cell">
                            <span class="info-label">Required Security Level:</span>
                            <span class="info-val">
                              <span class="level-tag" :class="'level-' + parseSinglePayload(msgItem.payload).level">
                                Level {{ parseSinglePayload(msgItem.payload).level }}
                              </span>
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    <!-- Custom Renderer: SQL_AUDIT -->
                    <div v-if="msgItem.notifyType === 'SQL_AUDIT' && msgItem.payload && parseSinglePayload(msgItem.payload)" class="payload-container sql-card">
                      <div class="payload-header">
                        <Database :size="16" />
                        <span>SQL Interception Audit details</span>
                      </div>
                      <div class="payload-body">
                        <div class="audit-meta">
                          <span class="meta-lbl">Triggered By:</span>
                          <span class="meta-val">{{ parseSinglePayload(msgItem.payload).username }} (ID: {{ parseSinglePayload(msgItem.payload).userId }})</span>
                        </div>
                        <div class="audit-meta">
                          <span class="meta-lbl">Intercept Reason:</span>
                          <span class="meta-val warning-text">{{ parseSinglePayload(msgItem.payload).reason }}</span>
                        </div>
                        <div class="sql-code-block">
                          <div class="code-header">Generated SQL Query</div>
                          <pre><code>{{ parseSinglePayload(msgItem.payload).sql }}</code></pre>
                        </div>
                      </div>
                    </div>

                    <!-- Custom Renderer: BUG_REPORT -->
                    <div v-if="msgItem.notifyType === 'BUG_REPORT' && msgItem.payload && parseSinglePayload(msgItem.payload)" class="payload-container bug-card">
                      <div class="payload-header">
                        <Bug :size="16" />
                        <span>AI Agent Execution Trace Report</span>
                      </div>
                      <div class="payload-body">
                        <el-collapse class="trace-collapse">
                          <!-- Task-based report -->
                          <template v-if="parseSinglePayload(msgItem.payload).taskId">
                            <el-collapse-item title="Task Info" name="0">
                              <div class="trace-meta-row">
                                <span class="trace-meta-label">Task ID:</span>
                                <span class="trace-meta-value">{{ parseSinglePayload(msgItem.payload).taskId }}</span>
                              </div>
                              <div class="trace-meta-row">
                                <span class="trace-meta-label">Type:</span>
                                <span class="trace-meta-value">{{ parseSinglePayload(msgItem.payload).taskType }}</span>
                              </div>
                              <div class="trace-meta-row">
                                <span class="trace-meta-label">Status:</span>
                                <span class="trace-meta-value">{{ parseSinglePayload(msgItem.payload).status }}</span>
                              </div>
                              <div v-if="parseSinglePayload(msgItem.payload).elapsedTime" class="trace-meta-row">
                                <span class="trace-meta-label">Elapsed:</span>
                                <span class="trace-meta-value">{{ parseSinglePayload(msgItem.payload).elapsedTime }}ms</span>
                              </div>
                              <div v-if="parseSinglePayload(msgItem.payload).createdAt" class="trace-meta-row">
                                <span class="trace-meta-label">Created:</span>
                                <span class="trace-meta-value">{{ parseSinglePayload(msgItem.payload).createdAt }}</span>
                              </div>
                            </el-collapse-item>
                            <el-collapse-item title="User Input" name="1">
                              <div class="trace-box">{{ parseSinglePayload(msgItem.payload).input }}</div>
                            </el-collapse-item>
                            <el-collapse-item title="Output / Result" name="2" v-if="parseSinglePayload(msgItem.payload).output">
                              <pre class="trace-box pre-wrap">{{ formatOutputPreview(parseSinglePayload(msgItem.payload).output) }}</pre>
                            </el-collapse-item>
                            <el-collapse-item title="Error Message" name="3" v-if="parseSinglePayload(msgItem.payload).errorMsg">
                              <pre class="trace-box error-text">{{ parseSinglePayload(msgItem.payload).errorMsg }}</pre>
                            </el-collapse-item>
                          </template>
                          <!-- Legacy mock report -->
                          <template v-else>
                            <el-collapse-item title="1. User Prompt" name="1">
                              <div class="trace-box">{{ parseSinglePayload(msgItem.payload).prompt }}</div>
                            </el-collapse-item>
                            <el-collapse-item title="2. RAG Retrieved Documents (Milvus Top-K)" name="2">
                              <pre class="trace-box pre-wrap">{{ parseSinglePayload(msgItem.payload).milvusTopK || 'No retrieval data.' }}</pre>
                            </el-collapse-item>
                            <el-collapse-item title="3. LLM Response Output" name="3">
                              <pre class="trace-box pre-wrap">{{ parseSinglePayload(msgItem.payload).response || 'No response content.' }}</pre>
                            </el-collapse-item>
                            <el-collapse-item title="4. Generated SQL Code" name="4" v-if="parseSinglePayload(msgItem.payload).generatedSql">
                              <pre class="trace-box sql-font"><code>{{ parseSinglePayload(msgItem.payload).generatedSql }}</code></pre>
                            </el-collapse-item>
                            <el-collapse-item title="5. System Error Output" name="5" v-if="parseSinglePayload(msgItem.payload).error">
                              <pre class="trace-box error-text">{{ parseSinglePayload(msgItem.payload).error }}</pre>
                            </el-collapse-item>
                          </template>
                        </el-collapse>
                      </div>
                    </div>

                  </div>
                </div>
              </div>

              <!-- HITL Actions (Only if status is Pending Approval (2)) -->
              <div v-if="selectedMessage.status === 2 && activeFilter !== 'sent'" class="approval-actions-box">
                <h4>{{ $t('notification.hitlTitle') }}</h4>
                <el-input
                  v-model="opinion"
                  :placeholder="$t('notification.opinionPlaceholder')"
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
                    {{ $t('notification.approve') }}
                  </el-button>
                  <el-button
                    type="danger"
                    @click="submitApproval('DENY')"
                    :loading="submittingAction"
                    class="btn-deny"
                  >
                    <X :size="14" />
                    {{ $t('notification.reject') }}
                  </el-button>
                </div>
              </div>

              <!-- Quick Reply Trigger Button -->
              <div v-else-if="canReplySelected" class="reply-trigger-box">
                <el-button
                  type="primary"
                  class="btn-reply-trigger"
                  @click="openReplyDialog"
                >
                  <Reply :size="14" />
                  <span>{{ $t('notification.replyBtn') }}</span>
                </el-button>
              </div>

            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- Send Message Dialog -->
    <el-dialog
      v-model="sendDialogVisible"
      :title="$t('notification.composeMessage')"
      width="820px"
      class="custom-dialog"
      :before-close="closeSendDialog"
    >
      <div class="compose-split-layout">
        <!-- Left: Recipient Selection -->
        <div class="compose-left-pane">
          <h4 class="pane-title">{{ $t('notification.selectRecipients') }}</h4>
          <div class="recipient-groups-list">
            <div v-for="(group, deptName) in groupedUsers" :key="deptName" class="dept-group-item">
              <div class="dept-group-header">
                <el-checkbox
                  v-model="group.selectedAll"
                  :indeterminate="group.isIndeterminate"
                  @change="(val) => handleDeptSelectAll(deptName, val)"
                >
                  <span class="dept-group-name">{{ deptName }}</span>
                </el-checkbox>
                <span class="dept-count-badge">{{ group.users.length }}</span>
              </div>
              <div class="dept-group-members">
                <el-checkbox
                  v-for="user in group.users"
                  :key="user.id"
                  v-model="user.selected"
                  @change="handleUserSelectChange(deptName)"
                  class="member-checkbox"
                >
                  <div class="member-checkbox-label">
                    <span class="member-realname">{{ user.realName || user.username }}</span>
                    <span class="member-username">@{{ user.username }}</span>
                  </div>
                </el-checkbox>
              </div>
            </div>
          </div>
        </div>

        <!-- Right: Message Form -->
        <div class="compose-right-pane">
          <el-form :model="sendForm" :rules="sendRules" ref="sendFormRef" label-position="top">
            <el-form-item :label="$t('notification.messageType')">
              <el-tag type="primary" effect="plain" style="font-size: 13px;">Chat Message</el-tag>
            </el-form-item>
            <el-form-item :label="$t('notification.titleField')" prop="title">
              <el-input v-model="sendForm.title" :placeholder="$t('notification.titlePlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('notification.content')" prop="content">
              <el-input
                v-model="sendForm.content"
                type="textarea"
                :rows="4"
                :placeholder="$t('notification.contentPlaceholder')"
              />
            </el-form-item>
          </el-form>

          <!-- Selection Summary Badge -->
          <div class="selected-recipients-summary">
            <span>{{ $t('notification.recipient') }}: </span>
            <el-tag type="info" class="recipients-tag">
              {{ selectedReceiverIds.length }} {{ $t('notification.unread') === 'Unread' ? 'users' : '人' }}
            </el-tag>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeSendDialog">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="submitSendMessage" :loading="sendingMessage">{{ $t('common.send') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Reply Message Dialog -->
    <el-dialog
      v-model="replyDialogVisible"
      :title="$t('notification.replyTitle')"
      width="480px"
      class="custom-dialog"
      :before-close="closeReplyDialog"
    >
      <div class="reply-dialog-body" style="padding: 10px 0;">
        <el-form label-position="top">
          <el-form-item :label="$t('notification.replyTo')">
            <el-input :value="selectedMessage ? (selectedMessage.senderRealName || selectedMessage.senderName) : ''" disabled />
          </el-form-item>
          <el-form-item :label="$t('notification.content')">
            <el-input
              v-model="replyContent"
              :placeholder="$t('notification.replyPlaceholder')"
              type="textarea"
              :rows="4"
              class="reply-textarea"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeReplyDialog">{{ $t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            :loading="sendingReply"
            :disabled="!replyContent.trim()"
            class="confirm-btn"
            @click="submitReply"
          >
            {{ $t('common.submit') }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@stores/modules/user'
import {
  getNotifications,
  markAsRead,
  handleAction,
  sendNotification,
  getUsers,
  getThread
} from '@/api/notification'
import {
  Plus, RefreshCw, Inbox, Mail, Send,
  Clock, Check, X, BookOpen, Database, Bug, Reply
} from 'lucide-vue-next'

const { t } = useI18n()
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
  notifyType: 'CHAT',
  title: '',
  content: '',
  payload: ''
})

const sendRules = {
  title: [{ required: true, message: t('notification.titleRequired'), trigger: 'blur' }],
  content: [{ required: true, message: t('notification.contentRequired'), trigger: 'blur' }]
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
    ElMessage.error(t('notification.loadError') + e.message)
  } finally {
    loading.value = false
  }
}

const threadMessages = ref<any[]>([])
const loadingThread = ref(false)
const replyContent = ref('')
const sendingReply = ref(false)

const canReplySelected = computed(() => {
  const msg = selectedMessage.value
  if (!msg) return false
  if (msg.senderId === 0) return false
  if (msg.status === 2 && activeFilter.value !== 'sent') return false
  return true
})

const fetchThread = async (msg: any) => {
  if (msg.threadId) {
    loadingThread.value = true
    try {
      const res: any = await getThread(msg.threadId)
      threadMessages.value = res || []
    } catch (e: any) {
      console.error('Failed to load thread:', e)
      threadMessages.value = [msg]
    } finally {
      loadingThread.value = false
    }
  } else {
    threadMessages.value = [msg]
  }
}

const parseSinglePayload = (payloadStr: string) => {
  if (!payloadStr) return null
  try {
    return JSON.parse(payloadStr)
  } catch (e) {
    return null
  }
}

const formatOutputPreview = (outputStr: string) => {
  try {
    return JSON.stringify(JSON.parse(outputStr), null, 2)
  } catch {
    return outputStr
  }
}

const replyDialogVisible = ref(false)

const openReplyDialog = () => {
  replyContent.value = ''
  replyDialogVisible.value = true
}

const closeReplyDialog = () => {
  replyContent.value = ''
  replyDialogVisible.value = false
}

const submitReply = async () => {
  if (!selectedMessage.value || !replyContent.value.trim()) return
  sendingReply.value = true
  try {
    const parentMsg = selectedMessage.value
    const receiverId = parentMsg.senderName === userStore.userInfo?.username ? parentMsg.receiverId : parentMsg.senderId

    await sendNotification({
      receiverId,
      title: parentMsg.title.startsWith('Re:') ? parentMsg.title : `Re: ${parentMsg.title}`,
      content: replyContent.value.trim(),
      notifyType: parentMsg.notifyType,
      parentId: parentMsg.id
    })

    ElMessage.success(t('notification.replySuccess'))
    closeReplyDialog()
    
    if (parentMsg.threadId) {
      await fetchThread(parentMsg)
    } else {
      parentMsg.threadId = parentMsg.id
      await fetchThread(parentMsg)
      fetchData()
    }
  } catch (e: any) {
    ElMessage.error(t('notification.sendError') + e.message)
  } finally {
    sendingReply.value = false
  }
}

const selectMessage = async (msg: any) => {
  selectedMessage.value = msg
  opinion.value = ''
  replyContent.value = ''

  await fetchThread(msg)

  if (msg.status === 0 && activeFilter.value !== 'sent') {
    try {
      await markAsRead(msg.id)
      msg.status = 1
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
    ElMessage.success(t('notification.decisionSubmitted', { action: action === 'APPROVE' ? t('notification.approved') : t('notification.rejected') }))

    // update status locally
    selectedMessage.value.status = action === 'APPROVE' ? 3 : 4
    if (opinion.value) {
      selectedMessage.value.content += ` (Approval Opinion: ${opinion.value})`
    }

    // refresh unread count / pending badges
    fetchData()
  } catch (e: any) {
    ElMessage.error(t('notification.submitError') + e.message)
  } finally {
    submittingAction.value = false
  }
}

const groupedUsers = ref<Record<string, {
  selectedAll: boolean
  isIndeterminate: boolean
  users: Array<{
    id: number
    username: string
    realName: string
    selected: boolean
  }>
}>>({})

const buildGroupedUsers = () => {
  const groups: Record<string, any> = {}
  userList.value.forEach((u: any) => {
    const deptName = u.deptName || 'Unassigned'
    if (!groups[deptName]) {
      groups[deptName] = {
        selectedAll: false,
        isIndeterminate: false,
        users: []
      }
    }
    groups[deptName].users.push({
      id: u.id,
      username: u.username,
      realName: u.realName,
      selected: false
    })
  })
  groupedUsers.value = groups
}

const handleDeptSelectAll = (deptName: string, checked: boolean) => {
  const group = groupedUsers.value[deptName]
  if (!group) return
  group.users.forEach((user) => {
    user.selected = checked
  })
  group.isIndeterminate = false
}

const handleUserSelectChange = (deptName: string) => {
  const group = groupedUsers.value[deptName]
  if (!group) return
  const checkedCount = group.users.filter(u => u.selected).length
  const totalCount = group.users.length

  group.selectedAll = checkedCount === totalCount
  group.isIndeterminate = checkedCount > 0 && checkedCount < totalCount
}

const selectedReceiverIds = computed(() => {
  const ids: number[] = []
  Object.values(groupedUsers.value).forEach((group) => {
    group.users.forEach((user) => {
      if (user.selected) {
        ids.push(user.id)
      }
    })
  })
  return ids
})

// Compose Dialog Actions
const openSendDialog = async () => {
  sendDialogVisible.value = true
  sendForm.value = {
    notifyType: 'CHAT',
    title: '',
    content: '',
    payload: ''
  }
  groupedUsers.value = {}

  try {
    const users: any = await getUsers()
    userList.value = (users || []).filter((u: any) => u.username !== userStore.userInfo?.username)
    buildGroupedUsers()
  } catch (e: any) {
    ElMessage.error(t('notification.loadUserError') + e.message)
  }
}

const closeSendDialog = () => {
  sendDialogVisible.value = false
  if (sendFormRef.value) {
    sendFormRef.value.resetFields()
  }
}

const submitSendMessage = () => {
  if (selectedReceiverIds.value.length === 0) {
    ElMessage.warning(t('notification.selectRecipient'))
    return
  }
  if (!sendFormRef.value) return
  sendFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      sendingMessage.value = true
      try {
        const sendPromises = selectedReceiverIds.value.map(receiverId => 
          sendNotification({
            receiverId,
            title: sendForm.value.title,
            content: sendForm.value.content,
            notifyType: sendForm.value.notifyType,
            payload: sendForm.value.payload || undefined
          })
        )
        await Promise.all(sendPromises)

        ElMessage.success(t('notification.sendSuccess'))
        closeSendDialog()
        fetchData()
      } catch (e: any) {
        ElMessage.error(t('notification.sendError') + e.message)
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
  const statusKeys = ['notification.unread', 'notification.read', 'notification.pendingApproval', 'notification.approved', 'notification.denied']
  return t(statusKeys[status]) || t('notification.unknown')
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
  font-family: 'Inter', 'Noto Sans SC', sans-serif;
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
.type-badge.meeting { background: #dcfce7; color: #166534; }
.type-badge.support_ticket { background: #e0f2fe; color: #0369a1; }

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
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
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

.user-capsule-premium {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 2px 10px;
  border-radius: 99px;
  font-size: 12.5px;
  line-height: 1.2;
  max-width: 100%;
  box-sizing: border-box;
}

.user-capsule-premium .capsule-name {
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  max-width: 150px;
}

.user-capsule-premium.sender .capsule-name {
  color: #10b981;
}

.user-capsule-premium.receiver .capsule-name {
  color: #1e293b;
}

.user-capsule-premium .capsule-handle {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 400;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  max-width: 120px;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;
  overflow: hidden;
}

.message-text {
  font-size: 14.5px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* Thread Timeline & Reply Box Styles */
.thread-timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 480px;
  overflow-y: auto;
  padding-right: 6px;
  margin-bottom: 16px;
}

.timeline-bubble-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 85%;
  align-self: flex-start;
}

.timeline-bubble-item.sent-by-me {
  align-self: flex-end;
  align-items: flex-end;
}

.bubble-header {
  display: flex;
  gap: 8px;
  font-size: 11.5px;
  color: #94a3b8;
  margin-bottom: 4px;
  padding: 0 4px;
}

.bubble-sender-name {
  font-weight: 600;
}

.bubble-content-wrap {
  background: #f1f5f9;
  border-radius: 12px;
  border-top-left-radius: 4px;
  padding: 10px 14px;
  border: 1px solid #e2e8f0;
}

.sent-by-me .bubble-content-wrap {
  background: #f0fdf4;
  border-top-left-radius: 12px;
  border-top-right-radius: 4px;
  border-color: #dcfce7;
}

.bubble-text {
  font-size: 13.5px;
  color: #1e293b;
  margin: 0;
  line-height: 1.5;
  white-space: pre-wrap;
}

.sent-by-me .bubble-text {
  color: #14532d;
}

.reply-editor-box {
  border-top: 1px solid #f1f5f9;
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.reply-textarea :deep(.el-textarea__inner) {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-family: inherit;
  font-size: 13.5px;
  box-shadow: none !important;
  transition: all 0.15s;
}

.reply-textarea :deep(.el-textarea__inner:hover) {
  border-color: #cbd5e1;
  background: #fff;
}

.reply-textarea :deep(.el-textarea__inner:focus) {
  border-color: #1e293b;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(30, 41, 59, 0.08) !important;
}

.reply-actions-row {
  display: flex;
  justify-content: flex-end;
}

.btn-send-reply {
  background: #1e293b;
  border-color: #1e293b;
  border-radius: 8px;
  font-size: 13px;
  padding: 8px 16px;
  font-weight: 500;
}

.btn-send-reply:hover {
  background: #334155 !important;
  border-color: #334155 !important;
}

.reply-trigger-box {
  border-top: 1px solid #f1f5f9;
  padding-top: 16px;
  display: flex;
  justify-content: flex-start;
}

.btn-reply-trigger {
  background: #1e293b;
  border-color: #1e293b;
  border-radius: 8px;
  font-size: 13px;
  padding: 8px 18px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-reply-trigger:hover {
  background: #334155 !important;
  border-color: #334155 !important;
}

/* Special bubble colors for Support Tickets & Bug Reports */
.timeline-bubble-item.ticket-bubble:not(.sent-by-me) .bubble-content-wrap {
  background: #f0f9ff;
  border-color: #e0f2fe;
}

.timeline-bubble-item.ticket-bubble:not(.sent-by-me) .bubble-text {
  color: #0369a1;
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

/* Task Report Meta Rows */
.trace-meta-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 13px;
}
.trace-meta-label {
  color: #64748b;
  width: 80px;
  flex-shrink: 0;
}
.trace-meta-value {
  color: #1e293b;
  font-weight: 500;
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

/* Compose Message Split Dialog Styles */
.compose-split-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  min-height: 380px;
}

.compose-left-pane {
  border-right: 1px solid #f1f5f9;
  padding-right: 20px;
  display: flex;
  flex-direction: column;
  height: 400px;
  overflow: hidden;
}

.pane-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 12px 0;
}

.recipient-groups-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 6px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dept-group-item {
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
}

.dept-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 6px;
}

.dept-group-name {
  font-weight: 600;
  font-size: 13px;
  color: #334155;
}

.dept-count-badge {
  background: #cbd5e1;
  color: #475569;
  font-size: 10.5px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 10px;
}

.dept-group-members {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 6px;
}

.member-checkbox {
  margin-right: 0 !important;
  height: auto !important;
  display: flex;
  align-items: center;
}

.member-checkbox-label {
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 1.2;
}

.member-realname {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
}

.member-username {
  font-size: 11px;
  color: #94a3b8;
}

.compose-right-pane {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.selected-recipients-summary {
  margin-top: auto;
  background: #f1f5f9;
  padding: 10px 14px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #475569;
}

.recipients-tag {
  font-weight: 600;
  font-size: 12px;
}
</style>
