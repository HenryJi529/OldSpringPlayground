import { useAuthStore } from '../stores/auth'
import router from '../router'

/** 后端统一响应包 */
export interface R<T = unknown> {
  code: string
  msg: string
  data?: T
}

/** 带业务 code 的错误，供调用方按 code 分支处理（如鉴权失效） */
export class ApiError extends Error {
  code?: string

  constructor(message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 后端鉴权失败不走 HTTP 401，而是统一 200 + R 包里的这些 code
 * （见 WebUtil.renderJson / JwtAuthenticationFilter）
 */
export const AUTH_ERROR_CODES = new Set(['TOKEN_INVALID', 'TOKEN_EXPIRED', 'AUTHENTICATION_FAILED'])

/** 清理登录态并跳登录页（已在登录页则不重复跳），返回可直接 throw 的错误 */
export function forceLogout(message = '登录已过期，请重新登录'): ApiError {
  const auth = useAuthStore()
  auth.logout()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
  return new ApiError(message)
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  body?: unknown
}

/**
 * 普通 JSON 请求的薄封装：
 * - 统一拼 /api 前缀、带裸 token（后端 JWT 过滤器直接 parse header，无 Bearer 前缀）
 * - 解 R 包：code !== 'SUCCESS' 抛错（message 取后端 msg）
 * - token 失效（TOKEN_INVALID / TOKEN_EXPIRED 等）：清理登录态并跳登录页
 */
export async function request<T = unknown>(path: string, { method = 'GET', body }: RequestOptions = {}): Promise<T> {
  const auth = useAuthStore()

  let resp: Response
  try {
    resp = await fetch(`/api${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(auth.token ? { Authorization: auth.token } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError('网络异常，请检查后端服务是否可用')
  }

  let r: R<T>
  try {
    r = (await resp.json()) as R<T>
  } catch {
    throw new ApiError(`服务异常（HTTP ${resp.status}）`)
  }

  if (AUTH_ERROR_CODES.has(r.code)) {
    throw forceLogout(r.msg)
  }
  if (r.code !== 'SUCCESS') {
    throw new ApiError(r.msg || '请求失败', r.code)
  }
  return r.data as T
}
