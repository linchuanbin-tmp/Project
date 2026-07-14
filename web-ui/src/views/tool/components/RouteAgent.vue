<template>
  <div class="map-stage-container">
    <!-- Map Container occupies 100% of stage height -->
    <MapContainer
        ref="mapRef"
        :path="routePath"
        :start-point="routeStart.length === 2 ? routeStart : [114.137, 22.283]"
        :end-point="routeEnd"
        height="580px"
    />

    <!-- Floating Apple-style Panel -->
    <div class="floating-map-panel">
      <div class="panel-form-section">
        <el-form :model="routeForm" label-position="left" label-width="45px" size="small">
          <el-form-item :label="$t('route.from')" class="compact-form-item">
            <el-input v-model="routeForm.from" :placeholder="$t('route.fromPlaceholder')" clearable />
          </el-form-item>
          <el-form-item :label="$t('route.to')" class="compact-form-item">
            <el-input v-model="routeForm.to" :placeholder="$t('route.toPlaceholder')" clearable />
          </el-form-item>

          <div class="mode-select-row">
            <el-select v-model="routeForm.mode" class="mode-select" :placeholder="$t('route.mode')" size="default">
              <el-option :label="$t('route.driving')" value="driving" />
              <el-option :label="$t('route.transit')" value="transit" />
              <el-option :label="$t('route.walking')" value="walking" />
            </el-select>

            <el-button
                type="primary"
                class="route-submit-btn"
                @click="planRoute"
                :disabled="loading"
            >
              {{ $t('route.planBtn') }}
            </el-button>
          </div>
        </el-form>
      </div>

    <!-- Results panel -->
    <div v-if="routePath.length > 0" class="panel-results-section">
      <el-divider class="panel-divider" />
      
      <div>
        <!-- Real Results Section -->
        <div>
          <div class="compact-stats">
            <div class="compact-stat-item">
              <span class="stat-lbl">Time</span>
              <span class="stat-val duration-val">{{ routeResult?.duration || '35 mins' }}</span>
            </div>
            <div class="compact-stat-item">
              <span class="stat-lbl">Distance</span>
              <span class="stat-val">{{ routeResult?.distance || '12.5km' }}</span>
            </div>
            <div class="compact-stat-item" v-if="routeForm.mode === 'driving' && routeResult?.toll">
              <span class="stat-lbl">Toll</span>
              <span class="stat-val">{{ routeResult.toll }}</span>
            </div>
            <div class="compact-stat-item" v-if="routeForm.mode === 'transit' && routeResult?.cost">
              <span class="stat-lbl">Fare</span>
              <span class="stat-val">{{ routeResult.cost }}</span>
            </div>
          </div>

          <div class="traffic-bar-badge" :class="getTrafficClass(routeResult?.trafficStatus, routeForm.mode)">
            <span class="traffic-dot"></span>
            {{ getTrafficLabel(routeForm.mode, routeResult?.trafficStatus) }}
          </div>

          <!-- Scrollable directions steps -->
          <div class="panel-directions-list" v-if="routeResult?.steps && routeResult.steps.length > 0">
            <h5 class="directions-subtitle">Directions</h5>
            <div class="steps-scroll-area">
              <div class="compact-step-item clickable-step" v-for="(step, index) in routeResult.steps" :key="index" @click="onStepClick(step)">
                <div class="step-num">{{ index + 1 }}</div>
                <div class="step-desc-wrap">
                  <p class="step-desc">{{ step.instruction }}</p>
                  <span class="step-dist" v-if="step.distance && step.distance !== '0米' && step.distance !== '0'">
                    {{ step.distance }} <span v-if="step.duration">({{ step.duration }})</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
    
    <!-- Premium Agent Thinking Modal for Route Planning -->
    <AgentThinking
      :visible="loading"
      :title="$t('route.planning')"
      footer="Please wait while Route Agent queries routing engine..."
      :steps="routeThoughtSteps"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import MapContainer from '@components/MapContainer.vue'
import AgentThinking from '@components/AgentThinking.vue'
import { planRoute as planRouteApi } from '@api/tool'

const { t } = useI18n()

const loading = ref(false)
const routePath = ref<number[][]>([])
const routeStart = ref<number[]>([])
const routeEnd = ref<number[]>([])
const routeResult = ref<any>(null)
const mapRef = ref<any>(null)

const getStepCoordinate = (step: any): number[] | null => {
  if (step.polyline) {
    const points = step.polyline.split(';')
    if (points.length > 0) {
      const parts = points[0].split(',')
      if (parts.length === 2) {
        return [parseFloat(parts[0]), parseFloat(parts[1])]
      }
    }
  }
  if (step.location) {
    if (typeof step.location === 'string') {
      const parts = step.location.split(',')
      if (parts.length === 2) {
        return [parseFloat(parts[0]), parseFloat(parts[1])]
      }
    } else if (Array.isArray(step.location) && step.location.length === 2) {
      return [parseFloat(step.location[0]), parseFloat(step.location[1])]
    }
  }
  return null
}

const onStepClick = (step: any) => {
  const coord = getStepCoordinate(step)
  if (coord) {
    mapRef.value?.focusOnCoordinate(coord, step.polyline)
  }
}

const routeForm = reactive({
  from: 'HKU',
  to: 'Hong Kong International Airport Terminal 1',
  mode: 'driving'
})

// Specific thought messages for Route Agent
const routeThoughtSteps = [
  'Resolving origin and destination coordinates...',
  'Querying real-time traffic conditions...',
  'Calculating optimal path via routing engine...',
  'Generating step-by-step navigation instructions...',
  'Rendering coordinates path onto Map stage...'
]





const getTrafficClass = (status: string, mode: string) => {
  if (mode === 'walking') return 'traffic-walking'
  if (mode === 'transit') return 'traffic-transit'
  if (!status) return 'traffic-clear'
  const s = status.toLowerCase()
  if (s.includes('畅通') || s.includes('clear') || s.includes('smooth')) return 'traffic-clear'
  if (s.includes('缓行') || s.includes('slow') || s.includes('heavy') || s.includes('congested-light')) return 'traffic-slow'
  return 'traffic-jam'
}

const getTrafficLabel = (mode: string, status: string) => {
  if (mode === 'walking') return 'Walking'
  if (mode === 'transit') return status || 'Transit'
  return `Traffic: ${status || 'Clear'}`
}

const planRoute = async () => {
  if (!routeForm.to) {
    ElMessage.warning(t('route.enterDestination'))
    return
  }
  loading.value = true
  try {
    const res: any = await planRouteApi({
      from: routeForm.from,
      to: routeForm.to,
      mode: routeForm.mode
    })

    routeResult.value = res

    if (res.path && res.path.length > 0) {
      routePath.value = res.path
      routeStart.value = res.startPoint || []
      routeEnd.value = res.endPoint || []
    } else {
      routeStart.value = [114.137, 22.283]
      routeEnd.value = [113.915, 22.309]
      routePath.value = [
        [114.137, 22.283], [114.115, 22.315], [114.075, 22.335],
        [114.020, 22.345], [113.965, 22.298], [113.915, 22.309]
      ]
      ElMessage.info(t('route.demoRoute'))
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(t('route.planFailed'))
    routeResult.value = null
  } finally {
    loading.value = false
  }
}

// Expose interface for parent injection
const setRouteData = (data: {
  from?: string
  to?: string
  mode?: string
  path?: number[][]
  startPoint?: number[]
  endPoint?: number[]
  result?: any
}) => {
  if (data.from) routeForm.from = data.from
  if (data.to) routeForm.to = data.to
  if (data.mode) routeForm.mode = data.mode
  if (data.path) routePath.value = data.path
  if (data.startPoint) routeStart.value = data.startPoint
  if (data.endPoint) routeEnd.value = data.endPoint
  if (data.result !== undefined) routeResult.value = data.result
}

defineExpose({ setRouteData })
</script>

<style scoped>
/* Apple Maps Stage Container */
.map-stage-container {
  position: relative;
  width: 100%;
  height: 580px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
}

/* Floating Control Panel on Map */
.floating-map-panel {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 10;
  width: 320px;
  max-height: calc(100% - 32px);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px) saturate(190%);
  -webkit-backdrop-filter: blur(20px) saturate(190%);
  border-radius: 16px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.4);
  padding: 16px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.floating-map-panel:hover {
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.14);
}

/* Form Styles inside Floating Panel */
.compact-form-item {
  margin-bottom: 8px !important;
}
.compact-form-item :deep(.el-form-item__label) {
  font-size: 13px !important;
  font-weight: 600 !important;
  color: #4b5563;
  height: 32px;
  line-height: 32px;
  display: inline-flex;
  align-items: center;
}
.compact-form-item :deep(.el-input__wrapper) {
  background: rgba(0, 0, 0, 0.04) !important;
  border: none !important;
  border-radius: 8px !important;
  box-shadow: none !important;
  height: 32px;
}
.compact-form-item :deep(.el-input__wrapper.is-focus) {
  background: #ffffff !important;
  box-shadow: 0 0 0 2px #111827 !important;
}

.mode-select-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  height: 32px;
}
.mode-select {
  width: 130px;
}
.mode-select :deep(.el-input__wrapper) {
  background: rgba(0, 0, 0, 0.04) !important;
  border: none !important;
  border-radius: 10px !important;
  box-shadow: none !important;
  padding: 0 12px !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.mode-select :deep(.el-input__wrapper:hover) {
  background: rgba(0, 0, 0, 0.07) !important;
}
.mode-select :deep(.el-input__wrapper.is-focus) {
  background: #ffffff !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05) !important;
}
.mode-select :deep(.el-input__inner) {
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #1c1c1e !important;
}
.mode-select :deep(.el-select__caret) {
  color: #8e8e93 !important;
  font-size: 11px !important;
}
.route-submit-btn {
  min-width: 60px !important;
  height: 32px !important;
  border-radius: 16px !important;
  padding: 0 16px !important;
  font-weight: 600 !important;
  font-size: 12px !important;
  background-color: #007aff !important; /* iOS Blue */
  box-shadow: 0 2px 8px rgba(0, 122, 255, 0.2) !important;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.route-submit-btn:hover {
  background-color: #0062cc !important;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3) !important;
}

/* Results panel inside Floating Panel */
.panel-divider {
  margin: 12px 0 !important;
}
.compact-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
}
.compact-stat-item {
  display: flex;
  flex-direction: column;
}
.stat-lbl {
  font-size: 10px;
  color: #8e8e93;
  font-weight: 600;
  letter-spacing: 0.3px;
  margin-bottom: 2px;
}
.stat-val {
  font-size: 13.5px;
  font-weight: 700;
  color: #1c1c1e;
}
.duration-val {
  color: #34c759 !important; /* iOS Green for time */
  font-size: 15px;
}

.traffic-bar-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  width: fit-content;
  margin-bottom: 12px;
}
.traffic-bar-badge.traffic-clear {
  background: rgba(52, 199, 89, 0.1);
  color: #30d158;
}
.traffic-bar-badge.traffic-slow {
  background: rgba(255, 149, 0, 0.1);
  color: #ff9f0a;
}
.traffic-bar-badge.traffic-jam {
  background: rgba(255, 59, 48, 0.1);
  color: #ff453a;
}
.traffic-bar-badge.traffic-walking {
  background: rgba(52, 199, 89, 0.1);
  color: #30d158;
}
.traffic-bar-badge.traffic-transit {
  background: rgba(0, 122, 255, 0.1);
  color: #007aff;
}
.traffic-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-right: 6px;
  background-color: currentColor;
}



/* Scrollable Directions inside Floating Panel */
.panel-directions-list {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding-top: 10px;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-height: 240px;
}
.directions-subtitle {
  font-size: 11px;
  font-weight: 700;
  color: #8e8e93;
  letter-spacing: 0.4px;
  margin: 0 0 8px 0;
}
.steps-scroll-area {
  overflow-y: auto;
  flex-grow: 1;
  padding-right: 4px;
}
.steps-scroll-area::-webkit-scrollbar {
  width: 3px;
}
.steps-scroll-area::-webkit-scrollbar-track {
  background: transparent;
}
.steps-scroll-area::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.compact-step-item {
  display: flex;
  padding: 6px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
}
.clickable-step {
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.15s ease;
  border-radius: 8px;
  padding: 6px 8px !important;
  margin: 0 -8px;
}
.clickable-step:hover {
  background-color: rgba(0, 122, 255, 0.08);
}
.clickable-step:active {
  background-color: rgba(0, 122, 255, 0.15);
  transform: scale(0.98);
}
.compact-step-item:last-child {
  border-bottom: none;
}
.step-num {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.05);
  color: #8e8e93;
  font-size: 9px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
  flex-shrink: 0;
  margin-top: 2px;
}
.step-desc-wrap {
  flex-grow: 1;
}
.step-desc {
  margin: 0 0 2px 0;
  font-size: 11.5px;
  color: #1c1c1e;
  line-height: 1.35;
}
.step-dist {
  font-size: 10px;
  color: #8e8e93;
}
</style>
