<template>
  <div class="login-card">
    <div class="card-body">

      <!-- Brand label -->
      <p class="app-label">{{ $t('login.appLabel') }}</p>

      <!-- Session Expired Alert -->
      <el-alert
        v-if="showExpiredAlert"
        :title="$t('request.sessionExpired')"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 20px; border-radius: 10px; border: 1px solid #fef0f0;"
      />

      <!-- Page heading -->
      <div class="heading-block">
        <h1 class="page-heading">{{ $t('login.heading') }}</h1>
        <p class="page-sub">{{ $t('login.subheading') }}</p>
      </div>

      <!-- Login mode toggle -->
      <div class="mode-toggle">
        <button
            :class="['mode-btn', { active: loginMode === 'password' }]"
            @click="switchMode('password')"
            type="button"
        >{{ $t('login.passwordLogin') }}</button>
        <button
            :class="['mode-btn', { active: loginMode === 'code' }]"
            @click="switchMode('code')"
            type="button"
        >{{ $t('login.codeLogin') }}</button>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <label class="field-label">{{ $t('login.email') }}</label>
          <el-input
              v-model="form.username"
              :placeholder="$t('login.emailPlaceholder')"
              :prefix-icon="() => h(Mail, { size: 15, strokeWidth: 1.6 })"
              class="soft-input"
          />
        </el-form-item>

        <!-- Password mode -->
        <el-form-item v-if="loginMode === 'password'" prop="password">
          <label class="field-label">{{ $t('login.password') }}</label>
          <el-input
              v-model="form.password"
              type="password"
              :placeholder="$t('login.passwordPlaceholder')"
              :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
              @keyup.enter="handleLogin"
              class="soft-input"
          />
        </el-form-item>

        <!-- Code mode -->
        <el-form-item v-if="loginMode === 'code'" prop="code">
          <label class="field-label">{{ $t('login.code') }}</label>
          <div class="code-row">
            <el-input
                v-model="form.code"
                :placeholder="$t('login.codePlaceholder')"
                :prefix-icon="() => h(ShieldCheck, { size: 15, strokeWidth: 1.6 })"
                maxlength="6"
                @keyup.enter="handleLogin"
                class="soft-input code-input"
            />
            <el-button
                :loading="sending"
                :disabled="countdown > 0"
                class="send-code-btn"
                @click="sendCode"
            >
              {{ countdown > 0 ? $t('login.resendCode', { seconds: countdown }) : $t('login.sendCode') }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button :loading="loading" class="submit-btn" @click="handleLogin">
            {{ $t('login.loginBtn') }}
          </el-button>
        </el-form-item>
      </el-form>

      <p class="switch-link">
        {{ $t('login.noAccount') }}
        <router-link to="/register" class="link">{{ $t('login.createOne') }}</router-link>
      </p>

      <p class="forgot-link-wrap">
        <a class="forgot-link" @click.prevent="forgotDialogVisible = true">{{ $t('login.forgotPassword') }}</a>
      </p>

    </div>

    <!-- Forgot Password Help Dialog -->
    <el-dialog
      v-model="forgotDialogVisible"
      :title="$t('login.forgotPassword')"
      width="400px"
      :close-on-click-modal="false"
      class="forgot-dialog"
      :align-center="false"
    >
      <p class="forgot-dialog-text">{{ $t('login.forgotPasswordHint') }}</p>
      <template #footer>
        <el-button type="primary" class="forgot-dialog-btn" @click="forgotDialogVisible = false">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, h, computed, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { Mail, Lock, ShieldCheck } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import request from '@utils/request'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
const formRef = ref()
const showExpiredAlert = ref(route.query.expired === '1')
const forgotDialogVisible = ref(false)
const loginMode = ref<'password' | 'code'>('password')
const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const form = reactive({ username: '', password: '', code: '' })

const rules = computed(() => {
  const base: any = {
    username: [
      { required: true, message: t('login.emailRequired'), trigger: 'blur' }
    ]
  }
  if (loginMode.value === 'code') {
    base.code = [
      { required: true, message: t('login.codeRequired'), trigger: 'blur' },
      { pattern: /^\d{6}$/, message: t('login.codeRequired'), trigger: 'blur' }
    ]
  } else {
    base.password = [
      { required: true, message: t('login.passwordRequired'), trigger: 'blur' }
    ]
  }
  return base
})

const switchMode = (mode: 'password' | 'code') => {
  loginMode.value = mode
  form.password = ''
  form.code = ''
  formRef.value?.clearValidate()
}

const sendCode = async () => {
  if (!form.username || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.username)) {
    ElMessage.warning(t('login.emailRequired'))
    return
  }
  try {
    sending.value = true
    await request.post('/user/send-code', { email: form.username })
    ElMessage.success(t('login.codeSent'))
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        if (timer) clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch {
    // error handled by axios interceptor
  } finally {
    sending.value = false
  }
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    if (loginMode.value === 'code') {
      await userStore.login(form.username, '', form.code)
    } else {
      await userStore.login(form.username, form.password)
    }
    router.push('/app/dashboard')
  } catch {
    // errors handled by Axios interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-card {
  width: 420px;
  background: #fff;
  border-radius: 20px;
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.04),
    0 8px 24px rgba(0,0,0,0.08),
    0 32px 64px rgba(0,0,0,0.04);
}

.card-body {
  padding: 40px 36px 36px;
}

.app-label {
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
  margin: 0 0 28px 0;
}

.heading-block { margin-bottom: 24px; }

.page-heading {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 6px 0;
  letter-spacing: -0.6px;
  line-height: 1.2;
}

.page-sub {
  font-size: 14px;
  color: #9ca3af;
  margin: 0;
  font-weight: 400;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.soft-input :deep(.el-input__wrapper) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: none !important;
  height: 44px;
  transition: all 0.15s;
}
.soft-input :deep(.el-input__wrapper:hover) {
  border-color: #d1d5db;
  background: #fff;
}
.soft-input :deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(17,24,39,0.08) !important;
}
.soft-input :deep(.el-input__inner) {
  font-size: 14px;
  color: #111827;
}

.submit-btn {
  width: 100%;
  height: 44px;
  background: #111827 !important;
  border: none !important;
  border-radius: 10px !important;
  color: #fff !important;
  font-size: 14px;
  font-weight: 500;
  margin-top: 4px;
  transition: opacity 0.15s, transform 0.1s;
}
.submit-btn:hover { opacity: 0.88; transform: translateY(-1px); }
.submit-btn:active { transform: translateY(0); }

.switch-link {
  margin: 24px 0 12px;
  font-size: 13.5px;
  color: #9ca3af;
  text-align: center;
}
.link {
  color: #111827;
  font-weight: 500;
  text-decoration: none;
  margin-left: 4px;
  cursor: pointer;
}
.link:hover { text-decoration: underline; }
.forgot-link-wrap {
  margin: 0;
  text-align: center;
}
.forgot-link {
  font-size: 12.5px;
  color: #9ca3af;
  text-decoration: none;
  cursor: pointer;
  transition: color 0.15s;
}
.forgot-link:hover {
  color: #6366f1;
}

:deep(.el-form-item) { margin-bottom: 18px; }
:deep(.el-form-item__content) { flex-direction: column; align-items: flex-start; }

.mode-toggle {
  display: flex;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 1px;
}
.mode-btn {
  flex: 1;
  padding: 8px 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s;
}
.mode-btn.active {
  background: #fff;
  color: #111827;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.code-input {
  flex: 1;
}
.send-code-btn {
  width: 150px;
  height: 44px;
  background: #fff !important;
  border: 1px solid #d1d5db !important;
  border-radius: 10px !important;
  color: #374151 !important;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  transition: all 0.15s;
}
.send-code-btn:hover:not(:disabled) {
  border-color: #111827 !important;
  color: #111827 !important;
}
.send-code-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

<style>
.forgot-dialog .el-dialog {
  border-radius: 14px !important;
  overflow: hidden;
  box-shadow: 0 0 0 1px rgba(0,0,0,0.04), 0 8px 24px rgba(0,0,0,0.08), 0 32px 64px rgba(0,0,0,0.04) !important;
}
.forgot-dialog .el-dialog__header {
  margin: 0;
  padding: 24px 24px 0 !important;
}
.forgot-dialog .el-dialog__title {
  font-size: 16px !important;
  font-weight: 600 !important;
  color: #111827 !important;
}
.forgot-dialog .el-dialog__body {
  padding: 12px 24px !important;
}
.forgot-dialog .el-dialog__footer {
  padding: 0 24px 24px !important;
  text-align: left !important;
}

.forgot-dialog .forgot-dialog-text {
  font-size: 14px;
  color: #4b5563;
  line-height: 1.7;
  margin: 0;
  text-align: left;
}
.forgot-dialog .forgot-dialog-btn {
  height: 36px;
  padding: 0 20px;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 500;
  background: #111827 !important;
  border-color: #111827 !important;
}
.forgot-dialog .forgot-dialog-btn:hover {
  background: #1f2937 !important;
  border-color: #1f2937 !important;
}</style>