<template>
  <Transition name="fade">
    <div v-if="visible" class="loading-overlay">
      <div class="loading-modal">
        <div class="loading-header">
          <Sparkles :size="18" class="loading-icon-sparkle" />
          <span class="loading-title">{{ title }}</span>
        </div>
        
        <div class="loading-message-container">
          <span class="loading-message-text">{{ currentThoughtMessage }}</span>
        </div>

        <div class="progress-bar-container">
          <div class="progress-bar-fill" :style="{ width: progressPercentage + '%' }"></div>
        </div>

        <div class="loading-footer">
          {{ footer }}
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { Sparkles } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    footer?: string
    steps?: string[]
  }>(),
  {
    title: 'Agent is thinking',
    footer: 'Please wait while LLM formulates the response...',
    steps: () => [
      'Analyzing request context...',
      'Matching prompt with agent profile tools...',
      'Connecting to LLM inference service...',
      'Formulating response...',
      'Applying safety checks and validations...'
    ]
  }
)

const currentThoughtMessage = ref('')
const progressPercentage = ref(0)
let thoughtInterval: any = null
let progressInterval: any = null

const startThinkingAnimation = () => {
  clearInterval(thoughtInterval)
  clearInterval(progressInterval)
  
  progressPercentage.value = 0
  currentThoughtMessage.value = props.steps[0] || ''
  
  let messageIndex = 0
  thoughtInterval = setInterval(() => {
    if (props.steps.length === 0) return
    messageIndex = (messageIndex + 1) % props.steps.length
    currentThoughtMessage.value = props.steps[messageIndex]
  }, 2000)

  progressInterval = setInterval(() => {
    if (progressPercentage.value < 92) {
      const diff = Math.max(1, Math.floor((95 - progressPercentage.value) / 10))
      progressPercentage.value += diff
    }
  }, 120)
}

const stopThinkingAnimation = () => {
  clearInterval(thoughtInterval)
  clearInterval(progressInterval)
  progressPercentage.value = 100
}

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      startThinkingAnimation()
    } else {
      stopThinkingAnimation()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  clearInterval(thoughtInterval)
  clearInterval(progressInterval)
})
</script>

<style scoped>
/* ── Minimalist Loading Overlay ── */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(15, 23, 42, 0.3); /* Soft semi-transparent background */
  backdrop-filter: blur(5px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
}

.loading-modal {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  width: 400px;
  padding: 28px 24px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.05), 0 10px 10px -5px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  align-items: flex-start; /* Left-aligned */
  text-align: left; /* Left-aligned */
}

.loading-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  align-self: stretch;
}

.loading-icon-sparkle {
  color: #6366f1;
  animation: loading-pulse 1.8s infinite ease-in-out;
  flex-shrink: 0;
}

.loading-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.loading-message-container {
  min-height: 40px;
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  align-self: stretch;
}

.loading-message-text {
  font-size: 13.5px;
  color: #475569;
  font-family: Consolas, Monaco, monospace;
  line-height: 1.5;
}

.progress-bar-container {
  width: 100%;
  height: 4px;
  background: #f1f5f9;
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 16px;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #3b82f6);
  transition: width 0.15s ease-out;
}

.loading-footer {
  font-size: 11px;
  color: #94a3b8;
  align-self: stretch;
}

/* Animations */
@keyframes loading-pulse {
  0%, 100% {
    transform: scale(1) rotate(0deg);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.15) rotate(180deg);
    opacity: 1;
  }
}

/* Transitions */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
