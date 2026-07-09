import request from '@utils/request'

export interface TaskRecord {
    id: number
    taskType: string
    status: 'INIT' | 'RUNNING' | 'SUCCESS' | 'FAIL'
    userId: number
    input: string
    output: string | null
    errorMsg: string | null
    attemptCount: number
    elapsedTime: number | null
    createdAt: string
    updatedAt: string
}

export interface TaskSubmitResult {
    id: number
    status: string
}

// 提交异步任务 → POST /api/task/submit
export const submitTask = (data: { taskType: string; input: string }) =>
    request.post<any, any>('/task/submit', data)

// 查询单个任务状态/结果 → GET /api/task/{id}
export const getTask = (id: number) =>
    request.get<any, any>(`/task/${id}`)

// 查询当前用户任务历史 → GET /api/task/list
export const getMyTasks = () =>
    request.get<any, any>('/task/list')

// 管理员查询全平台任务 → GET /api/task/list/all
export const getAllTasks = () =>
    request.get<any, any>('/task/list/all')
