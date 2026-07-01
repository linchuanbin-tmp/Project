import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@stores/modules/user'
import { ElMessage } from 'element-plus'

const routes = [
    {
        // Auth pages share a persistent background via AuthLayout
        path: '/',
        component: () => import('@layouts/AuthLayout.vue'),
        redirect: '/login',
        meta: { public: true },
        children: [
            {
                path: 'login',
                name: 'Login',
                component: () => import('@views/login/index.vue'),
                meta: { public: true }
            },
            {
                path: 'register',
                name: 'Register',
                component: () => import('@views/register/index.vue'),
                meta: { public: true }
            }
        ]
    },
    {
        path: '/app',
        name: 'Layout',
        component: () => import('@layouts/MainLayout.vue'),
        redirect: '/app/dashboard',
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
                meta: { title: 'Tool Agent' }
            },
            {
                path: 'code',
                name: 'CodeAgent',
                component: () => import('@views/code/index.vue'),
                meta: { title: 'Code Agent' }
            },
            {
                path: 'rag',
                name: 'RagAgent',
                component: () => import('@views/rag/index.vue'),
                meta: { title: 'RAG Agent' }
            },
            {
                path: 'admin/users',
                name: 'UserManagement',
                component: () => import('@views/admin/UserManagement.vue'),
                meta: { title: 'User Management', requiresRole: 'ROLE_ADMIN' }
            }
        ]
    },
    // Catch-all: redirect root to login
    {
        path: '/:pathMatch(.*)*',
        redirect: '/login'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to, _from, next) => {
    const userStore = useUserStore()

    const isPublicRoute = to.meta.public === true

    // 1. If logged in but userInfo is empty (page refresh), restore it silently
    if (userStore.isLoggedIn && !userStore.userInfo) {
        try {
            await userStore.getUserInfo()
        } catch {
            // Token invalid — clear and go to login without showing an error popup
            userStore.logout()
            return next('/login')
        }
    }

    // 2. Auth guard: unauthenticated user on private page → login
    if (!isPublicRoute && !userStore.isLoggedIn) {
        next('/login')
    }
    // 3. Already logged in hitting /login or /register → dashboard
    else if (isPublicRoute && userStore.isLoggedIn && (to.path === '/login' || to.path === '/register')) {
        next('/app/dashboard')
    }
    // 4. Role guard
    else if (to.meta.requiresRole) {
        const requiredRole = to.meta.requiresRole as string
        const hasRole = userStore.userInfo?.roles?.includes(requiredRole)
        if (!hasRole) {
            ElMessage.warning('Access denied: Administrator privileges required.')
            next('/app/dashboard')
        } else {
            next()
        }
    }
    else {
        next()
    }
})

export default router