import request from '@utils/request'

// 获取会议室列表
export const getMeetingRooms = (params?: { date?: string; capacity?: number }) => {
    return request.get('/tool/meeting-rooms', { params })
}

// 检查日程冲突
export const checkScheduleConflict = (data: {
    startTime: string
    endTime: string
    attendees: string[]
}) => {
    return request.post('/tool/check-conflict', data)
}

// 规划路线
export const planRoute = (params: {
    from: string
    to: string
    mode?: string
}) => {
    return request.get('/tool/route', { params })
}

// 统一工具执行接口（AI 助手用）
export const executeTool = (data: {
    toolType: string
    parameters: Record<string, any>
    naturalLanguage?: string
}) => {
    return request.post('/tool/execute', data)
}