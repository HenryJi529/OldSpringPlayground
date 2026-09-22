import { defineStore } from 'pinia'
import { request } from '../api/http'

const TOKEN_KEY = 'ai-chat-token'
const ACCOUNT_KEY = 'ai-chat-account'

/** 登录接口返回的数据 */
interface LoginResponse {
  token: string
  account: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    account: localStorage.getItem(ACCOUNT_KEY) || '',
  }),
  getters: {
    isLoggedIn: (state): boolean => !!state.token,
  },
  actions: {
    async login(account: string, password: string): Promise<void> {
      const data = await request<LoginResponse>('/user/auth/login', {
        method: 'POST',
        body: { account, password },
      })
      this.token = data.token
      this.account = data.account
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(ACCOUNT_KEY, data.account)
    },
    logout(): void {
      this.token = ''
      this.account = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(ACCOUNT_KEY)
    },
  },
})
