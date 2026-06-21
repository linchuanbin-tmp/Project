<template>
  <div class="login-card">
    <div class="card-body">

      <!-- Brand label -->
      <p class="app-label">BankAgent</p>

      <!-- Page heading -->
      <div class="heading-block">
        <h1 class="page-heading">Sign in</h1>
        <p class="page-sub">Access your account to continue</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <label class="field-label">Email</label>
          <el-input
              v-model="form.username"
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
              placeholder="Enter your password"
              :prefix-icon="() => h(Lock, { size: 15, strokeWidth: 1.6 })"
              @keyup.enter="handleLogin"
              class="soft-input"
          />
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" class="submit-btn" @click="handleLogin">
            Continue
          </el-button>
        </el-form-item>
      </el-form>

      <p class="switch-link">
        Don't have an account?
        <router-link to="/register" class="link">Create one</router-link>
      </p>

    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, h } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import { Mail, Lock } from 'lucide-vue-next'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref()

const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [
    { required: true, message: 'Please enter your email', trigger: 'blur' }
  ],
  password: [{ required: true, message: 'Please enter your password', trigger: 'blur' }]
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    await userStore.login(form.username, form.password)
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
  margin: 24px 0 0;
  font-size: 13.5px;
  color: #9ca3af;
  text-align: center;
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