import request from '@utils/request'

// Department Admin: Fetch department members, supports passing deptId for admin
export const getDeptMembers = (deptId?: number | null) => {
    return request.get('/user/dept-admin/members', { params: { deptId } })
}

// Department Admin: Fetch candidates (users with no department)
export const getDeptCandidates = () => {
    return request.get('/user/dept-admin/candidates')
}

// Department Admin: Add members to department, supports passing deptId for admin
export const addDeptMembers = (userIds: number[], deptId?: number | null) => {
    return request.post('/user/dept-admin/add-members', { userIds, deptId })
}

// Department Admin: Remove single member from department, supports passing deptId for admin
export const removeDeptMember = (userId: number, deptId?: number | null) => {
    return request.post('/user/dept-admin/remove-member', { userId, deptId })
}

// System Admin: Update user's department
export const updateUserDept = (data: { userId: number; deptId: number | null }) => {
    return request.put('/admin/user/dept', data)
}

// System Admin: Update user's clearance level
export const updateUserClearance = (data: { userId: number; clearanceLevel: number }) => {
    return request.put('/admin/user/clearance', data)
}

// User: Fetch department documents
export const getDeptDocuments = () => {
    return request.get('/user/document/list')
}

// Fetch all department entities dynamically from database
export const getDepartmentsList = () => {
    return request.get('/user/dept/list')
}

// System Admin: Create new department
export const createDepartment = (data: { deptName: string; description: string }) => {
    return request.post('/admin/dept', data)
}

// System Admin: Update department details
export const updateDepartment = (data: { id: number; deptName: string; description: string }) => {
    return request.put('/admin/dept', data)
}

// System Admin: Delete department
export const deleteDepartment = (id: number) => {
    return request.delete(`/admin/dept/${id}`)
}

// User Document Management
export const createDocument = (data: { title: string; content: string; securityLevel: number; deptId: number | null }) => {
    return request.post('/user/document/create', data)
}

export const updateDocument = (data: { id: number; title: string; content: string; securityLevel: number; deptId: number | null }) => {
    return request.put('/user/document/update', data)
}

export const deleteDocument = (id: number) => {
    return request.delete(`/user/document/delete/${id}`)
}

