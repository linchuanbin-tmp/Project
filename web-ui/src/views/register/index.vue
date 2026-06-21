<template>
  <div class="login-card">
    <div class="card-body">

      <p class="app-label">BankAgent</p>

      <div class="heading-block">
        <h1 class="page-heading">Create account</h1>
        <p class="page-sub">Fill in the details below to get started</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="email">
          <label class="field-label">Email address</label>
          <el-input
              v-model="form.email"
              placeholder="you@example.com"
              :prefix-icon="() => h(Mail, { size: 15, strokeWidth: 1.6 })"
              class="soft-input"
          />
        </el-form-item>

        <el-form-item prop="password">
          <label class="field-label">Password</label>
          <el-input
              v-model="form.password"
              type="password"
              placeholder="At least 8 characters"
              :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
              show-password
              class="soft-input"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <label class="field-label">Confirm password</label>
          <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="Repeat your password"
              :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
              show-password
              @keyup.enter="submit"
              class="soft-input"
          />
        </el-form-item>

        <el-form-item>
          <el-button :loading="loading" class="submit-btn" @click="submit">
            Create account
          </el-button>
        </el-form-item>
      </el-form>

      <p class="switch-link">
        Already have an account?
        <router-link to="/login" class="link">Sign in</router-link>
      </p>

    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Mail } from 'lucide-vue-next'
import request from '@utils/request'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  email: '',
  password: '',
  confirmPassword: ''
})

const rules = {
  email: [
    { required: true, message: 'Please enter your email', trigger: 'blur' },
    { type: 'email', message: 'Please enter a valid email', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please set a password', trigger: 'blur' },
    { min: 8, message: 'At least 8 characters', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: 'Please confirm your password', trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: Function) => {
        if (value !== form.password) callback(new Error('Passwords do not match'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const submit = async () => {
  try {
    await formRef.value?.validate()
    loading.value = true
    await request.post('/user/register', {
      username: form.email,
      password: form.password,
      realName: form.email
    })
    ElMessage.success('Account created! Please sign in.')
    router.push('/login')
  } catch {
    // errors shown by Axios interceptor
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
</style>
