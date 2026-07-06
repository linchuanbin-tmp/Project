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
    const login = async (username: string, password: string) => {
        const res: any = await request.post('/user/login', { username, password })
        token.value = res.token
        localStorage.setItem('token', res.token)
        await getUserInfo()

        ElNotification({
            title: 'Welcome Back',
            message: `Hello, ${userInfo.value?.realName || username}! You have successfully logged in.`,
            type: 'success',
            duration: 4500,
            position: 'top-right'
        })

        return res
    }

    const logout = () => {
        const name = userInfo.value?.realName || userInfo.value?.username || 'User'
        token.value = ''
        userInfo.value = null
        localStorage.removeItem('token')
        router.push('/login')

        ElNotification({
            title: 'Signed Out',
            message: `Goodbye, ${name}! You have signed out successfully.`,
            type: 'success',
            duration: 4500,
            position: 'top-right'
        })
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