<template>
  <div class="login-page">
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
            <label class="field-label">Username</label>
            <el-input
                v-model="form.username"
                placeholder="Enter your username"
                :prefix-icon="() => h(User, { size: 15, strokeWidth: 1.6 })"
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
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, h } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import { User, Lock } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref()

const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [{ required: true, message: 'Please enter your username', trigger: 'blur' }],
  password: [{ required: true, message: 'Please enter your password', trigger: 'blur' }]
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    await userStore.login(form.username, form.password)
    router.push('/dashboard')
  } catch (error) {
    console.error('Login failed:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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

/* ── 品牌标识：小而克制 ─────────────────────────── */
.app-label {
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
  margin: 0 0 28px 0;
  letter-spacing: 0;
}

/* ── 标题区：和品牌标识拉开距离 ──────────────────── */
.heading-block {
  margin-bottom: 32px;
}

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

/* ── 字段标签 ─────────────────────────────────── */
.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

/* ── 输入框 ───────────────────────────────────── */
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

/* ── 按钮 ─────────────────────────────────────── */
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

/* ── 跳转注册 ─────────────────────────────────── */
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

/* Element Plus overrides */
:deep(.el-form-item) { margin-bottom: 18px; }
:deep(.el-form-item__content) { flex-direction: column; align-items: flex-start; }
</style>