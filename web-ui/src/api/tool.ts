import request from '@utils/request'

// Fetch available meeting rooms
export const getMeetingRooms = (params?: { startTime?: string; endTime?: string; capacity?: number }) => {
    return request.get('/tool/meeting-rooms', { params })
}

// Check schedule conflict for given attendees
export const checkScheduleConflict = (data: {
    startTime: string
    endTime: string
    attendees: string[]
}) => {
    return request.post('/tool/check-conflict', data)
}

// Plan a route between two locations
export const planRoute = (params: {
    from: string
    to: string
    mode?: string
}) => {
    return request.get('/tool/route', { params })
}

// Unified tool execution endpoint (used by the AI assistant)
export const executeTool = (data: {
    toolType: string
    parameters: Record<string, any>
    naturalLanguage?: string
}) => {
    return request.post('/tool/execute', data)
}