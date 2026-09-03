 <template>
  <div class="chat-layout">
    <header class="chat-header">
      <div class="brand"><span class="brand-icon">✨</span>灵犀光年小助手</div>
      <div class="chat-header-actions">
        <span class="chat-account">{{ auth.account }}</span>
        <a-button size="small" shape="round" @click="onNewSession">
          <PlusOutlined /> 新会话
        </a-button>
        <a-button size="small" type="text" danger @click="onLogout">退出</a-button>
      </div>
    </header>

    <main ref="listRef" class="chat-list">
      <div v-if="chat.messages.length === 0" class="chat-empty">
        <div class="empty-logo">✨</div>
        <p class="empty-hint">问点什么吧，比如：查看我的数据</p>
      </div>

      <div
        v-for="(msg, i) in chat.messages"
        :key="i"
        class="msg-row"
        :class="msg.role"
      >
        <div v-if="msg.role === 'user'" class="user-bubble">{{ msg.content }}</div>
        <template v-else>
          <div class="assistant-avatar">✨</div>
          <div class="assistant-body">
            <template v-for="(seg, j) in msg.segments" :key="j">
              <ThinkBlock v-if="seg.type === 'think'" :segment="seg" />
              <ToolCard v-else-if="seg.type === 'tool'" :segment="seg" />
              <AnswerBlock
                v-else
                :segment="seg"
                :streaming="msg.status === 'streaming' && j === msg.segments.length - 1"
              />
            </template>
            <div v-if="msg.status === 'streaming' && msg.segments.length === 0" class="typing">
              <span /><span /><span />
            </div>
            <a-alert
              v-if="msg.error"
              :message="msg.error"
              type="error"
              show-icon
              class="msg-error"
            />
          </div>
        </template>
      </div>
    </main>

    <footer class="chat-input-area">
      <div class="input-capsule">
        <a-textarea
          v-model:value="input"
          :bordered="false"
          :auto-size="{ minRows: 1, maxRows: 6 }"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          :disabled="chat.streaming"
          @keydown.enter.exact.prevent="onSend"
        />
        <a-button
          v-if="chat.streaming"
          shape="circle"
          danger
          title="停止"
          @click="chat.abort()"
        >
          <StopOutlined />
        </a-button>
        <a-button
          v-else
          type="primary"
          shape="circle"
          title="发送"
          :disabled="!input.trim()"
          @click="onSend"
        >
          <ArrowUpOutlined />
        </a-button>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, ArrowUpOutlined, StopOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import ThinkBlock from '../components/ThinkBlock.vue'
import ToolCard from '../components/ToolCard.vue'
import AnswerBlock from '../components/AnswerBlock.vue'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()

const input = ref('')
const listRef = ref<HTMLElement | null>(null)

function onSend() {
  const text = input.value.trim()
  if (!text || chat.streaming) return
  input.value = ''
  chat.sendMessage(text)
}

function onNewSession() {
  chat.newSession()
}

function onLogout() {
  chat.newSession()
  auth.logout()
  router.push({ name: 'login' })
}

// 消息增长时滚动到底部
watch(
  () => chat.messages,
  async () => {
    await nextTick()
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  },
  { deep: true }
)
</script>

<style scoped>
.chat-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  max-width: 820px;
  margin: 0 auto;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
}

.brand {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-account {
  color: #999;
  font-size: 13px;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 24px 24px;
}

.chat-empty {
  text-align: center;
  margin-top: 160px;
  color: #999;
}

.empty-logo {
  font-size: 40px;
  margin-bottom: 12px;
}

.msg-row {
  display: flex;
  margin-bottom: 24px;
  animation: fade-up 0.25s ease;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.msg-row.user {
  justify-content: flex-end;
}

.user-bubble {
  max-width: 75%;
  padding: 10px 16px;
  border-radius: 16px 16px 4px 16px;
  background: #e8f0fe;
  color: #1a1a1a;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.assistant-avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e6f4ff, #f9f0ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  margin-right: 12px;
  margin-top: 2px;
}

.assistant-body {
  flex: 1;
  min-width: 0;
}

.typing {
  display: inline-flex;
  gap: 4px;
  padding: 8px 0;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #bbb;
  animation: typing-bounce 1.2s infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-bounce {
  0%, 60%, 100% {
    transform: none;
    opacity: 0.4;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.msg-error {
  margin-top: 8px;
}

.chat-input-area {
  padding: 12px 24px 20px;
}

.input-capsule {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 24px;
  padding: 8px 10px 8px 18px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-capsule:focus-within {
  border-color: #1677ff;
  box-shadow: 0 2px 12px rgba(22, 119, 255, 0.12);
}

.input-capsule :deep(.ant-input) {
  padding: 4px 0;
  resize: none;
}
</style>
