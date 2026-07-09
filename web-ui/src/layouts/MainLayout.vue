<template>
  <div class="layout-wrapper">
    <!-- Mobile Header -->
    <div class="mobile-header">
      <button class="hamburger-btn" @click="mobileOpen = !mobileOpen">
        <Menu :size="20" :stroke-width="1.8" />
      </button>
      <span class="mobile-logo-text" @click="router.push('/app/dashboard')">{{ $t('menu.logo') }}</span>
      <div class="mobile-spacer"></div>
    </div>

    <!-- Backdrop for mobile drawer -->
    <div v-show="mobileOpen" class="mobile-backdrop" @click="mobileOpen = false"></div>

    <el-container class="main-layout">

      <!-- Sidebar -->
      <el-aside :width="collapsed ? '56px' : '230px'" class="aside" :class="{ collapsed, 'mobile-active': mobileOpen }">

      <!-- Logo & collapse toggle -->
      <div class="sidebar-logo">
        <span v-if="!collapsed" class="logo-text" @click="router.push('/app/dashboard')">{{ $t('menu.logo') }}</span>
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
        <div v-if="!collapsed" class="menu-section-title">{{ $t('menu.workspace') }}</div>

        <el-menu-item index="/app/dashboard">
          <template #title>{{ $t('menu.dashboard') }}</template>
          <Home :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/my-tasks">
          <template #title>{{ $t('menu.myTasks') }}</template>
          <ClipboardList :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/my-schedules">
          <template #title>{{ $t('menu.mySchedules') }}</template>
          <CalendarDays :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/notification">
          <template #title>
            <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
              <span>{{ $t('menu.messages') }}</span>
              <span v-if="unreadCount > 0" class="badge-dot">{{ unreadCount }}</span>
            </div>
          </template>
          <Bell :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <div v-if="!collapsed" class="menu-section-title">{{ $t('menu.agentServices') }}</div>

        <el-menu-item index="/app/tool">
          <template #title>{{ $t('menu.toolAgent') }}</template>
          <Wrench :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/code">
          <template #title>{{ $t('menu.codeAgent') }}</template>
          <FileText :size="16" :stroke-width="1.6" />
        </el-menu-item>
        <el-menu-item index="/app/rag">
          <template #title>{{ $t('menu.ragAgent') }}</template>
          <BookOpen :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <div v-if="!collapsed" class="menu-section-title">{{ $t('menu.knowledgeBase') }}</div>
        <el-menu-item index="/app/dept-docs">
          <template #title>{{ $t('menu.documents') }}</template>
          <FolderOpen :size="16" :stroke-width="1.6" />
        </el-menu-item>

        <!-- Management section -->
        <template v-if="userStore.userInfo?.roles?.includes('ROLE_DEPT_ADMIN') || userStore.userInfo?.roles?.includes('ROLE_ADMIN')">
          <div v-if="!collapsed" class="menu-section-title">{{ $t('menu.management') }}</div>
          <el-menu-item index="/app/admin/my-dept">
            <template #title>{{ userStore.userInfo?.roles?.includes('ROLE_ADMIN') ? $t('menu.deptManagement') : $t('menu.myDept') }}</template>
            <Briefcase :size="16" :stroke-width="1.6" />
          </el-menu-item>
        </template>

        <template v-if="userStore.userInfo?.roles?.includes('ROLE_ADMIN')">
          <el-menu-item index="/app/admin/users">
            <template #title>{{ $t('menu.userManagement') }}</template>
            <Users :size="16" :stroke-width="1.6" />
          </el-menu-item>
          <el-menu-item index="/app/admin/resources">
            <template #title>{{ $t('menu.resourceManagement') }}</template>
            <Database :size="16" :stroke-width="1.6" />
          </el-menu-item>
          <el-menu-item index="/app/admin/task-center">
            <template #title>{{ $t('menu.taskCenter') }}</template>
            <LayoutDashboard :size="16" :stroke-width="1.6" />
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
          <span v-if="!collapsed">{{ $t('menu.settings') }}</span>
        </div>



        <!-- User info & logout -->
        <el-dropdown @command="handleCommand" trigger="click" placement="top-start">
          <div class="bottom-item user-item" :class="{ 'icon-only': collapsed }">
            <div class="user-avatar">
              {{ (userStore.userInfo?.realName === '管理员' ? $t('menu.administrator') : (userStore.userInfo?.realName || userStore.userInfo?.username || 'U')).charAt(0).toUpperCase() }}
            </div>
            <template v-if="!collapsed">
              <div class="user-meta">
                <span class="user-name">
                  {{ userStore.userInfo?.realName === '管理员' ? $t('menu.administrator') : (userStore.userInfo?.realName || userStore.userInfo?.username || 'User') }}
                </span>
                <span class="user-role">
                  {{ userStore.userInfo?.roles?.includes('ROLE_ADMIN') ? $t('menu.administrator') : (userStore.userInfo?.roles?.includes('ROLE_DEPT_ADMIN') ? $t('menu.deptAdmin') : $t('menu.employee')) }}
                </span>
              </div>
              <ChevronDown :size="12" :stroke-width="1.8" class="user-chevron" />
            </template>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">{{ $t('menu.logout') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

    </el-aside>

    <!-- Main content area -->
    <el-main class="main-content">
      <!-- Tabs Bar -->
      <transition-group name="tab-fade" tag="div" class="layout-tabs-bar" v-if="openTabs.length > 0">
        <div 
          v-for="tab in openTabs" 
          :key="tab.path" 
          class="tab-item"
          :class="{ active: route.path === tab.path, 'has-close': openTabs.length > 1 }"
          @click="switchTab(tab.path)"
        >
          <span class="tab-title">{{ $t(`menu.${tab.metaKey}`) || tab.title }}</span>
          <span 
            v-if="openTabs.length > 1" 
            class="tab-close-icon" 
            @click.stop="closeTab(tab.path)"
          >
            <X :size="12" :stroke-width="2.2" />
          </span>
        </div>
      </transition-group>
      
      <!-- Content View -->
      <div class="main-content-view">
        <router-view v-slot="{ Component, route: currentRoute }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive>
              <component :is="Component" :key="currentRoute.fullPath + '-' + (cacheKeys[currentRoute.path] || 0)" />
            </keep-alive>
          </transition>
        </router-view>
      </div>
    </el-main>

  </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import { getUnreadCount } from '@/api/notification'

import {
  Home, Wrench, FileText, BookOpen,
  Settings, ChevronDown, Users, Database,
  PanelLeftClose, PanelLeftOpen, Menu, CalendarDays, Bell, Briefcase, FolderOpen, X,
  ClipboardList, LayoutDashboard
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const mobileOpen = ref(false)
const activeMenu = computed(() => route.path)

// Multi-Tab management
const openTabs = ref<Array<{ path: string; title: string; metaKey: string }>>([])
const cacheKeys = ref<Record<string, number>>({})

const addTab = (path: string, title: string, metaKey: string) => {
  if (!openTabs.value.some(t => t.path === path)) {
    openTabs.value.push({ path, title, metaKey })
  }
}

const switchTab = (path: string) => {
  router.push(path)
}

const closeTab = (path: string) => {
  const index = openTabs.value.findIndex(t => t.path === path)
  if (index === -1) return

  // Increment counter key to evict keepalive cached view state
  cacheKeys.value[path] = (cacheKeys.value[path] || 0) + 1

  openTabs.value.splice(index, 1)

  // Switch active page to next remaining tab
  if (route.path === path) {
    const nextTab = openTabs.value[index] || openTabs.value[index - 1]
    if (nextTab) {
      router.push(nextTab.path)
    } else {
      router.push('/app/dashboard')
    }
  }
}

watch(
  () => route.path,
  (newPath) => {
    if (newPath.startsWith('/app/')) {
      const title = (route.meta.title as string) || 'Page'
      let metaKey = 'dashboard'
      if (newPath === '/app/my-schedules') metaKey = 'mySchedules'
      else if (newPath === '/app/notification') metaKey = 'messages'
      else if (newPath === '/app/tool') metaKey = 'toolAgent'
      else if (newPath === '/app/code') metaKey = 'codeAgent'
      else if (newPath === '/app/rag') metaKey = 'ragAgent'
      else if (newPath === '/app/dept-docs') metaKey = 'documents'
      else if (newPath === '/app/admin/my-dept') {
        metaKey = userStore.userInfo?.roles?.includes('ROLE_ADMIN') ? 'deptManagement' : 'myDept'
      }
      else if (newPath === '/app/admin/users') metaKey = 'userManagement'
      else if (newPath === '/app/admin/resources') metaKey = 'resourceManagement'
      else if (newPath === '/app/admin/task-center') metaKey = 'taskCenter'
      else if (newPath === '/app/my-tasks') metaKey = 'myTasks'
      else if (newPath === '/app/settings') metaKey = 'settings'

      addTab(newPath, title, metaKey)
    }
  },
  { immediate: true }
)

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
  font-weight: 400;
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
  background: #f8fafc;
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden !important; /* Prevents outer container scrollbar */
  flex: 1;
}

.main-content-view {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 24px 24px;
  position: relative;
  z-index: 1;
}

.main-content-view > * {
  max-width: 100% !important;
}

/* ── 标签页选项卡样式 (Discrete Floating Card Style) ─────────────────────────── */
.layout-tabs-bar {
  display: flex;
  align-items: center;
  gap: 8px; /* Generous gap to separate tabs clearly */
  padding: 12px 24px 6px 24px;
  background: #f8fafc; /* Matches off-white workspace background for zero-divider layout */
  flex-shrink: 0;
  overflow-x: auto;
  white-space: nowrap;
  height: 54px;
  box-sizing: border-box;
}

/* Hide scrollbars for the tabs bar itself */
.layout-tabs-bar::-webkit-scrollbar {
  display: none;
}
.layout-tabs-bar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px; /* Balanced gap between text and close icon */
  height: 36px;
  padding: 0 16px; /* Symmetric padding when close button is absent */
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
  user-select: none;
  max-width: 180px;
  min-width: 80px; /* Prevent shrinking too small */
  flex-shrink: 1; /* Allow sharing space when tabs pile up */
  overflow: hidden;
  box-sizing: border-box;
}

.tab-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0; /* Enable flex-child text truncation */
}

.tab-item.has-close {
  padding-left: 16px;
  padding-right: 8px; /* Tighter right side space to visually balance the close icon circle */
}

.tab-item:hover {
  background: #edf2f7;
  border-color: #cbd5e1;
  color: #334155;
}

.tab-item.active {
  background: #ffffff;
  color: #0f172a;
  font-weight: 600;
  border: 1.5px solid #0f172a;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.tab-close-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #94a3b8;
  transition: all 0.1s;
  flex-shrink: 0; /* NEVER shrink or deform the close button */
}

.tab-close-icon:hover {
  background: #cbd5e1;
  color: #1e293b;
}

/* Tab closing/adding animations */
.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.tab-fade-enter-from,
.tab-fade-leave-to {
  opacity: 0;
  transform: translateY(4px) scale(0.95);
  max-width: 0;
  padding-left: 0 !important;
  padding-right: 0 !important;
  margin-right: -4px;
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

/* ── 路由选项卡切面动画 ─────────────────────────── */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.15s cubic-bezier(0.16, 1, 0.3, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
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