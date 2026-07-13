import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@utils/request'
import router from '@router/index'
import { ElNotification } from 'element-plus'

export const useUserStore = defineStore('user', () => {
    // State
    const token = ref(localStorage.getItem('token') || '')
    const userInfo = ref<any>(null)

    // Getters
    const isLoggedIn = computed(() => !!token.value)

    // Actions
    const login = async (username: string, password: string, code?: string) => {
        const payload: any = { username, password }
        if (code) {
            payload.code = code
            payload.password = ''  // 验证码模式下密码可空
        }
        const res: any = await request.post('/user/login', payload)
        token.value = res.token
        localStorage.setItem('token', res.token)
        await getUserInfo()

        ElNotification({
            title: 'Welcome Back',
            message: `Hello, ${userInfo.value?.realName || username}! You have successfully logged in.`,
            type: 'success',
            duration: 4500,
            position: 'top-right',
            showClose: true
        })

        return res
    }

    const logout = async (isExpired = false) => {
        if (!token.value) return
        const name = userInfo.value?.realName || userInfo.value?.username || 'User'

        if (!isExpired) {
            // Notify server to delete Redis session key (only if explicit user logout)
            try { await request.post('/user/logout') } catch (_) {}
        }

        token.value = ''
        userInfo.value = null
        localStorage.removeItem('token')
        router.push('/login')

        if (!isExpired) {
            ElNotification({
                title: 'Signed Out',
                message: `Goodbye, ${name}! You have signed out successfully.`,
                type: 'success',
                duration: 4500,
                position: 'top-right',
                showClose: true
            })
        }
    }

    const getUserInfo = async () => {
        const res: any = await request.get('/user/info')
        userInfo.value = res
        return res
    }

    return {
        token,
        userInfo,
        isLoggedIn,
        login,
        logout,
        getUserInfo
    }
})