<template>
  <div class="login-page">
    <a-card class="login-card" title="灵犀光年MCP">
      <a-form :model="form" layout="vertical" @finish="onSubmit">
        <a-alert
          v-if="errorMsg"
          :message="errorMsg"
          type="error"
          show-icon
          class="login-alert"
        />
        <a-form-item
          label="账号"
          name="account"
          :rules="[{ required: true, message: '请输入账号' }]"
        >
          <a-input v-model:value="form.account" placeholder="账号" size="large" autofocus />
        </a-form-item>
        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password v-model:value="form.password" placeholder="密码" size="large" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            登 录
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ account: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

async function onSubmit() {
  loading.value = true
  errorMsg.value = ''
  try {
    await auth.login(form.account, form.password)
    // 登录成功：优先跳回被守卫拦截前想去的页面，否则进聊天页
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' ? redirect : { name: 'chat' })
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f8ff 0%, #f7f7f8 60%, #fdf6ff 100%);
}

.login-card {
  width: 380px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);
}

.login-alert {
  margin-bottom: 16px;
}
</style>
