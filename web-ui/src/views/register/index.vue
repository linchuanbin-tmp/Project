<template>
  <div class="login-card">
    <div class="card-body">

      <p class="app-label">{{ $t('register.appLabel') }}</p>

      <div class="heading-block">
        <h1 class="page-heading">{{ $t('register.heading') }}</h1>
        <p class="page-sub">{{ $t('register.subheading') }}</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="email" :error="emailError">
          <label class="field-label">{{ $t('register.email') }}</label>
          <el-input
              v-model="form.email"
              :placeholder="$t('register.emailPlaceholder')"
              :prefix-icon="() => h(Mail, { size: 15, strokeWidth: 1.6 })"
              class="soft-input"
          />
        </el-form-item>

        <el-form-item prop="code">
          <label class="field-label">{{ $t('register.code') }}</label>
          <div class="code-row">
            <el-input
                v-model="form.code"
                :placeholder="$t('register.codePlaceholder')"
                maxlength="6"
                class="soft-input code-input"
            />
            <button
                :disabled="countdown > 0 || sending"
                class="send-code-btn"
                @click="sendCode"
                type="button"
            >
              <span v-if="sending" class="btn-loading-dot"></span>
              {{ countdown > 0 ? $t('register.resendCode', { seconds: countdown }) : $t('register.sendCode') }}
            </button>
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <label class="field-label">{{ $t('register.password') }}</label>
          <el-input
              v-model="form.password"
              type="password"
              :placeholder="$t('register.passwordPlaceholder')"
              :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
              show-password
              class="soft-input"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <label class="field-label">{{ $t('register.confirmPassword') }}</label>
          <el-input
              v-model="form.confirmPassword"
              type="password"
              :placeholder="$t('register.confirmPasswordPlaceholder')"
              :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
              show-password
              @keyup.enter="submit"
              class="soft-input"
          />
        </el-form-item>

        <el-form-item>
          <el-button :loading="loading" class="submit-btn" @click="submit">
            {{ $t('register.registerBtn') }}
          </el-button>
        </el-form-item>
      </el-form>

      <p class="switch-link">
        {{ $t('register.haveAccount') }}
        <router-link to="/login" class="link">{{ $t('register.signIn') }}</router-link>
      </p>

    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, h, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Lock, Mail } from 'lucide-vue-next'
import request from '@utils/request'

const router = useRouter()
const { t } = useI18n()
const formRef = ref()
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const emailError = ref('')
let timer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const form = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  code: ''
})

watch(() => form.email, () => {
  if (emailError.value) emailError.value = ''
})

const sendCode = async () => {
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.warning(t('register.emailInvalid'))
    return
  }
  try {
    sending.value = true
    await request.post('/user/send-code', { email: form.email, scene: 'register' }, { silent: true } as any)
    ElMessage.success(t('register.codeSent'))
    emailError.value = ''
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        if (timer) clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch (e: any) {
    const msg = e?.message || ''
    if (msg.includes('already registered')) {
      emailError.value = t('register.emailAlreadyRegistered')
    } else {
      ElMessage.error(msg || t('request.failed'))
    }
  } finally {
    sending.value = false
  }
}

const rules = {
  email: [
    { required: true, message: t('register.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('register.emailInvalid'), trigger: 'blur' }
  ],
  code: [
    { required: true, message: t('register.codeRequired'), trigger: 'blur' },
    { pattern: /^\d{6}$/, message: t('register.codeRequired'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('register.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('register.passwordMinLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('register.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: Function) => {
        if (value !== form.password) callback(new Error(t('register.passwordMismatch')))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const submit = () => {
  if (!formRef.value) return
  formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    try {
      loading.value = true
      await request.post('/user/register', {
        username: form.email,
        password: form.password,
        realName: form.email,
        code: form.code
      })
      ElMessage.success(t('register.registerSuccess'))
      router.push('/login')
    } catch {
      // errors shown by Axios interceptor
    } finally {
      loading.value = false
    }
  })
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

.heading-block { margin-bottom: 28px; }

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
  line-height: 1.6;
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
  transition: opacity 0.15s, transform 0.1s;
  margin-top: 4px;
}
.submit-btn:hover { opacity: 0.88; transform: translateY(-1px); }
.submit-btn:active { transform: translateY(0); }

.switch-link {
  font-size: 13.5px;
  color: #9ca3af;
  text-align: center;
  margin: 24px 0 0;
}

.link {
  color: #111827;
  font-weight: 500;
  text-decoration: none;
  margin-left: 4px;
}
.link:hover { text-decoration: underline; }

:deep(.el-form-item) { margin-bottom: 18px; }
:deep(.el-form-item__content) { flex-direction: column; align-items: flex-start; }

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}
.code-input {
  flex: 1;
}
.send-code-btn {
  flex-shrink: 0;
  width: 150px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  color: #374151;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s;
  box-sizing: border-box;
  font-family: inherit;
  margin: 0;
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
.soft-input input:-webkit-autofill,
.soft-input input:-webkit-autofill:hover,
.soft-input input:-webkit-autofill:focus,
.soft-input input:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 30px #f9fafb inset !important;
  -webkit-text-fill-color: #111827 !important;
  transition: background-color 5000s ease-in-out 0s;
}
</style>