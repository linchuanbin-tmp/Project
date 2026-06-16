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

        if (res.code !== 200) {
            ElMessage.error(res.message || 'Request failed')

            // 401: Token expired or not authenticated
            if (res.code === 401) {
                ElMessageBox.confirm('Session expired. Please log in again.', 'Notice', {
                    confirmButtonText: 'Log in',
                    cancelButtonText: 'Cancel',
                    type: 'warning'
                }).then(() => {
                    const userStore = useUserStore()
                    userStore.logout()
                    router.push('/login')
                })
            }
            return Promise.reject(new Error(res.message || 'Error'))
        }

        return res.data
    },
    (error) => {
        ElMessage.error(error.message || 'Network error')
        return Promise.reject(error)
    }
)

export default request