/**
 * WebSocket client wrapper for real-time task progress updates.
 */
class WebSocketClient {
    private ws: WebSocket | null = null
    private url: string = ''
    private reconnectAttempts: number = 0
    private maxReconnectAttempts: number = 3
    private reconnectTimer: number | null = null
    private listeners: Map<string, Function[]> = new Map()

    /**
     * Open a WebSocket connection.
     * @param url e.g. ws://localhost:8083/tool/ws?taskId=xxx
     */
    connect(url: string) {
        this.url = url
        // NOTE: do NOT reset reconnectAttempts here — attemptReconnect() calls connect()
        // and the counter must survive across calls to actually stop after maxAttempts.

        try {
            this.ws = new WebSocket(url)

            this.ws.onopen = () => {
                console.log('[WS] Connected')
                this.reconnectAttempts = 0
                this.emit('open', null)
            }

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data)
                    console.log('[WS] Message received:', data)
                    this.emit('message', data)
                } catch (e) {
                    this.emit('message', event.data)
                }
            }

            this.ws.onclose = () => {
                console.log('[WS] Connection closed')
                this.emit('close', null)
                this.attemptReconnect()
            }

            this.ws.onerror = (error) => {
                console.error('[WS] Error:', error)
                this.emit('error', error)
            }
        } catch (error) {
            console.error('[WS] Failed to connect:', error)
        }
    }

    /**
     * Send a message.
     */
    send(data: any) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const payload = typeof data === 'string' ? data : JSON.stringify(data)
            this.ws.send(payload)
        } else {
            console.error('[WS] Cannot send: not connected')
        }
    }

    /**
     * Close the connection and clear all listeners.
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
        this.listeners.clear()
    }

    /**
     * Register an event listener.
     */
    on(event: 'open' | 'message' | 'close' | 'error', callback: Function) {
        if (!this.listeners.has(event)) {
            this.listeners.set(event, [])
        }
        this.listeners.get(event)!.push(callback)
    }

    /**
     * Remove an event listener.
     */
    off(event: string, callback: Function) {
        const callbacks = this.listeners.get(event)
        if (callbacks) {
            const index = callbacks.indexOf(callback)
            if (index > -1) callbacks.splice(index, 1)
        }
    }

    /**
     * Remove all listeners for an event.
     */
    removeAllListeners(event: string) {
        this.listeners.delete(event)
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
            console.log(`[WS] Reconnecting (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
            this.reconnectTimer = window.setTimeout(() => {
                this.connect(this.url)
            }, 2000)
        }
    }
}

export const wsClient = new WebSocketClient()