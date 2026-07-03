<template>
  <router-view />
</template>

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
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
  'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
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
</style>