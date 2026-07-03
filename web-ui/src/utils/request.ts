import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@stores/modules/user'
import router from '@router/index'

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
            ElMessage.error(res.message || 'Request failed')

            // 401: Token expired — only show dialog when inside the app, not on auth pages
            if (res.code === 401) {
                const publicPaths = ['/login', '/register']
                const isPublic = publicPaths.some(p => router.currentRoute.value.path.startsWith(p))
                if (!isPublic) {
                    ElMessageBox.confirm('Session expired. Please log in again.', 'Notice', {
                        confirmButtonText: 'Log in',
                        cancelButtonText: 'Cancel',
                        type: 'warning'
                    }).then(() => {
                        const userStore = useUserStore()
                        userStore.logout()
                    }).catch(() => {})
                }
            }
            return Promise.reject(new Error(res.message || 'Error'))
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
                ElMessage.error('Session expired. Please log in again.')
            }
        } else {
            ElMessage.error(error.message || 'Network error')
        }
        return Promise.reject(error)
    }
)

export default request