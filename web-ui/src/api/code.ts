const BASE = '/api/code'

async function post(path: string, body: any): Promise<any> {
    const token = localStorage.getItem('token')
        || localStorage.getItem('access_token')
        || sessionStorage.getItem('token')
        || ''

    const res = await fetch(`${BASE}${path}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(body)
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
}

async function get(path: string): Promise<any> {
    const token = localStorage.getItem('token')
        || localStorage.getItem('access_token')
        || sessionStorage.getItem('token')
        || ''

    const res = await fetch(`${BASE}${path}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
}

// 一键查询：自然语言 → SQL 生成 + 执行
export const codeQuery = (question: string) =>
    post('/query', { question })

// 仅生成 SQL（不执行）
export const codeGenerate = (question: string) =>
    post('/generate', { question })

// 校验 SQL 白名单
export const codeValidate = (sql: string) =>
    post('/validate', { sql })

// 执行已校验的 SQL
export const codeExecute = (sql: string) =>
    post('/execute', { sql })

// 获取元数据
export const codeMetadata = () => get('/metadata')

// 刷新元数据缓存
export const codeMetadataRefresh = () => post('/metadata/refresh', {})

// 健康检查
export const codeHealth = () => get('/health')
