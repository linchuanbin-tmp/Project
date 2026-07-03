import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@stores/modules/user'
import router from '@router/index'
import i18n from '@/i18n'

const request = axios.create({
    baseURL: '/api',  // proxied via Vite config
    timeout: 60000,   // 60s timeout for potentially slow AI requests
    headers: {
        'Content-Type': 'application/json'
    }
})

// Request interceptor
request.interceptors.request.use(
    (config) => {
        const userStore = useUserStore()
        if (userStore.token) {
            config.headers.Authorization = `Bearer ${userStore.token}`
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// Response interceptor
request.interceptors.response.use(
    (response) => {
        const res = response.data

        // Check if response is wrapped in a standard Result class
        if (res.code !== undefined && res.code !== 200) {
            ElMessage.error(res.message || i18n.global.t('request.failed'))

            // 401: Token expired — only show dialog when inside the app, not on auth pages
            if (res.code === 401) {
                const publicPaths = ['/login', '/register']
                const isPublic = publicPaths.some(p => router.currentRoute.value.path.startsWith(p))
                if (!isPublic) {
                    ElMessageBox.confirm(
                        i18n.global.t('request.sessionExpired'),
                        i18n.global.t('request.tip'),
                        {
                            confirmButtonText: i18n.global.t('request.relogin'),
                            cancelButtonText: i18n.global.t('request.cancel'),
                            type: 'warning'
                        }
                    ).then(() => {
                        const userStore = useUserStore()
                        userStore.logout()
                    }).catch(() => {})
                }
            }
            return Promise.reject(new Error(res.message || i18n.global.t('request.failed')))
        }

        // Return unwrapped data if wrapped, otherwise return raw payload
        return res.code !== undefined ? res.data : res
    },
    (error) => {
        const response = error.response
        if (response && response.status === 401) {
            const publicPaths = ['/login', '/register']
            const isPublic = publicPaths.some(p => router.currentRoute.value.path.startsWith(p))
            if (!isPublic) {
                const userStore = useUserStore()
                userStore.logout()
                ElMessage.error(i18n.global.t('request.sessionExpired'))
            }
        } else {
            ElMessage.error(error.message || i18n.global.t('request.networkError'))
        }
        return Promise.reject(error)
    }
)

export default request
