import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Task {
    id: string
    type: 'code' | 'rag' | 'tool'
    status: 'pending' | 'running' | 'completed' | 'failed'
    progress: number
    result?: any
    error?: string
    createTime: number
}

export const useTaskStore = defineStore('task', () => {
    const tasks = ref<Task[]>([])
    const currentTask = ref<Task | null>(null)

    const addTask = (task: Task) => {
        tasks.value.unshift(task)
        currentTask.value = task
    }

    const updateTask = (id: string, updates: Partial<Task>) => {
        const task = tasks.value.find(t => t.id === id)
        if (task) {
            Object.assign(task, updates)
            if (currentTask.value?.id === id) {
                Object.assign(currentTask.value, updates)
            }
        }
    }

    return {
        tasks,
        currentTask,
        addTask,
        updateTask
    }
})