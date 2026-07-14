<template>
  <div class="settings-page">
    <!-- Header — 与其他页面保持一致 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('settings.title') }}</h1>
        <p class="page-sub">{{ $t('settings.subtitle') }}</p>
      </div>
    </div>

    <!-- Settings Sections -->
    <div class="settings-sections">

      <!-- Category: System Administration (Admin only) -->
      <div v-if="isAdmin" class="settings-category admin-category">
        <h3 class="category-title admin-category-title">{{ $t('settings.categoryAdmin') }}</h3>
        <div class="category-card admin-category-card">
          <!-- AI Provider -->
          <div class="settings-item">
            <div class="item-left">
              <div class="item-icon-wrap">
                <Cpu :size="17" :stroke-width="1.7" />
              </div>
              <div class="item-text">
                <span class="item-title">{{ $t('settings.aiProvider') }}</span>
                <span class="item-desc">{{ currentProviderLabel }}</span>
              </div>
            </div>
            <button class="edit-btn" @click="openAiProviderDialog">{{ $t('common.edit') }}</button>
          </div>

          <div class="settings-item">
            <div class="item-left">
              <div class="item-icon-wrap">
                <Timer :size="17" :stroke-width="1.7" />
              </div>
              <div class="item-text">
                <span class="item-title">{{ $t('settings.sessionTimeout') }}</span>
                <span class="item-desc">{{ $t('settings.sessionTimeoutDesc') }}</span>
              </div>
            </div>
            <div class="session-timeout-ctrl">
              <el-input-number
                v-model="sessionTimeoutMinutes"
                :min="1"
                :max="1440"
                :step="5"
                controls-position="right"
                style="width: 120px;"
              />
              <span class="timeout-unit">min</span>
              <button class="edit-btn" :disabled="sessionTimeoutSaving" @click="handleSaveSessionTimeout">
                {{ sessionTimeoutSaving ? $t('common.saving') : $t('common.save') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Category: Account & Security -->
      <div class="settings-category">
        <h3 class="category-title">{{ $t('settings.categoryAccount') }}</h3>
        <div class="category-card">
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
      </div>

      <!-- Category: Preferences -->
      <div class="settings-category">
        <h3 class="category-title">{{ $t('settings.categoryPreferences') }}</h3>
        <div class="category-card">
          <!-- Language item -->
          <div class="settings-item">
            <div class="item-left">
              <div class="item-icon-wrap">
                <Globe :size="17" :stroke-width="1.7" />
              </div>
              <div class="item-text">
                <span class="item-title">{{ $t('settings.language') }}</span>
                <span class="item-desc">{{ $t('settings.languageDesc') }}</span>
              </div>
            </div>
            <el-select v-model="currentLocale" @change="handleLocaleChange" class="locale-select" style="width: 140px;">
              <el-option label="简体中文" value="zh-CN" />
              <el-option label="繁體中文" value="zh-TW" />
              <el-option label="English" value="en" />
            </el-select>
          </div>
        </div>
      </div>

      <!-- Category: Support & About -->
      <div class="settings-category">
        <h3 class="category-title">{{ $t('settings.categorySupport') }}</h3>
        <div class="category-card">
          <!-- Report Issue item -->
          <div class="settings-item">
            <div class="item-left">
              <div class="item-icon-wrap">
                <AlertCircle :size="17" :stroke-width="1.7" />
              </div>
              <div class="item-text">
                <span class="item-title">{{ $t('settings.reportIssue') }}</span>
                <span class="item-desc">{{ $t('settings.reportIssueDesc') }}</span>
              </div>
            </div>
            <button class="edit-btn" @click="openReportDialog">{{ $t('common.submit') }}</button>
          </div>

          <!-- About item -->
          <div class="settings-item">
            <div class="item-left">
              <div class="item-icon-wrap">
                <Info :size="17" :stroke-width="1.7" />
              </div>
              <div class="item-text">
                <span class="item-title">{{ $t('settings.about') }}</span>
                <span class="item-desc">{{ $t('settings.aboutDesc') }}</span>
              </div>
            </div>
            <button class="edit-btn" @click="openAboutDialog">{{ $t('settings.aboutViewBtn') }}</button>
          </div>
        </div>
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

    <!-- ── Report Issue Dialog ────────────────────── -->
    <el-dialog
      v-model="reportDialogVisible"
      :title="$t('settings.reportIssue')"
      width="460px"
      :close-on-click-modal="false"
      class="settings-dialog"
      @close="resetReportForm"
    >
      <el-form
        ref="reportFormRef"
        :model="reportForm"
        :rules="reportRules"
        label-position="top"
        class="dialog-form"
      >
        <el-form-item :label="$t('settings.issueType')" prop="notifyType">
          <el-select v-model="reportForm.notifyType" class="dialog-select" style="width: 100%;">
            <el-option :label="$t('settings.issueTypeBug')" value="BUG_REPORT" />
            <el-option :label="$t('settings.issueTypeFailure')" value="SYSTEM_FAILURE" />
            <el-option :label="$t('settings.issueTypeTicket')" value="SUPPORT_TICKET" />
          </el-select>
        </el-form-item>

        <el-form-item :label="$t('settings.issueTitle')" prop="title">
          <el-input
            v-model="reportForm.title"
            :placeholder="$t('settings.issueTitlePlaceholder')"
            class="dialog-input"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item :label="$t('settings.issueContent')" prop="content">
          <el-input
            v-model="reportForm.content"
            type="textarea"
            :rows="5"
            :placeholder="$t('settings.issueContentPlaceholder')"
            class="dialog-textarea"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="cancel-btn" @click="reportDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="reportLoading" class="confirm-btn" @click="handleSendReport">
            {{ $t('common.submit') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- ── About Dialog ───────────────────────────── -->
    <el-dialog
      v-model="aboutDialogVisible"
      :title="$t('settings.aboutSystemTitle')"
      width="580px"
      class="settings-dialog about-dialog"
    >
      <div class="about-container">
        <div class="about-logo-area">
          <div class="logo-box">BA</div>
          <div class="logo-text-wrap">
            <h2 class="about-app-title">BankAgent</h2>
            <p class="about-app-sub">Intelligent Financial Co-Running Platform</p>
          </div>
        </div>
        
        <p class="about-description">
          {{ $t('settings.aboutSystemText') }}
        </p>

        <el-divider class="dialog-divider" />

        <div class="about-metadata-list">
          <div class="meta-row">
            <span class="meta-label">{{ $t('settings.aboutVersion') }}:</span>
            <span class="meta-val">v1.2.0-stable</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ $t('settings.aboutCoreTech') }}:</span>
            <span class="meta-val">Spring Cloud / Milvus RAG / Vue 3 / Element Plus</span>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer" style="justify-content: flex-start; padding-left: 8px;">
          <el-button type="primary" class="confirm-btn" style="width: 120px;" @click="aboutDialogVisible = false">
            {{ $t('common.confirm') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- ── AI Provider Dialog ──────────────────────── -->
    <el-dialog
      v-model="aiDialogVisible"
      :title="$t('settings.aiProviderDialogTitle')"
      width="720px"
      :close-on-click-modal="false"
      class="settings-dialog ai-provider-dialog"
    >
      <div class="ai-dialog-layout">
        <!-- Left: Provider list -->
        <div class="ai-dialog-left">
          <div class="ai-preset-label">{{ $t('settings.aiPresets') }}</div>
          <div class="ai-preset-list">
            <div
              v-for="p in AI_PROVIDERS"
              :key="p.key"
              class="ai-preset-item"
              :class="{ selected: aiProviderDraft === p.key }"
              @click="selectProvider(p.key)"
            >
              <div class="ai-preset-name">{{ p.label }}</div>
            </div>
            <div
              class="ai-preset-item"
              :class="{ selected: aiProviderDraft === 'custom' }"
              @click="selectProvider('custom')"
            >
              <div class="ai-preset-name">{{ $t('settings.aiProviderCustom') }}</div>
            </div>
          </div>
        </div>

        <!-- Right: detail form -->
        <div class="ai-dialog-right">
          <div class="ai-form-group">
            <label class="ai-form-label">Base URL</label>
            <el-input
              v-model="aiProviderBaseUrl"
              :disabled="aiProvider !== 'custom'"
              size="small"
            />
          </div>
          <div class="ai-form-group">
            <label class="ai-form-label">Model</label>
            <el-input
              v-if="aiProviderDraft === 'ollama' || aiProviderDraft === 'custom'"
              v-model="aiProviderCustomModel"
              placeholder="e.g. llama3, qwen2"
              size="small"
            />
            <el-input
              v-else
              :model-value="selectedProvider?.model || ''"
              disabled
              size="small"
            />
          </div>
          <div class="ai-form-group">
            <label class="ai-form-label">{{ $t('settings.apiKey') }}</label>
            <el-input
              v-model="aiProviderApiKey"
              type="password"
              show-password
              :placeholder="$t('settings.apiKeyPlaceholder')"
              size="small"
            />
          </div>

          <!-- Test area -->
          <div class="ai-test-area">
            <div class="ai-test-label">{{ $t('settings.testConnection') }}</div>
            <textarea
              v-model="aiTestMessage"
              rows="2"
              :placeholder="$t('settings.testPlaceholder')"
              class="ai-test-input"
            ></textarea>
            <div class="ai-test-actions">
              <button class="btn-test-connection" :disabled="aiTesting" @click="testAiConnection">
                <Loader v-if="aiTesting" :size="12" class="spin" /> {{ aiTesting ? $t('settings.testing') : $t('settings.testConnectionBtn') }}
              </button>
            </div>
            <div v-if="aiTestResult !== null" class="ai-test-result" :class="aiTestResultClass">
              {{ aiTestResult }}
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="aiDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :disabled="aiProviderSaving" @click="handleSaveAiProvider">
            {{ aiProviderSaving ? $t('common.saving') : $t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, Shield, Key, ShieldCheck, Check, X, Globe, AlertCircle, Info, Timer, Cpu, Loader } from 'lucide-vue-next'
import request from '@utils/request'

const { t, locale } = useI18n()
const currentLocale = ref(locale.value)

const handleLocaleChange = (lang: string) => {
  locale.value = lang
  localStorage.setItem('lang', lang)
  ElMessage.success(
    lang === 'en'
      ? 'Language switched successfully!'
      : lang === 'zh-TW'
        ? '語言切換成功！'
        : '语言切换成功！'
  )
}
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

// ── AI Model Provider ───────────────────────────────────
const AI_PROVIDERS_BASE = [
  { key: 'xunfei',       i18nKey: 'settings.aiProviderXunfei',     baseUrl: 'https://maas-api.cn-huabei-1.xf-yun.com/v2', model: 'xopdeepseekv32' },
  { key: 'deepseek',     i18nKey: 'settings.aiProviderDeepseek',   baseUrl: 'https://api.deepseek.com',                   model: 'deepseek-chat' },
  { key: 'deepseek-v4',  i18nKey: 'settings.aiProviderDeepseekV4', baseUrl: 'https://api.deepseek.com',                   model: 'deepseek-v4-flash' },
  { key: 'ollama',       i18nKey: 'settings.aiProviderOllama',     baseUrl: 'http://localhost:11434/v1',                  model: '' },
]

const AI_PROVIDERS = computed(() =>
  AI_PROVIDERS_BASE.map(p => ({ ...p, label: t(p.i18nKey) }))
)

const aiProviderSaving = ref(false)
const aiProvider = ref('')
const aiProviderDraft = ref('')
const aiProviderBaseUrl = ref('')
const aiProviderCustomModel = ref('')

const selectProvider = (key: string) => {
  aiProviderDraft.value = key
  const preset = AI_PROVIDERS.value.find(p => p.key === key)
  if (key === 'custom') {
    aiProviderBaseUrl.value = ''
    aiProviderCustomModel.value = ''
  } else if (preset) {
    aiProviderBaseUrl.value = preset.baseUrl
    aiProviderCustomModel.value = preset.model
  }
}
const aiProviderApiKey = ref('')
const aiDialogVisible = ref(false)
const aiTesting = ref(false)
const aiTestMessage = ref('hi')
const aiTestResult = ref<string | null>(null)
const aiTestResultClass = ref('')

const selectedProvider = computed(() =>
  AI_PROVIDERS.value.find(p => p.key === aiProvider.value)
)

const currentProviderLabel = computed(() => {
  const p = selectedProvider.value
  return p ? `${p.label}${aiProviderCustomModel.value ? ' (' + aiProviderCustomModel.value + ')' : ''}` : ''
})

const openAiProviderDialog = () => {
  aiTestResult.value = null
  aiProviderDraft.value = aiProvider.value
  const preset = AI_PROVIDERS.value.find(p => p.key === aiProvider.value)
  if (aiProvider.value === 'custom') {
    // keep current baseUrl/model
  } else if (preset) {
    aiProviderBaseUrl.value = preset.baseUrl
    aiProviderCustomModel.value = preset.model
  }
  aiDialogVisible.value = true
}

const testAiConnection = async () => {
  aiTesting.value = true
  aiTestResult.value = null
  aiTestResultClass.value = ''
  const baseUrl = aiProviderDraft.value === 'custom'
    ? aiProviderBaseUrl.value
    : (selectedProvider.value?.baseUrl || '')

  try {
    const modelName = aiProviderDraft.value === 'custom'
      ? aiProviderCustomModel.value
      : (aiProviderCustomModel.value || selectedProvider.value?.model || '')
    const res: any = await request.post('/user/config/ai-provider/test', {
      baseUrl,
      model: modelName,
      message: aiTestMessage.value,
      apiKey: aiProviderApiKey.value || undefined,
    })
    if (res && res.ok) {
      aiTestResult.value = `✓ Success — ${res.reply || 'connected'}`
      aiTestResultClass.value = 'success'
    } else {
      aiTestResult.value = `✗ Failed — ${res?.error || 'no response'}`
      aiTestResultClass.value = 'error'
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || 'connection error'
    aiTestResult.value = `✗ Failed — ${msg}`
    aiTestResultClass.value = 'error'
  } finally {
    aiTesting.value = false
  }
}

const loadAiProvider = async () => {
  try {
    const res: any = await request.get('/user/config/ai-provider')
    if (res && res.provider) {
      aiProvider.value = AI_PROVIDERS.value.some(p => p.key === res.provider) ? res.provider : 'xunfei'
      // If the server returns a custom model (e.g. for ollama), populate it
      if (res.model && AI_PROVIDERS.value.find(p => p.key === res.provider)?.model !== res.model) {
        aiProviderCustomModel.value = res.model
      }
      return
    }
  } catch (_) {}
  // Fallback to localStorage
  const rawProvider = localStorage.getItem('ai_provider') || 'xunfei'
  aiProvider.value = AI_PROVIDERS.value.some(p => p.key === rawProvider) ? rawProvider : 'xunfei'
  aiProviderCustomModel.value = localStorage.getItem('ai_provider_custom_model') || ''
}

const handleSaveAiProvider = async () => {
  aiProviderSaving.value = true
  try {
    const baseUrl = aiProviderDraft.value === 'custom'
      ? aiProviderBaseUrl.value.trim()
      : (selectedProvider.value?.baseUrl || '')
    const modelName = aiProviderDraft.value === 'custom'
      ? aiProviderCustomModel.value.trim()
      : (aiProviderCustomModel.value.trim() || (selectedProvider.value?.model || ''))

    await request.put('/user/config/ai-provider', {
      provider: aiProviderDraft.value,
      baseUrl,
      model: modelName,
      apiKey: aiProviderApiKey.value || undefined,
    })

    aiDialogVisible.value = false
    // Commit draft to real state
    aiProvider.value = aiProviderDraft.value

    // Also keep localStorage as fallback
    localStorage.setItem('ai_provider', aiProvider.value)
    if (aiProviderCustomModel.value.trim()) {
      localStorage.setItem('ai_provider_custom_model', aiProviderCustomModel.value.trim())
    } else {
      localStorage.removeItem('ai_provider_custom_model')
    }

    ElMessage.success(t('settings.aiProviderSaved'))
  } catch (_) {
    ElMessage.error(t('settings.aiProviderFailed'))
  } finally {
    aiProviderSaving.value = false
  }
}

// ── Admin: Session Timeout ─────────────────────────────────
const sessionTimeoutMinutes = ref(30)
const sessionTimeoutSaving  = ref(false)

const loadSessionTimeout = async () => {
  if (!isAdmin.value) return
  try {
    const res: any = await request.get('/user/config/session-timeout')
    if (res != null) sessionTimeoutMinutes.value = Number(res)
  } catch (_) {}
}

const handleSaveSessionTimeout = async () => {
  sessionTimeoutSaving.value = true
  try {
    await request.put('/user/config/session-timeout', { timeout: sessionTimeoutMinutes.value })
    ElMessage.success(t('settings.sessionTimeoutSaved'))
  } catch (_) {
    ElMessage.error(t('settings.sessionTimeoutFailed'))
  } finally {
    sessionTimeoutSaving.value = false
  }
}

onMounted(() => { loadSessionTimeout(); loadAiProvider() })

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

const reportDialogVisible = ref(false)
const reportLoading = ref(false)
const reportFormRef = ref()
const reportForm = reactive({
  notifyType: 'BUG_REPORT',
  title: '',
  content: ''
})

const reportRules = reactive({
  notifyType: [{ required: true, message: 'Type is required', trigger: 'change' }],
  title: [{ required: true, message: 'Title is required', trigger: 'blur' }],
  content: [{ required: true, message: 'Description is required', trigger: 'blur' }]
})

const openReportDialog = () => {
  reportDialogVisible.value = true
}

const resetReportForm = () => {
  if (reportFormRef.value) {
    reportFormRef.value.resetFields()
  }
  reportForm.notifyType = 'BUG_REPORT'
  reportForm.title = ''
  reportForm.content = ''
}

const handleSendReport = async () => {
  if (!reportFormRef.value) return
  try {
    await reportFormRef.value.validate()
    reportLoading.value = true
    
    // Send to admin (ID 1)
    await request.post('/user/notification/send', {
      receiverId: 1,
      title: reportForm.title,
      content: reportForm.content,
      notifyType: reportForm.notifyType,
      payload: JSON.stringify({
        reporter: userStore.userInfo?.username || 'user',
        reportTime: new Date().toISOString()
      })
    })

    ElMessage.success(t('settings.reportSuccess'))
    reportDialogVisible.value = false
    resetReportForm()
  } catch (error) {
    console.error('Failed to submit ticket:', error)
    ElMessage.error(t('request.failed'))
  } finally {
    reportLoading.value = false
  }
}

const aboutDialogVisible = ref(false)
const openAboutDialog = () => {
  aboutDialogVisible.value = true
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

/* ── Settings Sections ───────────────────────────── */
.settings-sections {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.settings-category {
  display: flex;
  flex-direction: column;
}

.category-title {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin: 0 0 12px 4px;
}

.category-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.01);
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

.locale-select :deep(.el-select__wrapper) {
  border-radius: 9px;
  box-shadow: 0 0 0 1px #e5e7eb;
}
.locale-select :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #d1d5db;
}
.locale-select :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #111827 !important;
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

/* About Dialog Styles */
.about-container {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 16px;
  padding: 4px 8px;
}

.about-logo-area {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
  text-align: left;
}

.logo-box {
  width: 54px;
  height: 54px;
  background: #111827;
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(17, 24, 39, 0.15);
  flex-shrink: 0;
}

.about-app-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 2px 0;
}

.about-app-sub {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
  font-weight: 500;
}

.about-description {
  font-size: 13.5px;
  line-height: 1.6;
  color: #4b5563;
  text-align: left;
}

.about-metadata-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.meta-label {
  color: #9ca3af;
  font-weight: 500;
}

.meta-val {
  color: #111827;
  font-weight: 600;
}

.dialog-textarea :deep(.el-textarea__inner) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: none !important;
  font-family: inherit;
  transition: all 0.15s;
}

.dialog-textarea :deep(.el-textarea__inner:hover) {
  border-color: #d1d5db;
  background: #fff;
}

.dialog-textarea :deep(.el-textarea__inner:focus) {
  border-color: #111827;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(17,24,39,0.08) !important;
}

.session-timeout-ctrl {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.timeout-unit {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
  white-space: nowrap;
}

.ai-provider-item {
  flex-wrap: wrap;
}

.ai-provider-ctrl {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ── Admin category visual distinction ───────────── */
.admin-category {
  position: relative;
}

.admin-category .admin-category-title {
  color: #7c3aed;
  font-weight: 700;
}

.admin-category .admin-category-card {
  border: 1.5px solid #c4b5fd;
  background: linear-gradient(135deg, #faf5ff 0%, #f5f0ff 100%);
  box-shadow: 0 1px 4px rgba(124, 58, 237, 0.06);
}

.admin-category .settings-item {
  border-bottom-color: #ede4ff;
}

/* ── AI Provider Dialog ──────────────────────────── */
.ai-dialog-layout {
  display: flex;
  gap: 20px;
  min-height: 420px;
}

.ai-dialog-left {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #f1f5f9;
  padding-right: 16px;
}

.ai-preset-label {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 10px;
}

.ai-preset-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-preset-item {
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #475569;
  transition: all 0.15s;
  line-height: 1.4;
}
.ai-preset-item:hover { background: #f8fafc; color: #1e293b; }
.ai-preset-item.selected {
  background: #f5f3ff;
  color: #4f46e5;
  font-weight: 600;
}

.ai-dialog-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-form-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.ai-test-area {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.ai-test-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}

.ai-test-input {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  resize: none;
  outline: none;
  box-sizing: border-box;
}
.ai-test-input:focus { border-color: #4f46e5; }

.ai-test-actions {
  margin-top: 8px;
}

.btn-test-connection {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-test-connection:hover { background: #f8fafc; border-color: #cbd5e1; }
.btn-test-connection:disabled { opacity: 0.5; cursor: not-allowed; }

.ai-test-result {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 500;
}
.ai-test-result.success { background: #f0fdf4; color: #15803d; }
.ai-test-result.error   { background: #fef2f2; color: #dc2626; }
/* ── End AI Provider Dialog ──────────────────────── */

/* Dialog global style overrides */
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

/* ── AI Provider Dialog ──────────────────────────── */
.ai-dialog-layout {
  display: flex;
  gap: 20px;
}

.ai-dialog-left {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #f1f5f9;
  padding-right: 16px;
}

.ai-preset-label {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 10px;
}

.ai-preset-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-preset-item {
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12.5px;
  color: #475569;
  transition: all 0.15s;
  border: 1px solid transparent;
}
.ai-preset-item:hover { background: #f8fafc; color: #1e293b; }
.ai-preset-item.selected {
  background: #f5f3ff;
  color: #4f46e5;
  font-weight: 600;
  border-color: #e8e5ff;
}

.ai-dialog-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-form-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.ai-test-area {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.ai-test-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
}

.ai-test-input {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  resize: none;
  outline: none;
  box-sizing: border-box;
}
.ai-test-input:focus { border-color: #4f46e5; }

.ai-test-actions {
  margin-top: 8px;
}

.btn-test-connection {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-test-connection:hover { background: #f8fafc; border-color: #cbd5e1; }
.btn-test-connection:disabled { opacity: 0.5; cursor: not-allowed; }

.ai-test-result {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 500;
}
.ai-test-result.success { background: #f0fdf4; color: #15803d; }
.ai-test-result.error   { background: #fef2f2; color: #dc2626; }
</style>
