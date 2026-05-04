/**
 * WebSocket 客户端封装
 * 用于 Tool Agent 任务进度实时推送
 */
class WebSocketClient {
    private ws: WebSocket | null = null
    private url: string = ''
    private reconnectAttempts: number = 0
    private maxReconnectAttempts: number = 3
    private reconnectTimer: number | null = null
    private listeners: Map<string, Function[]> = new Map()

    /**
     * 建立连接
     * @param url WebSocket 地址，例如：ws://localhost:8083/tool/ws?taskId=xxx
     */
    connect(url: string) {
        this.url = url
        this.reconnectAttempts = 0

        try {
            this.ws = new WebSocket(url)

            this.ws.onopen = () => {
                console.log('✅ WebSocket 连接成功')
                this.reconnectAttempts = 0
                this.emit('open', null)
            }

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data)
                    console.log('📨 WebSocket 收到消息:', data)
                    this.emit('message', data)
                } catch (e) {
                    this.emit('message', event.data)
                }
            }

            this.ws.onclose = () => {
                console.log('❌ WebSocket 连接关闭')
                this.emit('close', null)
                this.attemptReconnect()
            }

            this.ws.onerror = (error) => {
                console.error('💥 WebSocket 错误', error)
                this.emit('error', error)
            }
        } catch (error) {
            console.error('WebSocket 连接失败', error)
        }
    }

    /**
     * 发送消息
     */
    send(data: any) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const payload = typeof data === 'string' ? data : JSON.stringify(data)
            this.ws.send(payload)
        } else {
            console.error('WebSocket 未连接，无法发送消息')
        }
    }

    /**
     * 关闭连接
     */
    close() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }
        if (this.ws) {
            this.ws.close()
            this.ws = null
        }
    }

    /**
     * 注册事件监听
     */
    on(event: 'open' | 'message' | 'close' | 'error', callback: Function) {
        if (!this.listeners.has(event)) {
            this.listeners.set(event, [])
        }
        this.listeners.get(event)!.push(callback)
    }

    /**
     * 移除事件监听
     */
    off(event: string, callback: Function) {
        const callbacks = this.listeners.get(event)
        if (callbacks) {
            const index = callbacks.indexOf(callback)
            if (index > -1) callbacks.splice(index, 1)
        }
    }

    private emit(event: string, data: any) {
        const callbacks = this.listeners.get(event)
        if (callbacks) {
            callbacks.forEach(cb => cb(data))
        }
    }

    private attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++
            console.log(`🔄 WebSocket 尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
            this.reconnectTimer = window.setTimeout(() => {
                this.connect(this.url)
            }, 2000)
        }
    }
}

// 单例导出
export const wsClient = new WebSocketClient()