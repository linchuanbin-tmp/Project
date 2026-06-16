<template>
  <el-container class="main-layout">

    <!-- Sidebar -->
    <el-aside :width="collapsed ? '56px' : '230px'" class="aside" :class="{ collapsed }">

      <!-- Logo & collapse toggle -->
      <div class="sidebar-logo">
        <span v-if="!collapsed" class="logo-text">BankAgent</span>
        <button class="collapse-btn" @click="collapsed = !collapsed">
          <PanelLeftClose v-if="!collapsed" :size="16" :stroke-width="1.6" />
          <PanelLeftOpen v-else :size="16" :stroke-width="1.6" />
        </button>
      </div>

      <!-- Navigation menu -->
      <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          :collapse="collapsed"
          :collapse-transition="false"
      >
        <el-menu-item index="/dashboard">
          <template #title>Dashboard</template>
          <Home :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <div v-if="!collapsed" class="menu-section-title">Agent services</div>

        <el-menu-item index="/tool">
          <template #title>Tool Call</template>
          <Wrench :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/code">
          <template #title>SQL Generator</template>
          <FileText :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/rag">
          <template #title>Knowledge Q&A</template>
          <BookOpen :size="16" :stroke-width="1.6" />
        </el-menu-item>
      </el-menu>

      <!-- Bottom: settings & user -->
      <div class="sidebar-bottom">
        <div class="bottom-divider"></div>

        <!-- System settings -->
        <div class="bottom-item settings-item" :class="{ 'icon-only': collapsed }">
          <Settings :size="16" :stroke-width="1.6" class="icon" />
          <span v-if="!collapsed">System settings</span>
        </div>

        <!-- User info & logout -->
        <el-dropdown @command="handleCommand" trigger="click" placement="top-start">
          <div class="bottom-item user-item" :class="{ 'icon-only': collapsed }">
            <div class="user-avatar">
              {{ (userStore.userInfo?.username || 'U').charAt(0).toUpperCase() }}
            </div>
            <template v-if="!collapsed">
              <div class="user-meta">
                <span class="user-name">{{ userStore.userInfo?.username || 'User' }}</span>
                <span class="user-role">Administrator</span>
              </div>
              <ChevronDown :size="12" :stroke-width="1.8" class="user-chevron" />
            </template>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">Sign out</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

    </el-aside>

    <!-- Main content area -->
    <el-main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>

  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import {
  Home, Wrench, FileText, BookOpen,
  Settings, ChevronDown,
  PanelLeftClose, PanelLeftOpen
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const activeMenu = computed(() => route.path)

const handleCommand = (command: string) => {
  if (command === 'logout') userStore.logout()
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ── 侧边栏 ───────────────────────────────────── */
.aside {
  background: #fff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  transition: width 0.22s ease;
  overflow: hidden;
}

/* Logo row */
.sidebar-logo {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px 0 18px;
  border-bottom: 1px solid #f5f5f5;
  flex-shrink: 0;
}

.collapsed .sidebar-logo {
  justify-content: center;
  padding: 0;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.5px;
  white-space: nowrap;
}

.collapse-btn {
  background: none;
  border: none;
  padding: 6px;
  cursor: pointer;
  color: #9ca3af;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.collapse-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

/* ── 菜单 ─────────────────────────────────────── */
.sidebar-menu {
  border: none;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: transparent;
  padding: 10px 10px 0;
}

.collapsed .sidebar-menu {
  padding: 10px 6px 0;
}

.menu-section-title {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  letter-spacing: 0.3px;
  padding: 14px 8px 5px;
}

/* Element Plus menu overrides */
:deep(.el-menu) { background: transparent; }
:deep(.el-menu--collapse) { width: 100%; }

:deep(.el-menu-item) {
  height: 38px;
  line-height: 38px;
  font-size: 13.5px;
  color: #6b7280;
  border-radius: 10px;
  margin-bottom: 2px;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  gap: 9px;
}

/* Lucide icons — no el-icon wrapper needed */
:deep(.el-menu-item svg) {
  flex-shrink: 0;
}

.aside:not(.collapsed) :deep(.el-menu-item) {
  padding: 0 12px !important;
}

.aside.collapsed :deep(.el-menu-item) {
  padding: 0 !important;
  justify-content: center;
  gap: 0;
}

:deep(.el-menu-item:hover) {
  background: #f5f5f7 !important;
  color: #111827;
}

:deep(.el-menu-item.is-active) {
  background: #f0f0f5 !important;
  color: #111827 !important;
  font-weight: 500;
}

/* Hide built-in tooltip title in collapsed mode */
:deep(.el-menu--collapse .el-menu-item .el-tooltip__trigger) {
  display: flex;
  justify-content: center;
}

/* ── 底部区域 ─────────────────────────────────── */
.sidebar-bottom {
  flex-shrink: 0;
  padding: 8px 10px 14px;
}

.collapsed .sidebar-bottom {
  padding: 8px 6px 14px;
}

.bottom-divider {
  height: 1px;
  background: #f0f0f0;
  margin-bottom: 8px;
}

.bottom-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 13.5px;
  white-space: nowrap;
  overflow: hidden;
}

.bottom-item.icon-only {
  justify-content: center;
  padding: 9px 0;
  gap: 0;
  width: 100%;
}

.bottom-item:hover { background: #f5f5f7; }

.settings-item { color: #6b7280; margin-bottom: 3px; }
.settings-item .icon { color: #9ca3af; flex-shrink: 0; }
.settings-item:hover { color: #111827; }
.settings-item:hover .icon { color: #374151; }

/* Full-width dropdown trigger so collapsed items can center */
.sidebar-bottom :deep(.el-dropdown) {
  display: block;
  width: 100%;
}

.user-item { color: #374151; }

.user-avatar {
  width: 26px;
  height: 26px;
  background: #111827;
  border-radius: 7px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  gap: 1px;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role { font-size: 11px; color: #9ca3af; }

.user-chevron { color: #9ca3af; flex-shrink: 0; }

/* ── 主内容 ───────────────────────────────────── */
.main-content {
  background: #f0f2f5;
  padding: 28px;
  overflow-y: auto;
  flex: 1;
}

/* ── 路由动画 ─────────────────────────────────── */
.fade-enter-active,
.fade-leave-active { transition: opacity 0.18s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }
</style>

<!-- Global Element Plus dropdown overrides (teleported to body, scoped styles won't reach) -->
<style>
.el-dropdown-menu {
  padding: 6px !important;
  border-radius: 12px !important;
  border: 1px solid #f0f0f0 !important;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08), 0 1px 4px rgba(0,0,0,0.04) !important;
  min-width: 160px !important;
}

.el-dropdown-menu__item {
  font-size: 13.5px !important;
  color: #374151 !important;
  border-radius: 8px !important;
  padding: 8px 12px !important;
  line-height: 1.4 !important;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif !important;
}

.el-dropdown-menu__item:hover {
  background-color: #f5f5f7 !important;
  color: #111827 !important;
}

/* 隐藏箭头 */
.el-popper__arrow {
  display: none !important;
}
</style>