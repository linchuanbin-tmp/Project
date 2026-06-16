<template>
  <div class="login-page">
    <div class="login-card">
      <div class="card-body">

        <p class="app-label">BankAgent</p>

        <!-- Step 1: Enter email -->
        <template v-if="step === 1">
          <div class="heading-block">
            <h1 class="page-heading">Create account</h1>
            <p class="page-sub">Start with your email address</p>
          </div>

          <el-form :model="emailForm" :rules="emailRules" ref="emailFormRef">
            <el-form-item prop="email">
              <label class="field-label">Email address</label>
              <el-input
                  v-model="emailForm.email"
                  placeholder="you@example.com"
                  :prefix-icon="() => h(Mail, { size: 15, strokeWidth: 1.6 })"
                  class="soft-input"
                  @keyup.enter="sendCode"
              />
            </el-form-item>
            <el-form-item>
              <el-button :loading="sending" class="submit-btn" @click="sendCode">
                Send verification code
              </el-button>
            </el-form-item>
          </el-form>
        </template>

        <!-- Step 2: Enter verification code -->
        <template v-else-if="step === 2">
          <div class="heading-block">
            <h1 class="page-heading">Check your email</h1>
            <p class="page-sub">
              We sent a 6-digit code to<br>
              <strong class="email-highlight">{{ emailForm.email }}</strong>
            </p>
          </div>

          <!-- OTP digit inputs -->
          <div class="otp-row">
            <input
                v-for="(_, i) in 6"
                :key="i"
                :ref="el => otpInputs[i] = el"
                v-model="otpDigits[i]"
                class="otp-box"
                maxlength="1"
                inputmode="numeric"
                @input="onOtpInput(i)"
                @keydown="onOtpKeydown($event, i)"
                @paste="onOtpPaste($event)"
                :class="{ filled: otpDigits[i] }"
            />
          </div>

          <p v-if="otpError" class="otp-error">{{ otpError }}</p>

          <el-button
              :loading="verifying"
              class="submit-btn"
              style="margin-top: 24px"
              @click="verifyCode"
          >
            Verify code
          </el-button>

          <div class="resend-row">
            <span v-if="countdown > 0" class="resend-timer">
              Resend in {{ countdown }}s
            </span>
            <button v-else class="resend-btn" @click="sendCode">
              Resend code
            </button>
          </div>
        </template>

        <!-- Step 3: Set up account -->
        <template v-else-if="step === 3">
          <div class="heading-block">
            <h1 class="page-heading">Set up account</h1>
            <p class="page-sub">Choose a username and password</p>
          </div>

          <el-form :model="profileForm" :rules="profileRules" ref="profileFormRef">
            <el-form-item prop="username">
              <label class="field-label">Username</label>
              <el-input
                  v-model="profileForm.username"
                  placeholder="Choose a username"
                  :prefix-icon="() => h(User, { size: 15, strokeWidth: 1.6 })"
                  class="soft-input"
              />
            </el-form-item>
            <el-form-item prop="password">
              <label class="field-label">Password</label>
              <el-input
                  v-model="profileForm.password"
                  type="password"
                  placeholder="At least 8 characters"
                  :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
                  class="soft-input"
              />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <label class="field-label">Confirm password</label>
              <el-input
                  v-model="profileForm.confirmPassword"
                  type="password"
                  placeholder="Repeat your password"
                  :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
                  @keyup.enter="createAccount"
                  class="soft-input"
              />
            </el-form-item>
            <el-form-item>
              <el-button :loading="creating" class="submit-btn" @click="createAccount">
                Create account
              </el-button>
            </el-form-item>
          </el-form>
        </template>

        <!-- Step indicator dots -->
        <div class="step-dots">
          <span v-for="n in 3" :key="n" class="dot" :class="{ active: step >= n }" />
        </div>

        <p class="switch-link">
          Already have an account?
          <router-link to="/login" class="link">Sign in</router-link>
        </p>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onUnmounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Mail } from 'lucide-vue-next'

const router = useRouter()

const step = ref(1)
const sending = ref(false)
const verifying = ref(false)
const creating = ref(false)
const countdown = ref(0)
const otpError = ref('')
let timer: ReturnType<typeof setInterval> | null = null

// Step 1: Email form
const emailFormRef = ref()
const emailForm = reactive({ email: '' })
const emailRules = {
  email: [
    { required: true, message: 'Please enter your email', trigger: 'blur' },
    { type: 'email', message: 'Please enter a valid email', trigger: 'blur' }
  ]
}

const sendCode = async () => {
  try {
    await emailFormRef.value?.validate()
    sending.value = true
    // TODO: integrate Resend email API
    await new Promise(r => setTimeout(r, 1200))
    ElMessage.success('Verification code sent!')
    step.value = 2
    startCountdown()
  } catch (e) {
    console.error(e)
  } finally {
    sending.value = false
  }
}

// Step 2: OTP input
const otpDigits = reactive<string[]>(Array(6).fill(''))
const otpInputs = ref<HTMLInputElement[]>([])

const onOtpInput = (i: number) => {
  const val = otpDigits[i]
  if (val && i < 5) {
    otpInputs.value[i + 1]?.focus()
  }
  otpError.value = ''
}

const onOtpKeydown = (e: KeyboardEvent, i: number) => {
  if (e.key === 'Backspace' && !otpDigits[i] && i > 0) {
    otpInputs.value[i - 1]?.focus()
  }
}

const onOtpPaste = (e: ClipboardEvent) => {
  e.preventDefault()
  const text = e.clipboardData?.getData('text').replace(/\D/g, '').slice(0, 6) || ''
  for (let i = 0; i < 6; i++) {
    otpDigits[i] = text[i] || ''
  }
  otpInputs.value[Math.min(text.length, 5)]?.focus()
}

const verifyCode = async () => {
  const code = otpDigits.join('')
  if (code.length < 6) {
    otpError.value = 'Please enter the full 6-digit code'
    return
  }
  verifying.value = true
  // TODO: verify code against backend
  await new Promise(r => setTimeout(r, 1000))
  verifying.value = false
  step.value = 3
}

const startCountdown = () => {
  countdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    if (countdown.value > 0) countdown.value--
    else { clearInterval(timer!); timer = null }
  }, 1000)
}

// Step 3: Set username & password
const profileFormRef = ref()
const profileForm = reactive({ username: '', password: '', confirmPassword: '' })
const profileRules = {
  username: [
    { required: true, message: 'Please choose a username', trigger: 'blur' },
    { min: 3, message: 'At least 3 characters', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please set a password', trigger: 'blur' },
    { min: 8, message: 'At least 8 characters', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: 'Please confirm your password', trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: Function) => {
        if (value !== profileForm.password) callback(new Error('Passwords do not match'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const createAccount = async () => {
  try {
    await profileFormRef.value?.validate()
    creating.value = true
    // TODO: call register API
    await new Promise(r => setTimeout(r, 1200))
    ElMessage.success('Account created! Please sign in.')
    router.push('/login')
  } catch (e) {
    console.error(e)
  } finally {
    creating.value = false
  }
}

onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
/* ── 复用登录页背景和卡片 ─────────────────────── */
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  background-image:
    radial-gradient(ellipse at 20% 50%, rgba(17,24,39,0.05) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 20%, rgba(99,102,241,0.06) 0%, transparent 50%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

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

/* ── 品牌 ──────────────────────────────────────── */
.app-label {
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
  margin: 0 0 28px 0;
}

/* ── 标题区 ────────────────────────────────────── */
.heading-block { margin-bottom: 32px; }

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

.email-highlight {
  color: #374151;
  font-weight: 500;
}

/* ── 字段标签 ──────────────────────────────────── */
.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

/* ── 输入框 ────────────────────────────────────── */
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

/* ── 按钮 ──────────────────────────────────────── */
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
}
.submit-btn:hover { opacity: 0.88; transform: translateY(-1px); }
.submit-btn:active { transform: translateY(0); }

/* OTP input boxes */
.otp-row {
  display: flex;
  gap: 10px;
  justify-content: flex-start;
}

.otp-box {
  width: 52px;
  height: 56px;
  border: 1.5px solid #e5e7eb;
  border-radius: 12px;
  background: #f9fafb;
  font-size: 22px;
  font-weight: 600;
  color: #111827;
  text-align: center;
  outline: none;
  transition: all 0.15s;
  caret-color: #111827;
}

.otp-box:focus {
  border-color: #111827;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(17,24,39,0.08);
}

.otp-box.filled {
  border-color: #374151;
  background: #fff;
}

.otp-error {
  margin: 10px 0 0;
  font-size: 13px;
  color: #ef4444;
}

/* ── 重发倒计时 ────────────────────────────────── */
.resend-row {
  margin-top: 16px;
  font-size: 13.5px;
  text-align: center;
}

.resend-timer { color: #9ca3af; }

.resend-btn {
  background: none;
  border: none;
  padding: 0;
  color: #111827;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.resend-btn:hover { opacity: 0.7; }

/* ── 步骤指示点 ────────────────────────────────── */
.step-dots {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin: 28px 0 20px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #e5e7eb;
  transition: background 0.2s, transform 0.2s;
}

.dot.active {
  background: #111827;
  transform: scale(1.2);
}

/* ── 跳转登录 ──────────────────────────────────── */
.switch-link {
  font-size: 13.5px;
  color: #9ca3af;
  text-align: center;
  margin: 0;
}

.link {
  color: #111827;
  font-weight: 500;
  text-decoration: none;
  margin-left: 4px;
}
.link:hover { text-decoration: underline; }

/* Element Plus overrides */
:deep(.el-form-item) { margin-bottom: 18px; }
:deep(.el-form-item__content) { flex-direction: column; align-items: flex-start; }
</style>
