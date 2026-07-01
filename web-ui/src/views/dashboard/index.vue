<template>
  <div class="dashboard">

    <!-- Page header -->
    <div class="page-header">
      <h1 class="page-title">Dashboard</h1>
      <p class="page-sub">Welcome back, {{ userStore.userInfo?.username || 'User' }}. Here's what's happening.</p>
    </div>

    <!-- Quick access: 3-column grid -->
    <p class="section-label">Quick access</p>
    <div class="action-grid">
      <router-link
          v-for="action in actions"
          :key="action.path"
          :to="action.path"
          class="action-card"
      >
        <div class="action-icon-wrap" :style="{ background: action.bg }">
          <component :is="action.icon" :size="20" :stroke-width="1.5" :color="action.color" />
        </div>
        <p class="action-name">{{ action.name }}</p>
        <p class="action-desc">{{ action.desc }}</p>
        <div class="action-footer">
          <span class="action-link">Open <ArrowRight :size="12" :stroke-width="2" /></span>
        </div>
      </router-link>
    </div>

    <!-- Service status: compact inline bar -->
    <p class="section-label" style="margin-top: 36px;">Service status</p>
    <div class="status-bar">
      <div class="status-item" v-for="svc in services" :key="svc.name">
        <span class="status-dot" :class="svc.status"></span>
        <span class="status-name">{{ svc.name }}</span>
        <span class="status-badge" :class="svc.status">{{ svc.label }}</span>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@stores/modules/user'
import { Wrench, FileText, BookOpen, ArrowRight } from 'lucide-vue-next'

const userStore = useUserStore()

const actions = [
  {
    path: '/tool',
    name: 'Tool Call',
    desc: 'Meeting rooms, schedule conflicts, and route planning via natural language.',
    icon: Wrench,
    bg: '#f0f9ff',
    color: '#0ea5e9',
  },
  {
    path: '/code',
    name: 'SQL Generator',
    desc: 'Describe what data you need and let the agent write the query for you.',
    icon: FileText,
    bg: '#f0fdf4',
    color: '#22c55e',
  },
  {
    path: '/rag',
    name: 'Knowledge Q&A',
    desc: 'Upload documents and ask questions. Powered by RAG for grounded answers.',
    icon: BookOpen,
    bg: '#fdf4ff',
    color: '#a855f7',
  },
]

const services = [
  { name: 'Gateway',    status: 'online',  label: 'Online' },
  { name: 'User',       status: 'online',  label: 'Online' },
  { name: 'Tool Agent', status: 'online',  label: 'Online' },
  { name: 'SQL Agent',  status: 'online',  label: 'Online' },
  { name: 'RAG Agent',  status: 'offline', label: 'Coming soon' },
]
</script>

<style scoped>
.dashboard {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-width: 1200px;
  padding-top: 16px;
}

/* ── 页头 ──────────────────────────────────────── */
.page-header {
  margin-bottom: 40px;
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

/* ── 区块标签 ──────────────────────────────────── */
.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 14px 0;
}

/* ── 三列横排卡片 ──────────────────────────────── */
.action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.action-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px 22px 20px;
  border: 1px solid #f0f0f0;
  text-decoration: none;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  transition: box-shadow 0.15s, border-color 0.15s, transform 0.12s;
}

.action-card:hover {
  box-shadow: 0 4px 20px rgba(0,0,0,0.07);
  border-color: #e5e7eb;
  transform: translateY(-2px);
}

.action-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.action-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 8px 0;
  letter-spacing: -0.2px;
}

.action-desc {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
  line-height: 1.55;
  flex: 1;
}

.action-footer {
  margin-top: 20px;
}

.action-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  transition: color 0.15s;
}

.action-card:hover .action-link {
  color: #111827;
}

/* ── 服务状态：紧凑横向 ────────────────────────── */
.status-bar {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  padding: 6px 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 8px;
  transition: background 0.12s;
}

.status-item:hover { background: #f9fafb; }

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.online  { background: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,0.2); }
.status-dot.offline { background: #d1d5db; }

.status-name {
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
}

.status-badge {
  font-size: 11.5px;
  font-weight: 500;
  padding: 2px 7px;
  border-radius: 20px;
}

.status-badge.online  { background: #f0fdf4; color: #16a34a; }
.status-badge.offline { background: #f3f4f6; color: #9ca3af; }

/* ── 移动端适配 ─────────────────────────────────── */
@media (max-width: 768px) {
  .page-header {
    margin-bottom: 24px;
    padding-top: 10px;
  }

  .action-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .action-card {
    padding: 20px 18px 16px;
  }
}
</style>