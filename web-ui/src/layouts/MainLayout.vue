<template>
  <div class="layout-wrapper">
    <!-- Mobile Header -->
    <div class="mobile-header">
      <button class="hamburger-btn" @click="mobileOpen = !mobileOpen">
        <Menu :size="20" :stroke-width="1.8" />
      </button>
      <span class="mobile-logo-text" @click="router.push('/app/dashboard')">BankAgent</span>
      <div class="mobile-spacer"></div>
    </div>

    <!-- Backdrop for mobile drawer -->
    <div v-show="mobileOpen" class="mobile-backdrop" @click="mobileOpen = false"></div>

    <el-container class="main-layout">

      <!-- Sidebar -->
      <el-aside :width="collapsed ? '56px' : '230px'" class="aside" :class="{ collapsed, 'mobile-active': mobileOpen }">

      <!-- Logo & collapse toggle -->
      <div class="sidebar-logo">
        <span v-if="!collapsed" class="logo-text" @click="router.push('/app/dashboard')">BankAgent</span>
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
        <el-menu-item index="/app/dashboard">
          <template #title>Dashboard</template>
          <Home :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/my-schedules">
          <template #title>My Schedules</template>
          <CalendarDays :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/notification">
          <template #title>
            <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
              <span>Messages</span>
              <span v-if="unreadCount > 0" class="badge-dot">{{ unreadCount }}</span>
            </div>
          </template>
          <Bell :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <div v-if="!collapsed" class="menu-section-title">Agent services</div>

        <el-menu-item index="/app/tool">
          <template #title>Tool Agent</template>
          <Wrench :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/code">
          <template #title>Code Agent</template>
          <FileText :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/rag">
          <template #title>RAG Agent</template>
          <BookOpen :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <div v-if="!collapsed" class="menu-section-title">Knowledge base</div>
        <el-menu-item index="/app/dept-docs">
          <template #title>Documents</template>
          <FolderOpen :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <!-- Department section -->
        <template v-if="userStore.userInfo?.roles?.includes('ROLE_DEPT_ADMIN') || userStore.userInfo?.roles?.includes('ROLE_ADMIN')">
          <div v-if="!collapsed" class="menu-section-title">Department</div>
          <el-menu-item index="/app/admin/my-dept">
            <template #title>{{ userStore.userInfo?.roles?.includes('ROLE_ADMIN') ? 'Dept Management' : 'My Department' }}</template>
            <Briefcase :size="16" :stroke-width="1.6" />
          </el-menu-item>
        </template>

        <!-- Admin section -->
        <template v-if="userStore.userInfo?.roles?.includes('ROLE_ADMIN')">
          <div v-if="!collapsed" class="menu-section-title">Administration</div>
          <el-menu-item index="/app/admin/users">
            <template #title>User Management</template>
            <Users :size="16" :stroke-width="1.6" />
          </el-menu-item>
          <el-menu-item index="/app/admin/resources">
            <template #title>Resource Management</template>
            <Database :size="16" :stroke-width="1.6" />
          </el-menu-item>
        </template>
      </el-menu>

      <!-- Bottom: settings & user -->
      <div class="sidebar-bottom">
        <div class="bottom-divider"></div>

        <!-- Settings -->
        <div
          class="bottom-item settings-item"
          :class="{ 'icon-only': collapsed, 'settings-active': route.path === '/app/settings' }"
          @click="router.push('/app/settings')"
        >
          <Settings :size="16" :stroke-width="1.6" class="icon" />
          <span v-if="!collapsed">Settings</span>
        </div>

        <!-- Language switcher -->
        <div class="bottom-lang" :class="{ 'icon-only': collapsed }">
          <LangSwitcher />
        </div>

        <!-- User info & logout -->
        <el-dropdown @command="handleCommand" trigger="click" placement="top-start">
          <div class="bottom-item user-item" :class="{ 'icon-only': collapsed }">
            <div class="user-avatar">
              {{ (userStore.userInfo?.realName === '管理员' ? 'Administrator' : (userStore.userInfo?.realName || userStore.userInfo?.username || 'U')).charAt(0).toUpperCase() }}
            </div>
            <template v-if="!collapsed">
              <div class="user-meta">
                <span class="user-name">
                  {{ userStore.userInfo?.realName === '管理员' ? 'Administrator' : (userStore.userInfo?.realName || userStore.userInfo?.username || 'User') }}
                </span>
                <span class="user-role">
                  {{ userStore.userInfo?.roles?.includes('ROLE_ADMIN') ? 'Administrator' : (userStore.userInfo?.roles?.includes('ROLE_DEPT_ADMIN') ? 'Dept Admin' : 'Employee') }}
                </span>
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
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import { getUnreadCount } from '@/api/notification'
import LangSwitcher from '@components/LangSwitcher.vue'
import {
  Home, Wrench, FileText, BookOpen,
  Settings, ChevronDown, Users, Database,
  PanelLeftClose, PanelLeftOpen, Menu, CalendarDays, Bell, Briefcase, FolderOpen
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const mobileOpen = ref(false)
const activeMenu = computed(() => route.path)

const unreadCount = ref(0)
let timer: any = null

const fetchUnreadCount = async () => {
  if (userStore.isLoggedIn) {
    try {
      const count = await getUnreadCount()
      unreadCount.value = typeof count === 'number' ? count : (count as any) || 0
    } catch (e) {
      console.error('Failed to fetch unread notification count', e)
    }
  }
}

// Auto-close mobile sidebar drawer upon navigation
watch(() => route.path, () => {
  mobileOpen.value = false
  fetchUnreadCount()
})

onMounted(() => {
  fetchUnreadCount()
  timer = setInterval(fetchUnreadCount, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

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
  cursor: pointer;
  transition: opacity 0.15s;
}

.logo-text:hover {
  opacity: 0.8;
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
.settings-active { background: #f0f0f5 !important; color: #111827 !important; font-weight: 500; }
.settings-active .icon { color: #374151 !important; }

/* Language switcher in sidebar bottom */
.bottom-lang {
  display: flex;
  justify-content: flex-start;
  padding: 10px 10px;
  margin-bottom: 3px;
}

.bottom-lang.icon-only {
  justify-content: center;
  padding: 10px 0;
}

.bottom-lang :deep(.el-radio-group) {
  display: flex;
}

.bottom-lang :deep(.el-radio-button__inner) {
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 0;
  border-color: #e5e7eb;
  background: #f9fafb;
  color: #6b7280;
  box-shadow: none;
}

.bottom-lang :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #111827;
  border-color: #111827;
  color: #fff;
  box-shadow: none;
}

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

/* ── 响应式适配 (移动端) ────────────────────────── */
.layout-wrapper {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
}

.mobile-header {
  display: none;
}

@media (max-width: 768px) {
  .main-layout {
    height: calc(100vh - 56px);
    flex-direction: column;
  }

  .mobile-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 56px;
    background: #fff;
    border-bottom: 1px solid #f0f0f0;
    padding: 0 16px;
    flex-shrink: 0;
  }

  .hamburger-btn {
    background: none;
    border: none;
    padding: 6px;
    cursor: pointer;
    color: #4b5563;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .hamburger-btn:hover {
    background: #f3f4f6;
  }

  .mobile-logo-text {
    font-size: 17px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.5px;
    cursor: pointer;
  }

  .mobile-spacer {
    width: 32px;
  }

  .aside {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 1000;
    width: 240px !important;
    transform: translateX(-100%);
    transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 8px 0 24px rgba(0,0,0,0.08);
  }

  .aside.mobile-active {
    transform: translateX(0);
  }

  .mobile-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.35);
    backdrop-filter: blur(1.5px);
    z-index: 999;
  }

  .main-content {
    padding: 16px;
  }

  /* Hide sidebar toggle collapse button on mobile */
  .collapse-btn {
    display: none !important;
  }
}

.badge-dot {
  background-color: #ef4444;
  color: #ffffff;
  border-radius: 9999px;
  font-size: 11px;
  height: 18px;
  min-width: 18px;
  line-height: 18px;
  text-align: center;
  padding: 0 5px;
  font-weight: 600;
  display: inline-block;
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