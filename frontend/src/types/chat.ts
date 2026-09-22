/**
 * 对话领域类型：一个 assistant 回合 = segments 数组（按 SSE 事件到达顺序组装）
 * think/tool/answer 在 ReAct 循环中多轮交错，用可判别联合（type 字段）区分。
 */

/** 思考段（纯文本展示），done 在整条消息收尾时置 true */
export interface ThinkSegment {
  type: 'think'
  text: string
  done: boolean
}

/** 正文段（markdown 渲染），无独立进度标记，跟消息级 status 走 */
export interface AnswerSegment {
  type: 'answer'
  text: string
}

export type ToolStatus = 'running' | 'done' | 'error'

/** 工具卡片段：result/error 始终存在，初始为 null */
export interface ToolSegment {
  type: 'tool'
  tool: string
  args: Record<string, unknown>
  result: string | null
  error: string | null
  status: ToolStatus
}

export type Segment = ThinkSegment | ToolSegment | AnswerSegment

export interface UserMessage {
  role: 'user'
  content: string
}

export type MessageStatus = 'streaming' | 'done' | 'error'

export interface AssistantMessage {
  role: 'assistant'
  segments: Segment[]
  status: MessageStatus
  error: string
}

export type ChatMessage = UserMessage | AssistantMessage

/* ---------- SSE 事件载荷（与后端 AiChatStream 约定一致） ---------- */

export interface SessionEventData {
  sessionId: string
}

export interface DeltaEventData {
  delta: string
}

export interface ToolStartEventData {
  tool: string
  args: Record<string, unknown>
}

export interface ToolEndEventData {
  tool: string
  result: string
  isError: boolean
}

export interface ToolErrorEventData {
  tool: string
  error: string
}

export interface ErrorEventData {
  message: string
}

/**
 * 按事件名分发的回调表：key 即 SSE 的 event 名。
 * 未注册的事件会被传输层静默忽略（前后端可独立演进）。
 */
export interface ChatStreamHandlers {
  session?: (data: SessionEventData) => void
  think?: (data: DeltaEventData) => void
  answer?: (data: DeltaEventData) => void
  tool_start?: (data: ToolStartEventData) => void
  tool_end?: (data: ToolEndEventData) => void
  tool_error?: (data: ToolErrorEventData) => void
  done?: (data: Record<string, never>) => void
  error?: (data: ErrorEventData) => void
}
