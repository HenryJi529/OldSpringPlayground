import { ref } from 'vue'
import { defineStore } from 'pinia'
import { chatStream } from '../api/chatStream'
import { AUTH_ERROR_CODES, forceLogout, ApiError } from '../api/http'
import { useAuthStore } from './auth'
import type {
  AssistantMessage,
  ChatMessage,
  ChatStreamHandlers,
  MessageStatus,
  ToolSegment,
} from '../types/chat'

/**
 * 一个 assistant 回合 = segments 数组（按 SSE 事件到达顺序组装）：
 *   think  思考段（纯文本展示，done 在消息收尾时置 true）
 *   tool   工具卡片（status: running/done/error）
 *   answer 正文段（markdown 渲染）
 *
 * think/tool/answer 在 ReAct 循环中多轮交错，不能用三个累积字符串；
 * 同类相邻 delta 合并进末尾段，异类到来开新段。
 *
 * 类型定义集中在 src/types/chat.ts。
 */
export const useChatStore = defineStore('chat', () => {
  /** 消息列表（segment 结构见 types/chat.ts） */
  const messages = ref<ChatMessage[]>([])
  /** 会话 id：仅存内存，刷新即新会话 */
  const sessionId = ref<string | null>(null)
  const streaming = ref(false)

  let abortController: AbortController | null = null
  let aborted = false

  async function sendMessage(text: string): Promise<void> {
    if (streaming.value) return
    const auth = useAuthStore()

    messages.value.push({ role: 'user', content: text })
    messages.value.push({ role: 'assistant', segments: [], status: 'streaming', error: '' })
    // 从 ref 数组里取出的就是 reactive 代理，改它的属性 UI 才能增量更新（流式效果）
    const assistantMsg = messages.value[messages.value.length - 1] as AssistantMessage
    streaming.value = true

    abortController = new AbortController()

    const handlers: ChatStreamHandlers = {
      session: (data) => {
        sessionId.value = data.sessionId
      },
      think: (data) => appendDelta(assistantMsg, 'think', data.delta),
      answer: (data) => appendDelta(assistantMsg, 'answer', data.delta),
      tool_start: (data) => {
        assistantMsg.segments.push({
          type: 'tool',
          tool: data.tool,
          args: data.args,
          result: null,
          error: null,
          status: 'running',
        })
      },
      tool_end: (data) => {
        const seg = findOpenToolSegment(assistantMsg, data.tool)
        if (seg) {
          seg.result = data.result
          seg.status = data.isError ? 'error' : 'done'
        }
      },
      tool_error: (data) => {
        const seg = findOpenToolSegment(assistantMsg, data.tool)
        if (seg) {
          seg.error = data.error
          seg.status = 'error'
        }
      },
      done: () => {
        finish(assistantMsg, 'done')
      },
      error: (data) => {
        assistantMsg.error = data.message
        finish(assistantMsg, 'error')
      },
    }

    try {
      await chatStream({
        message: text,
        sessionId: sessionId.value,
        token: auth.token,
        signal: abortController.signal,
        handlers,
      })
      // 流正常结束但没收到 done（异常情况），兜底收尾
      if (assistantMsg.status === 'streaming') finish(assistantMsg, 'done')
    } catch (err) {
      if (aborted) {
        // 手动停止：保留已收内容
        finish(assistantMsg, 'done')
      } else {
        const e = err as ApiError
        if (e.code && AUTH_ERROR_CODES.has(e.code)) {
          forceLogout(e.message)
        }
        assistantMsg.error = e.message
        finish(assistantMsg, 'error')
      }
    } finally {
      streaming.value = false
      abortController = null
      aborted = false
    }
  }

  /** 停止当前流式回答，保留已收内容 */
  function abort(): void {
    if (abortController) {
      aborted = true
      abortController.abort()
    }
  }

  /** 新会话：清空消息与 sessionId（登录态不受影响） */
  function newSession(): void {
    abort()
    messages.value = []
    sessionId.value = null
  }

  function appendDelta(msg: AssistantMessage, type: 'think' | 'answer', delta: string): void {
    const last = msg.segments[msg.segments.length - 1]
    if (last && last.type === type) {
      last.text += delta
    } else if (type === 'think') {
      msg.segments.push({ type: 'think', text: delta, done: false })
    } else {
      msg.segments.push({ type: 'answer', text: delta })
    }
  }

  /** 从后往前找"最早未闭合的同名工具段"——事件无 callId，按工具名配对 */
  function findOpenToolSegment(msg: AssistantMessage, toolName: string): ToolSegment | null {
    for (let i = msg.segments.length - 1; i >= 0; i--) {
      const seg = msg.segments[i]
      if (seg.type === 'tool' && seg.tool === toolName && seg.status === 'running') {
        return seg
      }
    }
    return null
  }

  function finish(msg: AssistantMessage, status: MessageStatus): void {
    msg.status = status
    for (const seg of msg.segments) {
      if (seg.type === 'think') seg.done = true
      if (seg.type === 'tool' && seg.status === 'running') seg.status = 'done'
    }
  }

  return { messages, sessionId, streaming, sendMessage, abort, newSession }
})
