import { getPortalToken } from '@/auth/storage'

const BASE = '/api/cs/chat'

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${getPortalToken()}`
  }
}

function authHeaderOnly() {
  return { 'Authorization': `Bearer ${getPortalToken()}` }
}

export const chatApi = {
  /** 创建会话；problem 非空时为快捷服务入场 */
  async createSession(problem) {
    const res = await fetch(`${BASE}/sessions`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(problem ? { problem } : {})
    })
    if (!res.ok) throw new Error(`createSession failed: ${res.status}`)
    return res.json()
  },

  async listMessages(conversationId) {
    const res = await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}/messages`, {
      headers: authHeaderOnly()
    })
    if (!res.ok) throw new Error(`listMessages failed: ${res.status}`)
    return res.json()
  },

  async submitRating(conversationId, useful) {
    const res = await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}/satisfaction`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ useful: useful === 'USEFUL' || useful === true })
    })
    if (!res.ok) throw new Error(`submitRating failed: ${res.status}`)
    return res.json()
  },

  async closeSession(conversationId) {
    await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}/close`, {
      method: 'POST',
      headers: authHeaderOnly()
    })
  },

  async executeAction(conversationId, actionCode) {
    const res = await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}/actions`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ action: actionCode })
    })
    return res.json()
  },

  async transferHuman(conversationId) {
    const res = await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}/human`, {
      method: 'POST',
      headers: authHeaders()
    })
    return res.json()
  },

  async getSession(conversationId) {
    const res = await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}`, {
      headers: authHeaderOnly()
    })
    if (!res.ok) throw new Error(`getSession failed: ${res.status}`)
    return res.json()
  },

  async sessionStatus(conversationId) {
    return this.getSession(conversationId)
  },

  async stop(conversationId) {
    await fetch(`${BASE}/sessions/${encodeURIComponent(conversationId)}/stop`, {
      method: 'POST',
      headers: authHeaderOnly()
    })
  },

  async listQuickEntries() {
    const res = await fetch(`${BASE}/quick-entries`, { headers: authHeaderOnly() })
    if (!res.ok) throw new Error(`listQuickEntries failed: ${res.status}`)
    return res.json()
  },

  /** V2 统一消息入口：寒暄 FastPath 命中则 chitchat=true */
  async sendMessage(conversationId, text) {
    const res = await fetch(`${BASE}/messages`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ conversationId, text })
    })
    if (!res.ok) throw new Error(`sendMessage failed: ${res.status}`)
    return res.json()
  },

  async sendHumanMessage(conversationId, content) {
    const params = new URLSearchParams({ conversationId, content })
    const res = await fetch(`/api/portal/human/send?${params}`, {
      method: 'POST',
      headers: authHeaderOnly()
    })
    if (!res.ok) throw new Error(`sendHumanMessage failed: ${res.status}`)
    return res.json()
  },

  async cancelHumanQueue(conversationId) {
    const params = new URLSearchParams({ conversationId })
    await fetch(`/api/portal/human/cancel?${params}`, {
      method: 'POST',
      headers: authHeaderOnly()
    })
  }
}

/**
 * SSE 流式对话，返回 AbortController。
 */
export function streamChat(conversationId, message, callbacks, quickServiceCode) {
  const controller = new AbortController()
  const timeoutMs = 180_000

  const timeoutId = setTimeout(() => {
    controller.abort()
    callbacks.onError?.('请求超时，请稍后重试')
  }, timeoutMs)

  const url = `${BASE}/sessions/${encodeURIComponent(conversationId)}/stream`
  const body = JSON.stringify({
    text: message,
    quickServiceCode: quickServiceCode || undefined
  })

  fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Content-Type': 'application/json',
      ...authHeaderOnly()
    },
    body,
    signal: controller.signal
  }).then(async response => {
    clearTimeout(timeoutId)
    if (!response.ok) {
      callbacks.onError?.(`HTTP ${response.status}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        let data = line.trim()
        if (!data) continue
        if (data.startsWith('data:')) data = data.slice(5).trim()
        if (!data) continue
        try {
          const event = JSON.parse(data)
          dispatchEvent(event, callbacks)
        } catch {
          // skip non-JSON
        }
      }
    }
  }).catch(err => {
    clearTimeout(timeoutId)
    if (err.name !== 'AbortError') {
      callbacks.onError?.(err.message)
    }
  })

  return controller
}

function dispatchEvent(event, cb) {
  switch (event.type) {
    case 'session': cb.onSession?.(event.content); break
    case 'progress': cb.onProgress?.(event.content); break
    case 'typing': cb.onTyping?.(); break
    case 'message': cb.onMessage?.(event); break
    case 'error': cb.onError?.(event.content); break
    case 'complete': cb.onComplete?.(event.data); break
    case 'stopped': cb.onStopped?.(event.content); break
    case 'human_queued': cb.onHumanQueued?.(event.count ?? event.content); break
    case 'human_connected': cb.onHumanConnected?.(event.content); break
    case 'human_text': cb.onHumanText?.(event.content); break
    case 'human_typing': cb.onHumanTyping?.(event.data); break
    case 'human_closed': cb.onHumanClosed?.(); break
  }
}

/**
 * 人工客服 SSE：排队 / 接入 / 坐席消息。
 */
export function streamHumanChat(conversationId, callbacks) {
  const controller = new AbortController()
  const url = `/api/portal/human/stream?conversationId=${encodeURIComponent(conversationId)}`

  fetch(url, {
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      ...authHeaderOnly()
    },
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      callbacks.onError?.(`HTTP ${response.status}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        let data = line.trim()
        if (!data) continue
        if (data.startsWith('data:')) data = data.slice(5).trim()
        if (!data) continue
        try {
          dispatchEvent(JSON.parse(data), callbacks)
        } catch {
          // skip
        }
      }
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      callbacks.onError?.(err.message)
    }
  })

  return controller
}
