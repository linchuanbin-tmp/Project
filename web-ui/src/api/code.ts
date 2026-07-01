import request from '@utils/request'

export interface CodeQueryRequest {
  question: string
}

export interface CodeGenerateRequest {
  question: string
}

export interface CodeValidateRequest {
  sql: string
}

// 自然语言转 SQL 并执行
export const executeCodeQuery = (data: CodeQueryRequest) => {
  return request.post('/code/query', data)
}

// 仅生成 SQL，不执行
export const generateSQLOnly = (data: CodeGenerateRequest) => {
  return request.post('/code/generate', data)
}

// 获取元数据列表
export const getCodeMetadata = () => {
  return request.get('/code/metadata')
}

// 刷新元数据
export const refreshCodeMetadata = () => {
  return request.post('/code/metadata/refresh')
}
