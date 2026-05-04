import { createPinia } from 'pinia'
import { useUserStore } from './modules/user'
import { useTaskStore } from './modules/task'

const pinia = createPinia()

export { useUserStore, useTaskStore }
export default pinia