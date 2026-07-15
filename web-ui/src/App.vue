<template>
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { elLocaleMap } from '@/i18n'

const { locale } = useI18n()
const elLocale = computed(() => elLocaleMap[locale.value] || elLocaleMap['en'])

// 同步 document.documentElement.lang 和 localStorage
watch(locale, (val) => {
  document.documentElement.lang = val
  localStorage.setItem('lang', val)
}, { immediate: true })
</script>

<style>
:root {
  --el-color-primary: #111827 !important;
  --el-color-primary-light-3: #374151 !important;
  --el-color-primary-light-5: #4b5563 !important;
  --el-color-primary-light-7: #9ca3af !important;
  --el-color-primary-light-8: #e5e7eb !important;
  --el-color-primary-light-9: #f3f4f6 !important;
  --el-color-primary-dark-2: #030712 !important;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Inter', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ──────────────────────────────────────────────────────────────────
   Global Form & Component Styling Unification (Admin & Management)
   ────────────────────────────────────────────────────────────────── */

/* 1. Global Selects and Inputs (Height 38px, Radius 8px) */
.el-select .el-input__wrapper,
.el-input .el-input__wrapper {
  background-color: #f9fafb !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  box-shadow: none !important;
  height: 38px !important;
  box-sizing: border-box !important;
  transition: border-color 0.2s, background-color 0.2s !important;
  padding: 0 12px !important;
}

.el-select .el-input__wrapper.is-focus,
.el-input .el-input__wrapper.is-focus,
.el-select .el-input__wrapper:hover,
.el-input .el-input__wrapper:hover {
  border-color: #111827 !important;
}

.el-select .el-input__wrapper.is-focus,
.el-input .el-input__wrapper.is-focus {
  background-color: #ffffff !important;
}

.el-select .el-input__inner,
.el-input .el-input__inner {
  font-size: 13.5px !important;
  color: #1f2937 !important;
}

/* 2. Global Table Action Buttons (Height 32px, Radius 8px) */
.custom-table .el-button,
.custom-table .action-btn,
.custom-table .remove-btn {
  height: 32px !important;
  padding: 0 14px !important;
  border-radius: 8px !important;
  font-size: 12.5px !important;
  font-weight: 500 !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  box-sizing: border-box !important;
  transition: all 0.15s !important;
}

/* Neutral Action Buttons (Edit, Details, etc.) */
.custom-table .el-button:not(.el-button--danger):not(.el-button--primary),
.custom-table .action-btn:not(.delete-btn):not(.delete-btn) {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  color: #374151 !important;
}

.custom-table .el-button:not(.el-button--danger):not(.el-button--primary):hover,
.custom-table .action-btn:not(.delete-btn):hover {
  background: #f9fafb !important;
  border-color: #111827 !important;
  color: #111827 !important;
}

/* Danger Action Buttons (Delete, Remove, Cancel) */
.custom-table .el-button--danger.is-plain,
.custom-table .delete-btn,
.custom-table .remove-btn {
  background: #fff5f5 !important;
  border: 1px solid #fecaca !important;
  color: #dc2626 !important;
  box-shadow: none !important;
}

.custom-table .el-button--danger.is-plain:hover,
.custom-table .delete-btn:hover,
.custom-table .remove-btn:hover {
  background: #fef2f2 !important;
  border-color: #fca5a5 !important;
  color: #b91c1c !important;
}

/* 3. Global Dialog Action Buttons */
.dialog-btn-cancel,
.dialog-btn-confirm {
  height: 38px !important;
  border-radius: 8px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 8px 18px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

/* ──────────────────────────────────────────────────────────────────
   macOS-style Premium ElNotification Custom Styling & Animations
   ────────────────────────────────────────────────────────────────── */
.el-notification {
  border-radius: 14px !important;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
  background: rgba(255, 255, 255, 0.85) !important;
  backdrop-filter: blur(12px) saturate(180%) !important;
  -webkit-backdrop-filter: blur(12px) saturate(180%) !important;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.08) !important;
  padding: 14px 18px !important;
  width: 320px !important;
  font-family: 'Inter', 'Noto Sans SC', sans-serif !important;
}

.el-notification__title {
  font-size: 13.5px !important;
  font-weight: 600 !important;
  color: #1d1d1f !important;
  line-height: 1.4 !important;
}

.el-notification__content {
  font-size: 12.5px !important;
  color: #515154 !important;
  margin-top: 4px !important;
  line-height: 1.4 !important;
}

.el-notification__closeBtn {
  top: 14px !important;
  right: 14px !important;
  color: #86868b !important;
}

.el-notification__closeBtn:hover {
  color: #1d1d1f !important;
}



/* Custom slide-in snappier transition animation (Mac-like easing) */
.el-notification-fade-enter-from {
  transform: translateX(120%) !important;
  opacity: 0 !important;
}

.el-notification-fade-enter-active {
  transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1) !important;
}

.el-notification-fade-leave-active {
  transition: all 0.25s ease !important;
}

.el-notification-fade-leave-to {
  transform: translateX(120%) !important;
  opacity: 0 !important;
}

/* ──────────────────────────────────────────────────────────────────
   macOS-style Premium ElMessage Custom Styling & Animations
   ────────────────────────────────────────────────────────────────── */
.el-message {
  border-radius: 12px !important;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
  background: rgba(255, 255, 255, 0.85) !important;
  backdrop-filter: blur(12px) saturate(180%) !important;
  -webkit-backdrop-filter: blur(12px) saturate(180%) !important;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.08) !important;
  padding: 10px 16px !important;
  font-family: 'Inter', 'Noto Sans SC', sans-serif !important;
  min-width: unset !important;
  width: 320px !important; /* Match ElNotification width */

  /* Place on the right side instead of top-center */
  left: auto !important;
  right: 16px !important;
  transform: translateX(0) !important;
}

.el-message__content {
  font-size: 13px !important;
  font-weight: 500 !important;
  color: #1d1d1f !important;
}



/* Override default icons color and size to look native and clean */
.el-message .el-message-icon--success { color: #10b981 !important; }
.el-message .el-message-icon--error { color: #ef4444 !important; }
.el-message .el-message-icon--warning { color: #f59e0b !important; }
.el-message .el-message-icon--info { color: #3b82f6 !important; }

/* Custom slide-in from right boundary animation for ElMessage */
.el-message-fade-enter-from,
.el-message-fade-leave-to {
  opacity: 0 !important;
  transform: translateX(120%) !important;
}

.el-message-fade-enter-active {
  transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1) !important;
}

.el-message-fade-leave-active {
  transition: all 0.25s ease !important;
}
</style>