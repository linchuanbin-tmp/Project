import request from '@utils/request'

// Fetch user's notification list
export const getNotifications = (params?: { status?: number; notifyType?: string }) => {
    return request.get('/user/notification/list', { params })
}

// Fetch unread notification count
export const getUnreadCount = () => {
    return request.get('/user/notification/unread-count')
}

// Send a custom message or notification
export const sendNotification = (data: {
    receiverId: number
    title: string
    content: string
    notifyType?: string
    payload?: string
}) => {
    return request.post('/user/notification/send', data)
}

// Mark a single notification as read
export const markAsRead = (id: number) => {
    return request.put(`/user/notification/read/${id}`)
}

// Submit action on a pending notification (e.g. approve/deny)
export const handleAction = (data: {
    notificationId: number
    action: 'APPROVE' | 'DENY'
    opinion?: string
}) => {
    return request.post('/user/notification/action', data)
}

// Fetch all system users for recipient selection
export const getUsers = () => {
    return request.get('/user/list')
}
