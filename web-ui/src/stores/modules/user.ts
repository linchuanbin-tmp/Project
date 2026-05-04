import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@utils/request'
import router from '@router/index'

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
        userInfo.value = res.userInfo
        localStorage.setItem('token', res.token)
        return res
    }

    const logout = () => {
        token.value = ''
        userInfo.value = null
        localStorage.removeItem('token')
        router.push('/login')
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