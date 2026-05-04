import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@stores/modules/user'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@views/login/index.vue'),
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
                meta: { title: '首页' }
            },
            {
                path: 'tool',
                name: 'ToolAgent',
                component: () => import('@views/tool/index.vue'),
                meta: { title: '工具调用' }
            },
            {
                path: 'code',
                name: 'CodeAgent',
                component: () => import('@views/code/index.vue'),
                meta: { title: 'SQL生成' }
            },
            {
                path: 'rag',
                name: 'RagAgent',
                component: () => import('@views/rag/index.vue'),
                meta: { title: '知识问答' }
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
    const userStore = useUserStore()

    // 检查是否需要登录（没有标记 public 的都需要登录）
    const isPublicRoute = to.meta.public === true

    if (!isPublicRoute && !userStore.isLoggedIn) {
        next('/login')
    } else if (to.path === '/login' && userStore.isLoggedIn) {
        // 已登录用户访问登录页，重定向到首页
        next('/')
    } else {
        next()
    }
})

export default router