/**
 * WebSocket 客户端类
 */
const WS_CLIENT_VERSION = '2026-04-26-2';

class GameWebSocketClient {
    constructor(url = null) {
        this.url = url || this.getWebSocketUrl();
        this.ws = null;
        this.handlers = {};
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 2000;
    }

    getWebSocketUrl() {
        const configuredUrl = window.BIGTWO_WS_URL || new URLSearchParams(window.location.search).get('wsUrl');
        if (configuredUrl) {
            return configuredUrl;
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const rawHostname = window.location.hostname || 'localhost';
        const hostname = rawHostname.includes(':') && !rawHostname.startsWith('[')
            ? `[${rawHostname}]`
            : rawHostname;
        const port = window.location.port || window.BIGTWO_BACKEND_PORT || '8080';
        return `${protocol}//${hostname}:${port}/api/ws/game`;
    }

    connect() {
        return new Promise((resolve, reject) => {
            try {
                console.log(`[WS ${WS_CLIENT_VERSION}] Connecting WebSocket:`, this.url);
                this.ws = new WebSocket(this.url);

                this.ws.onopen = () => {
                    console.log('WebSocket connected');
                    this.reconnectAttempts = 0;
                    this.emit('connected');
                    resolve();
                };

                this.ws.onmessage = (event) => {
                    try {
                        const message = JSON.parse(event.data);
                        console.log('Message received:', message);
                        this.emit(message.type, message);
                    } catch (e) {
                        console.error('Failed to parse message:', event.data);
                    }
                };

                this.ws.onerror = (error) => {
                    const details = {
                        url: this.url,
                        readyState: this.ws ? this.ws.readyState : -1
                    };
                    console.error('WebSocket error:', details, error);
                    this.emit('error', details);
                    if (this.reconnectAttempts === 0) {
                        reject(error);
                    }
                };

                this.ws.onclose = (event) => {
                    console.warn('WebSocket closed:', {
                        url: this.url,
                        code: event.code,
                        reason: event.reason,
                        wasClean: event.wasClean
                    });
                    this.emit('disconnected');
                    this.attemptReconnect();
                };
            } catch (error) {
                console.error('Failed to create WebSocket:', error);
                reject(error);
            }
        });
    }

    send(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
            console.log('Message sent:', message);
        } else {
            console.error('WebSocket is not connected');
        }
    }

    on(eventType, handler) {
        if (!this.handlers[eventType]) {
            this.handlers[eventType] = [];
        }
        this.handlers[eventType].push(handler);
    }

    emit(eventType, data) {
        if (this.handlers[eventType]) {
            this.handlers[eventType].forEach(handler => {
                try {
                    handler(data);
                } catch (e) {
                    console.error(`Error in handler for ${eventType}:`, e);
                }
            });
        }
    }

    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`Attempting reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => this.connect().catch(e => console.error('Reconnect failed:', e)), this.reconnectDelay);
        }
    }

    close() {
        if (this.ws) {
            this.ws.close();
        }
    }

    isConnected() {
        return this.ws && this.ws.readyState === WebSocket.OPEN;
    }
}

// 创建全局 WebSocket 客户端实例
const gameWs = new GameWebSocketClient();
