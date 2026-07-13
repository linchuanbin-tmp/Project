<template>
  <!-- Persistent auth background — never re-renders on route change -->
  <div class="auth-bg">
    <div class="auth-bg__orb auth-bg__orb--left" />
    <div class="auth-bg__orb auth-bg__orb--right" />

    <router-view v-slot="{ Component, route }">
      <transition :name="transitionName" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const transitionName = ref('slide-left')

// login → register: slide left; register → login: slide right
const ORDER: Record<string, number> = { '/login': 0, '/register': 1 }
let prevOrder = ORDER[route.path] ?? 0

watch(
  () => route.path,
  (next) => {
    const nextOrder = ORDER[next] ?? 0
    transitionName.value = nextOrder > prevOrder ? 'slide-left' : 'slide-right'
    prevOrder = nextOrder
  }
)
</script>

<style scoped>
.auth-bg {
  height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 10vh;
  background: #f0f2f5;
  overflow: hidden;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Subtle ambient orbs (replaces the per-page radial gradients) */
.auth-bg__orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.auth-bg__orb--left {
  width: 600px;
  height: 600px;
  left: -160px;
  top: 50%;
  transform: translateY(-50%);
  background: radial-gradient(circle, rgba(17,24,39,0.05) 0%, transparent 70%);
}
.auth-bg__orb--right {
  width: 500px;
  height: 500px;
  right: -120px;
  top: 10%;
  background: radial-gradient(circle, rgba(99,102,241,0.07) 0%, transparent 70%);
}

/* ── Slide-left (login → register) ── */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-left-enter-from {
  opacity: 0;
  transform: translateX(40px) scale(0.97);
}
.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-40px) scale(0.97);
}

/* ── Slide-right (register → login) ── */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from {
  opacity: 0;
  transform: translateX(-40px) scale(0.97);
}
.slide-right-leave-to {
  opacity: 0;
  transform: translateX(40px) scale(0.97);
}
</style>
