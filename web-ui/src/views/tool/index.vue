<template>
  <div class="tool-container">
    <el-page-header @back="router.back()" title="工具调用 Agent" />

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 左侧：功能选择 -->
      <el-col :xs="24" :sm="24" :md="16">
        <el-tabs v-model="activeTab" type="border-card" class="tool-tabs">
          <!-- 会议室查询 -->
          <el-tab-pane label="🏢 会议室查询" name="meeting">
            <el-form :model="meetingForm" label-width="100px">
              <el-form-item label="选择日期">
                <el-date-picker
                    v-model="meetingForm.date"
                    type="date"
                    placeholder="选择日期"
                    style="width: 100%;"
                />
              </el-form-item>
              <el-form-item label="容纳人数">
                <el-input-number v-model="meetingForm.capacity" :min="1" :max="100" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="queryMeetingRooms" :loading="loading">
                  查询可用会议室
                </el-button>
              </el-form-item>
            </el-form>

            <el-divider />

            <div v-if="meetingRooms.length > 0">
              <h4>查询结果：</h4>
              <el-row :gutter="10">
                <el-col :span="12" v-for="room in meetingRooms" :key="room.id">
                  <el-card :class="{ 'room-available': room.available, 'room-occupied': !room.available }" shadow="hover">
                    <h5>{{ room.name }} ({{ room.id }})</h5>
                    <p>容量: {{ room.capacity }}人 | 位置: {{ room.location }}</p>
                    <p>设备: {{ room.equipment.join(', ') }}</p>
                    <el-tag :type="room.available ? 'success' : 'danger'">
                      {{ room.available ? '可预订' : '已占用' }}
                    </el-tag>
                  </el-card>
                </el-col>
              </el-row>
            </div>
          </el-tab-pane>

          <!-- 日程冲突检测 -->
          <el-tab-pane label="📅 日程冲突" name="schedule">
            <el-form :model="scheduleForm" label-width="120px">
              <el-form-item label="会议时间">
                <el-date-picker
                    v-model="scheduleForm.timeRange"
                    type="datetimerange"
                    range-separator="至"
                    start-placeholder="开始时间"
                    end-placeholder="结束时间"
                />
              </el-form-item>
              <el-form-item label="参会人员">
                <el-select
                    v-model="scheduleForm.attendees"
                    placeholder="选择参会人员"
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
              <el-form-item>
                <el-button type="primary" @click="checkConflict" :loading="loading">
                  检测冲突
                </el-button>
              </el-form-item>
            </el-form>

            <el-divider />

            <div v-if="conflictResult">
              <el-alert
                  :title="conflictResult.message"
                  :type="conflictResult.hasConflict ? 'warning' : 'success'"
                  :closable="false"
                  show-icon
              />
              <div v-if="conflictResult.hasConflict" style="margin-top: 10px;">
                <h4>推荐替代时段：</h4>
                <el-tag v-for="time in conflictResult.suggestedTime" :key="time" style="margin-right: 10px;">
                  {{ time }}
                </el-tag>
              </div>
            </div>
          </el-tab-pane>

          <!-- 路线规划 -->
          <el-tab-pane label="🗺️ 路线规划" name="route">
            <el-form :model="routeForm" label-width="100px">
              <el-form-item label="出发地">
                <el-input v-model="routeForm.from" placeholder="例如：公司" />
              </el-form-item>
              <el-form-item label="目的地">
                <el-input v-model="routeForm.to" placeholder="例如：机场" />
              </el-form-item>
              <el-form-item label="出行方式">
                <el-radio-group v-model="routeForm.mode">
                  <el-radio label="driving">驾车</el-radio>
                  <el-radio label="transit">公交</el-radio>
                  <el-radio label="walking">步行</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="planRoute" :loading="loading">
                  规划路线
                </el-button>
              </el-form-item>
            </el-form>

            <el-divider />

            <div v-if="routePath.length > 0">
              <MapContainer
                  :path="routePath"
                  :start-point="routeStart"
                  :end-point="routeEnd"
              />
            </div>

            <el-empty v-else description="输入出发地和目的地，点击规划路线查看地图" />
          </el-tab-pane>
        </el-tabs>
      </el-col>

      <!-- 右侧：AI 助手模式 + WebSocket 实时进度 -->
      <el-col :xs="24" :sm="24" :md="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>🤖 AI 助手模式</span>
              <el-tag v-if="isExecuting" type="warning" effect="dark">执行中</el-tag>
            </div>
          </template>

          <div class="chat-mode">
            <el-input
                v-model="naturalQuery"
                type="textarea"
                :rows="4"
                placeholder="例如：帮我找个能容纳20人的会议室，今天下午2点到4点，要有投影仪"
                :disabled="isExecuting"
            />

            <el-button
                type="primary"
                style="width: 100%; margin-top: 10px;"
                @click="executeWithWebSocket"
                :loading="isExecuting"
                :disabled="!naturalQuery.trim()"
            >
              {{ isExecuting ? '执行中...' : '发送给 AI 助手' }}
            </el-button>

            <!-- 实时进度显示 -->
            <div v-if="taskStatus" class="progress-section">
              <el-divider />
              <div class="progress-info">
                <span class="status-label">{{ taskMessage }}</span>
                <span class="progress-percent">{{ taskProgress }}%</span>
              </div>
              <el-progress
                  :percentage="taskProgress"
                  :status="taskStatus === 'completed' ? 'success' : taskStatus === 'error' ? 'exception' : ''"
                  :stroke-width="10"
                  striped
                  striped-flow
                  :duration="10"
              />
            </div>

            <!-- AI 助手模式卡片内，进度条下方 -->
            <div v-if="aiResponse" class="ai-result">
              <el-divider />
              <h4>🤖 AI 解析结果</h4>

              <!-- 意图解析 -->
              <div v-if="aiResponse.aiParsed">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="意图">{{ aiResponse.aiParsed.intent || '查询' }}</el-descriptions-item>
                  <el-descriptions-item label="日期">{{ aiResponse.aiParsed.date || '今天' }}</el-descriptions-item>
                  <el-descriptions-item label="时间段">{{ aiResponse.aiParsed.timeRange || '未指定' }}</el-descriptions-item>
                  <el-descriptions-item label="人数">{{ aiResponse.aiParsed.capacity || '未指定' }}</el-descriptions-item>
                  <el-descriptions-item label="设备需求">{{ aiResponse.aiParsed.equipment?.join(', ') || '无' }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- 推荐结果 -->
              <div v-if="aiResponse.rooms" style="margin-top: 10px;">
                <h5>推荐会议室：</h5>
                <el-card v-for="room in aiResponse.rooms" :key="room.id" class="room-card" shadow="hover">
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span><strong>{{ room.name }}</strong> ({{ room.id }})</span>
                    <el-tag :type="room.available ? 'success' : 'danger'">
                      {{ room.available ? '可预订' : '已占用' }}
                    </el-tag>
                  </div>
                  <p style="margin: 5px 0; color: #666; font-size: 12px;">
                    容量: {{ room.capacity }}人 | 位置: {{ room.location }} | 设备: {{ room.equipment?.join(', ') }}
                  </p>
                  <p v-if="room.aiMatchScore" style="margin: 0; color: #409EFF; font-size: 12px;">
                    🤖 AI匹配度: {{ room.aiMatchScore }}% - {{ room.aiReasoning }}
                  </p>
                </el-card>
              </div>

              <!-- 路线规划结果 -->
              <div v-if="aiResponse.distance">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="距离">{{ aiResponse.distance }}</el-descriptions-item>
                  <el-descriptions-item label="时间">{{ aiResponse.duration }}</el-descriptions-item>
                  <el-descriptions-item label="路况">{{ aiResponse.trafficStatus }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- 日程冲突结果 -->
              <div v-if="aiResponse.hasConflict !== undefined">
                <el-alert
                    :title="aiResponse.message"
                    :type="aiResponse.hasConflict ? 'warning' : 'success'"
                    show-icon
                />
              </div>

              <!-- 原始JSON（调试用，可折叠） -->
              <el-collapse style="margin-top: 10px;">
                <el-collapse-item title="原始响应数据">
                  <pre style="font-size: 11px; background: #f5f7fa; padding: 8px;">{{ JSON.stringify(aiResponse, null, 2) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MapContainer from '@components/MapContainer.vue'
import { getMeetingRooms, checkScheduleConflict, planRoute as planRouteApi, executeTool } from '@api/tool'
import { wsClient } from '@utils/websocket'

const router = useRouter()
const loading = ref(false)
const activeTab = ref('meeting')
const routePath = ref<number[][]>([])
const routeStart = ref<number[]>([])
const routeEnd = ref<number[]>([])

// ==================== 会议室查询 ====================
const meetingForm = reactive({
  date: '',
  capacity: 10
})
const meetingRooms = ref<any[]>([])

const queryMeetingRooms = async () => {
  loading.value = true
  try {
    const res: any = await getMeetingRooms({
      date: meetingForm.date ? new Date(meetingForm.date).toISOString().split('T')[0] : undefined,
      capacity: meetingForm.capacity
    })
    meetingRooms.value = res || []
    ElMessage.success(`找到 ${meetingRooms.value.length} 个会议室`)
  } catch (error) {
    console.error(error)
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

// ==================== 日程冲突检测 ====================
const scheduleForm = reactive({
  timeRange: [] as Date[],
  attendees: [] as string[]
})
const userOptions = [
  { label: '管理员 (admin)', value: 'admin' },
  { label: '测试用户 (user)', value: 'user' },
  { label: '张三', value: 'zhangsan' },
  { label: '李四', value: 'lisi' }
]
const conflictResult = ref<any>(null)

const checkConflict = async () => {
  if (scheduleForm.timeRange.length !== 2) {
    ElMessage.warning('请选择完整的时间范围')
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
    ElMessage.error('检测失败')
  } finally {
    loading.value = false
  }
}

// ==================== 路线规划 ====================
const routeForm = reactive({
  from: '公司',
  to: '机场',
  mode: 'driving'
})
const routeResult = ref<any>(null)

const planRoute = async () => {
  if (!routeForm.to) {
    ElMessage.warning('请输入目的地')
    return
  }
  loading.value = true
  try {
    const res: any = await planRouteApi({
      from: routeForm.from,
      to: routeForm.to,
      mode: routeForm.mode
    })

    if (res.path && res.path.length > 0) {
      routePath.value = res.path
      routeStart.value = res.startPoint || []
      routeEnd.value = res.endPoint || []
    } else {
      routeStart.value = [116.321, 39.894]
      routeEnd.value = [116.412, 39.509]
      routePath.value = [
        [116.321, 39.894], [116.35, 39.85], [116.38, 39.78],
        [116.40, 39.65], [116.41, 39.55], [116.412, 39.509]
      ]
      ElMessage.info('已显示演示路线')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('规划失败')
  } finally {
    loading.value = false
  }
}

// ==================== WebSocket AI 助手 ====================
const naturalQuery = ref('')
const aiResponse = ref<any>(null)

const taskProgress = ref(0)
const taskStatus = ref('')
const taskMessage = ref('等待任务开始...')
const isExecuting = ref(false)
let hasFetchedResult = false

const generateTaskId = () => 'task_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)

const executeWithWebSocket = async () => {
  if (!naturalQuery.value.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }

  const taskId = generateTaskId()
  isExecuting.value = true
  hasFetchedResult = false
  taskProgress.value = 0
  taskStatus.value = 'connected'
  taskMessage.value = '正在连接服务器...'
  aiResponse.value = null

  // 关闭旧连接，防止事件堆积
  wsClient.close?.()

  const wsUrl = `ws://localhost:8083/tool/ws?taskId=${taskId}`
  wsClient.connect(wsUrl)

  // 监听消息（兼容字符串和对象）
  wsClient.on('message', (rawData: any) => {
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData

    taskProgress.value = data.progress ?? 0
    taskStatus.value = data.status ?? ''
    taskMessage.value = data.message ?? ''

    if (data.status === 'completed' && !hasFetchedResult) {
      hasFetchedResult = true
      isExecuting.value = false
      ElMessage.success('任务执行完成！')
      fetchTaskResult()
    } else if (data.status === 'error') {
      isExecuting.value = false
      taskMessage.value = data.message || '执行出错'
      ElMessage.error(data.message || '执行出错')
    }
  })

  wsClient.on('error', () => {
    taskStatus.value = 'error'
    taskMessage.value = '连接发生错误'
    isExecuting.value = false
  })

  wsClient.on('open', () => {
    taskMessage.value = '已连接，正在发送任务...'
    wsClient.send(JSON.stringify({
      taskType: 'AI',
      query: naturalQuery.value,
      parameters: {}
    }))
  })
}

const fetchTaskResult = async () => {
  try {
    const res: any = await executeTool({
      toolType: 'AI',
      parameters: {},
      naturalLanguage: naturalQuery.value
    })

    // 解包后端统一返回结构 Result<T>
    const payload = res?.data ?? res
    aiResponse.value = payload
    console.log('AI原始响应:', res)
    console.log('AI解析结果:', payload)

    if (!payload) {
      ElMessage.warning('AI 返回结果为空，请检查后端 /tool/execute 接口')
      return
    }

    // 根据意图自动跳转 Tab 并回填数据
    const intentRaw = payload.aiParsed?.intent || payload.intent || ''
    const intent = intentRaw.toLowerCase()

    let targetTab = 'meeting'
    if (intent.includes('route') || intent.includes('路线') || intent.includes('导航') || intent.includes('path') || intent.includes('map')) {
      targetTab = 'route'
    } else if (intent.includes('schedule') || intent.includes('冲突') || intent.includes('日程') || intent.includes('会议时间') || intent.includes('conflict')) {
      targetTab = 'schedule'
    } else if (intent.includes('meeting') || intent.includes('会议室') || intent.includes('room') || intent.includes('预订')) {
      targetTab = 'meeting'
    }

    activeTab.value = targetTab

    if (targetTab === 'route') {
      routeForm.from = payload.from || payload.aiParsed?.from || routeForm.from || '公司'
      routeForm.to = payload.to || payload.aiParsed?.to || routeForm.to || '机场'
      routeForm.mode = payload.mode || payload.aiParsed?.mode || 'driving'

      if (payload.path && payload.path.length > 0) {
        routePath.value = payload.path
        routeStart.value = payload.startPoint || payload.path[0]
        routeEnd.value = payload.endPoint || payload.path[payload.path.length - 1]
      } else if (payload.distance || payload.duration) {
        routeStart.value = payload.startPoint || [116.321, 39.894]
        routeEnd.value = payload.endPoint || [116.412, 39.509]
        routePath.value = payload.path || [
          [116.321, 39.894], [116.35, 39.85], [116.38, 39.78],
          [116.40, 39.65], [116.41, 39.55], [116.412, 39.509]
        ]
        ElMessage.info('已显示演示路线（后端未返回具体坐标）')
      }
      routeResult.value = payload

    } else if (targetTab === 'schedule') {
      conflictResult.value = {
        hasConflict: payload.hasConflict ?? false,
        message: payload.message || '检测完成',
        suggestedTime: payload.suggestedTime || payload.aiParsed?.suggestedTime || []
      }

      const timeRange = payload.timeRange || payload.aiParsed?.timeRange
      if (Array.isArray(timeRange) && timeRange.length === 2) {
        scheduleForm.timeRange = timeRange.map((t: string) => new Date(t))
      }
      const attendees = payload.attendees || payload.aiParsed?.attendees
      if (attendees) {
        scheduleForm.attendees = attendees
      }

    } else if (targetTab === 'meeting') {
      if (payload.rooms && payload.rooms.length > 0) {
        meetingRooms.value = payload.rooms
      }
      if (payload.aiParsed?.date) {
        meetingForm.date = new Date(payload.aiParsed.date)
      }
      if (payload.aiParsed?.capacity) {
        meetingForm.capacity = Number(payload.aiParsed.capacity)
      }
    }

    ElMessage.success(`AI 识别为「${targetTab === 'route' ? '路线规划' : targetTab === 'schedule' ? '日程冲突' : '会议室查询'}」意图，已自动跳转并展示结果`)
  } catch (error) {
    console.error('fetchTaskResult 错误:', error)
    ElMessage.error('获取 AI 结果失败')
  }
}

onUnmounted(() => {
  wsClient.close?.()
})
</script>

<style scoped>
.tool-container {
  padding: 20px;
}

.tool-tabs {
  min-height: 600px;
}

.room-available {
  border: 1px solid #67C23A;
  margin-bottom: 10px;
}

.room-occupied {
  border: 1px solid #F56C6C;
  opacity: 0.7;
  margin-bottom: 10px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.chat-mode {
  display: flex;
  flex-direction: column;
}

.progress-section {
  margin-top: 15px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.status-label {
  color: #606266;
}

.progress-percent {
  font-weight: bold;
  color: #409EFF;
}

.ai-response {
  margin-top: 15px;
}

.ai-response h4 {
  margin: 0 0 10px 0;
  color: #303133;
}

.ai-response pre {
  margin: 0;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>