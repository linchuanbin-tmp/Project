import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import { ElMessage } from 'element-plus'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@views/login/index.vue'),
        meta: { public: true }
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@views/register/index.vue'),
        meta: { public: true }
    },
    {
        path: '/',
        name: 'Layout',
        component: () => import('@layouts/MainLayout.vue'),
        redirect: '/dashboard',
        children: [
            {
                path: 'dashboard',
                name: 'Dashboard',
                component: () => import('@views/dashboard/index.vue'),
                meta: { title: 'Dashboard' }
            },
            {
                path: 'tool',
                name: 'ToolAgent',
                component: () => import('@views/tool/index.vue'),
                meta: { title: 'Tool Call' }
            },
            {
                path: 'code',
                name: 'CodeAgent',
                component: () => import('@views/code/index.vue'),
                meta: { title: 'SQL Generator' }
            },
            {
                path: 'rag',
                name: 'RagAgent',
                component: () => import('@views/rag/index.vue'),
                meta: { title: 'Knowledge Q&A' }
            },
            {
                path: 'admin/users',
                name: 'UserManagement',
                component: () => import('@views/admin/UserManagement.vue'),
                meta: { title: 'User Management', requiresRole: 'ROLE_ADMIN' }
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to, _from, next) => {
    const userStore = useUserStore()

    const isPublicRoute = to.meta.public === true

    // 1. If logged in but userInfo is empty (due to page refresh), fetch it.
    if (userStore.isLoggedIn && !userStore.userInfo) {
        try {
            await userStore.getUserInfo()
        } catch (error) {
            console.error('Failed to restore user info on refresh:', error)
            userStore.logout()
            ElMessage.error('Session expired. Please log in again.')
            return next('/login')
        }
    }

    // 2. Auth guard: if not logged in and requesting private page, redirect to login
    if (!isPublicRoute && !userStore.isLoggedIn) {
        next('/login')
    }
    // 3. Login guard: redirect to dashboard if already logged in
    else if (to.path === '/login' && userStore.isLoggedIn) {
        next('/')
    }
    // 4. Role guard: check if route requires a specific role
    else if (to.meta.requiresRole) {
        const requiredRole = to.meta.requiresRole as string
        const hasRole = userStore.userInfo?.roles?.includes(requiredRole)
        if (!hasRole) {
            ElMessage.warning('Access denied: Administrator privileges required.')
            next('/dashboard')
        } else {
            next()
        }
    }
    else {
        next()
    }
})

export default router