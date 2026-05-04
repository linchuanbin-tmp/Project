import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@stores/modules/user'
import router from '@router/index'

// 创建axios实例
const request = axios.create({
    baseURL: '/api',  // 对应vite配置的代理
    timeout: 60000,   // 60秒超时（AI请求可能较慢）
    headers: {
        'Content-Type': 'application/json'
    }
})

// 请求拦截器
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

// 响应拦截器
request.interceptors.response.use(
    (response) => {
        const res = response.data

        // 如果后端返回的code不为200，视为错误
        if (res.code !== 200) {
            ElMessage.error(res.message || '请求失败')

            // 401: Token过期或未登录
            if (res.code === 401) {
                ElMessageBox.confirm('登录状态已过期，是否重新登录？', '提示', {
                    confirmButtonText: '重新登录',
                    cancelButtonText: '取消',
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
        ElMessage.error(error.message || '网络错误')
        return Promise.reject(error)
    }
)

export default request