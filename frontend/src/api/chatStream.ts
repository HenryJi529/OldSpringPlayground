import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { ChatStreamHandlers } from '../types/chat'
import { ApiError } from './http'

export interface ChatStreamOptions {
  /** 用户消息 */
  message: string
  /** 会话 id（首轮不传） */
  sessionId?: string | null
  /** JWT（裸 token，无 Bearer 前缀） */
  token: string
  /** 用于"停止"按钮中断流 */
  signal: AbortSignal
  /** 按事件名分发：session/think/answer/tool_start/tool_end/tool_error/done/error */
  handlers: ChatStreamHandlers
}

/**
 * 对话 SSE 流封装（POST + 裸 token，原生 EventSource 不支持，用 fetch-event-source）。
 *
 * 约定：handlers.error 仅在收到服务端 error 事件时调用；
 * 连接级失败（鉴权失效、网络断开）通过 Promise reject 抛出 ApiError。
 */
export function chatStream({ message, sessionId, token, signal, handlers }: ChatStreamOptions): Promise<void> {
  return fetchEventSource('/api/demo/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token,
    },
    body: JSON.stringify(sessionId ? { message, sessionId } : { message }),
    signal,
    // 页面切到后台也不断流
    openWhenHidden: true,
    async onopen(resp) {
      // 鉴权失败时后端返回 200 + R 包 JSON（非 SSE 流），按 content-type 识别
      const contentType = resp.headers.get('content-type') || ''
      if (!contentType.includes('text/event-stream')) {
        const r = await resp.json().catch(() => null)
        throw new ApiError(r?.msg || `对话请求失败（HTTP ${resp.status}）`, r?.code)
      }
    },
    onmessage(ev) {
      // 后端每个事件的 data 都是 JSON（AiChatStream 统一定义）
      const data = JSON.parse(ev.data)
      const handler = handlers[ev.event as keyof ChatStreamHandlers] as
        | ((data: unknown) => void)
        | undefined
      handler?.(data)
    },
    onerror(err) {
      // fetch-event-source 默认会自动重连；这里统一上抛，禁止重试
      throw err
    },
  })
}
