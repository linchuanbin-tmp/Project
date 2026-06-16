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
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, _from, next) => {
    const userStore = useUserStore()

    const isPublicRoute = to.meta.public === true

    if (!isPublicRoute && !userStore.isLoggedIn) {
        next('/login')
    } else if (to.path === '/login' && userStore.isLoggedIn) {
        next('/')
    } else {
        next()
    }
})

export default router