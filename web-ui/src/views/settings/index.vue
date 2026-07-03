<template>
  <div class="settings-page">
    <!-- Header — 与其他页面保持一致 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('settings.title') }}</h1>
        <p class="page-sub">{{ $t('settings.subtitle') }}</p>
      </div>
    </div>

    <!-- Settings item list -->
    <div class="settings-list">

      <!-- Profile item -->
      <div class="settings-item">
        <div class="item-left">
          <div class="item-icon-wrap">
            <User :size="17" :stroke-width="1.7" />
          </div>
          <div class="item-text">
            <span class="item-title">{{ $t('settings.profile') }}</span>
            <span class="item-desc">{{ $t('settings.profileDesc') }}</span>
          </div>
        </div>
        <button class="edit-btn" @click="openProfileDialog">{{ $t('common.edit') }}</button>
      </div>

      <!-- Security item -->
      <div class="settings-item">
        <div class="item-left">
          <div class="item-icon-wrap">
            <Lock :size="17" :stroke-width="1.7" />
          </div>
          <div class="item-text">
            <span class="item-title">{{ $t('settings.security') }}</span>
            <span class="item-desc">{{ $t('settings.securityDesc') }}</span>
          </div>
        </div>
        <button class="edit-btn" @click="openPasswordDialog">{{ $t('common.edit') }}</button>
      </div>

    </div>

    <!-- ── Profile Dialog ──────────────────────────── -->
    <el-dialog
      v-model="profileDialogVisible"
      :title="$t('settings.profile')"
      width="440px"
      :close-on-click-modal="false"
      class="settings-dialog"
    >
      <!-- Avatar row -->
      <div class="dialog-avatar-row">
        <div class="dialog-avatar">{{ avatarLetter }}</div>
        <div class="dialog-user-info">
          <p class="dialog-user-name">{{ normalizeRealName(userStore.userInfo?.realName) || userStore.userInfo?.username }}</p>
          <p class="dialog-user-role">{{ isAdmin ? $t('settings.administrator') : $t('settings.employee') }}</p>
        </div>
      </div>

      <el-divider class="dialog-divider" />

      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        label-position="top"
        class="dialog-form"
      >
        <el-form-item :label="$t('settings.usernameEmail')">
          <el-input :value="userStore.userInfo?.username" disabled class="dialog-input">
            <template #prefix><Lock :size="13" :stroke-width="1.7" class="input-icon" /></template>
          </el-input>
          <p class="field-hint">{{ $t('settings.usernameHint') }}</p>
        </el-form-item>

        <el-form-item :label="$t('settings.role')">
          <el-input :value="isAdmin ? $t('settings.administrator') : $t('settings.employee')" disabled class="dialog-input">
            <template #prefix><Shield :size="13" :stroke-width="1.7" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item :label="$t('settings.displayName')" prop="realName">
          <el-input
            v-model="profileForm.realName"
            :placeholder="$t('settings.displayNamePlaceholder')"
            class="dialog-input"
            maxlength="50"
            show-word-limit
          >
            <template #prefix><User :size="13" :stroke-width="1.7" class="input-icon" /></template>
          </el-input>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="cancel-btn" @click="profileDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="profileLoading" class="confirm-btn" @click="handleSaveProfile">
            {{ $t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- ── Password Dialog ────────────────────────── -->
    <el-dialog
      v-model="passwordDialogVisible"
      :title="$t('settings.changePassword')"
      width="440px"
      :close-on-click-modal="false"
      class="settings-dialog"
      @close="resetPasswordForm"
    >
      <!-- Security banner -->
      <div class="security-banner">
        <ShieldCheck :size="16" :stroke-width="1.7" class="banner-icon" />
        <p class="banner-text">{{ $t('settings.passwordHint') }}</p>
      </div>

      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        class="dialog-form"
      >
        <el-form-item :label="$t('settings.oldPassword')" prop="currentPassword">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password
            :placeholder="$t('settings.oldPasswordPlaceholder')" class="dialog-input">
            <template #prefix><Lock :size="13" :stroke-width="1.7" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item :label="$t('settings.newPassword')" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password
            :placeholder="$t('settings.newPasswordPlaceholder')" class="dialog-input">
            <template #prefix><Key :size="13" :stroke-width="1.7" class="input-icon" /></template>
          </el-input>
          <!-- Strength meter -->
          <div class="strength-meter">
            <div v-for="n in 4" :key="n" class="strength-bar" :class="getStrengthClass(n)" />
            <span class="strength-label">{{ strengthLabel }}</span>
          </div>
        </el-form-item>

        <el-form-item :label="$t('settings.confirmPassword')" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password
            :placeholder="$t('settings.confirmPasswordPlaceholder')" class="dialog-input">
            <template #prefix><Key :size="13" :stroke-width="1.7" class="input-icon" /></template>
          </el-input>
          <p v-if="passwordForm.confirmPassword" class="match-hint"
            :class="passwordsMatch ? 'match-ok' : 'match-err'">
            <component :is="passwordsMatch ? Check : X" :size="12" />
            {{ passwordsMatch ? $t('settings.passwordsMatch') : $t('settings.passwordsMismatch') }}
          </p>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="cancel-btn" @click="passwordDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="passwordLoading" class="confirm-btn" @click="handleChangePassword">
            {{ $t('settings.updatePassword') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, Shield, Key, ShieldCheck, Check, X } from 'lucide-vue-next'
import request from '@utils/request'

const { t } = useI18n()
const userStore = useUserStore()

// Normalize legacy Chinese realName values stored in old databases
const normalizeRealName = (name: string | undefined | null): string => {
  if (!name) return ''
  const legacyMap: Record<string, string> = {
    '管理员': 'Admin',
    '普通用户': 'User',
  }
  return legacyMap[name] ?? name
}

const isAdmin = computed(() => userStore.userInfo?.roles?.includes('ROLE_ADMIN'))
const avatarLetter = computed(() => {
  const name = normalizeRealName(userStore.userInfo?.realName) || userStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

// ── Profile Dialog ────────────────────────────────
const profileDialogVisible = ref(false)
const profileFormRef = ref<FormInstance>()
const profileLoading = ref(false)
const profileForm = reactive({ realName: '' })
const profileRules: FormRules = {
  realName: [
    { required: true, message: t('settings.displayNameRequired'), trigger: 'blur' },
    { min: 1, max: 50, message: t('settings.displayNameLength'), trigger: 'blur' }
  ]
}

const openProfileDialog = () => {
  profileForm.realName = normalizeRealName(userStore.userInfo?.realName) || ''
  profileDialogVisible.value = true
}

const handleSaveProfile = async () => {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    profileLoading.value = true
    try {
      await request.put('/user/profile', { realName: profileForm.realName })
      if (userStore.userInfo) userStore.userInfo.realName = profileForm.realName
      ElMessage.success(t('settings.updateSuccess'))
      profileDialogVisible.value = false
    } finally {
      profileLoading.value = false
    }
  })
}

// ── Password Dialog ────────────────────────────────
const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordLoading = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const openPasswordDialog = () => {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

const passwordsMatch = computed(() => passwordForm.newPassword === passwordForm.confirmPassword)

const passwordStrength = computed(() => {
  const p = passwordForm.newPassword
  if (!p) return 0
  let score = 0
  if (p.length >= 6) score++
  if (p.length >= 10) score++
  if (/[A-Z]/.test(p) && /[0-9]/.test(p)) score++
  if (/[^a-zA-Z0-9]/.test(p)) score++
  return score
})
const strengthLabel = computed(() => ['', t('settings.passwordWeak'), t('settings.passwordFair'), t('settings.passwordGood'), t('settings.passwordStrong')][passwordStrength.value] || '')
const getStrengthClass = (n: number) => {
  const s = passwordStrength.value
  if (n > s) return ''
  if (s === 1) return 'bar-weak'
  if (s === 2) return 'bar-fair'
  if (s === 3) return 'bar-good'
  return 'bar-strong'
}

const passwordRules: FormRules = {
  currentPassword: [{ required: true, message: t('settings.oldPasswordRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('settings.newPasswordRequired'), trigger: 'blur' },
    { min: 6, message: t('settings.passwordMinLength'), trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, message: t('settings.confirmPasswordRequired'), trigger: 'blur' }]
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  if (!passwordsMatch.value) { ElMessage.error(t('settings.passwordsMismatch')); return }
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordLoading.value = true
    try {
      await request.put('/user/password', {
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
        confirmPassword: passwordForm.confirmPassword
      })
      ElMessage.success(t('settings.passwordUpdateSuccess'))
      passwordDialogVisible.value = false
      setTimeout(() => userStore.logout(), 1500)
    } finally {
      passwordLoading.value = false
    }
  })
}

const resetPasswordForm = () => {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.resetFields()
}
</script>

<style scoped>
/* ── Header (与其他页面完全一致) ─────────────────── */
.settings-page {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding: 16px 0;
}
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

/* ── Settings list card ──────────────────────────── */
.settings-list {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  overflow: hidden;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid #f5f5f7;
}

.settings-item:last-child {
  border-bottom: none;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.item-icon-wrap {
  width: 36px;
  height: 36px;
  background: #f5f5f7;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #374151;
  flex-shrink: 0;
}

.item-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-title {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
}

.item-desc {
  font-size: 12.5px;
  color: #9ca3af;
}

.edit-btn {
  flex-shrink: 0;
  height: 32px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  font-family: inherit;
}

.edit-btn:hover {
  background: #f5f5f7;
  border-color: #d1d5db;
}

/* ── Dialog form ─────────────────────────────────── */
.dialog-avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 4px;
}

.dialog-avatar {
  width: 48px;
  height: 48px;
  background: #111827;
  border-radius: 12px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.dialog-user-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 2px;
}

.dialog-user-role {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

.dialog-divider {
  margin: 16px 0 !important;
}

.dialog-form {
  text-align: left;
}

:deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  padding-bottom: 5px;
  line-height: 1.4;
  text-align: left;
}

.dialog-input :deep(.el-input__wrapper) {
  border-radius: 9px;
  box-shadow: 0 0 0 1px #e5e7eb;
  height: 38px;
}

.dialog-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #d1d5db;
}

.dialog-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #111827 !important;
}

.dialog-input :deep(.el-input__inner) {
  font-size: 13.5px;
  color: #111827;
}

.dialog-input :deep(.el-input__wrapper.is-disabled) {
  background: #f9fafb;
}

.input-icon {
  color: #9ca3af;
  margin-right: 3px;
}

.field-hint {
  font-size: 12px;
  color: #9ca3af;
  margin: 5px 0 0;
  text-align: left;
}

/* Security banner */
.security-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f8faff;
  border: 1px solid #e0e7ff;
  border-radius: 9px;
  padding: 11px 14px;
  margin-bottom: 20px;
}

.banner-icon {
  color: #6366f1;
  flex-shrink: 0;
}

.banner-text {
  font-size: 12.5px;
  color: #6366f1;
  margin: 0;
  line-height: 1.5;
}

/* Strength meter */
.strength-meter {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
}

.strength-bar {
  flex: 1;
  height: 3px;
  border-radius: 2px;
  background: #e5e7eb;
  transition: background 0.2s;
}

.bar-weak   { background: #ef4444; }
.bar-fair   { background: #f59e0b; }
.bar-good   { background: #3b82f6; }
.bar-strong { background: #10b981; }

.strength-label {
  font-size: 11.5px;
  color: #9ca3af;
  min-width: 40px;
  text-align: right;
}

/* Match hint */
.match-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  margin: 6px 0 0;
}

.match-ok  { color: #10b981; }
.match-err { color: #ef4444; }

/* Dialog footer */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cancel-btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #6b7280;
  border-color: #e5e7eb;
}

.cancel-btn:hover {
  color: #374151;
  border-color: #d1d5db;
  background: #f9fafb;
}

.confirm-btn {
  height: 36px;
  padding: 0 20px;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 500;
  background: #111827;
  border-color: #111827;
}

.confirm-btn:hover {
  background: #1f2937 !important;
  border-color: #1f2937 !important;
}
</style>

<!-- Dialog global style overrides -->
<style>
.settings-dialog .el-dialog {
  border-radius: 16px !important;
}

.settings-dialog .el-dialog__header {
  padding: 20px 24px 0 !important;
}

.settings-dialog .el-dialog__title {
  font-size: 16px !important;
  font-weight: 600 !important;
  color: #111827 !important;
}

.settings-dialog .el-dialog__body {
  padding: 20px 24px !important;
}

.settings-dialog .el-dialog__footer {
  padding: 0 24px 20px !important;
  border-top: 1px solid #f0f0f0;
  padding-top: 16px !important;
}
</style>
