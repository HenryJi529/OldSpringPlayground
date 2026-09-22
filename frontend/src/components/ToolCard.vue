<template>
  <div class="tool-card" :class="segment.status">
    <div class="tool-header" @click="expanded = !expanded">
      <LoadingOutlined v-if="segment.status === 'running'" class="tool-spinner" />
      <span v-else class="tool-wrench">🔧</span>
      <span class="tool-name">
        {{ segment.status === 'running' ? `正在调用 ${segment.tool} …` : segment.tool }}
      </span>
      <CheckCircleFilled v-if="segment.status === 'done'" class="status-icon ok" />
      <CloseCircleFilled v-else-if="segment.status === 'error'" class="status-icon err" />
      <DownOutlined class="chevron" :class="{ open: expanded }" />
    </div>
    <div v-show="expanded" class="tool-detail">
      <div class="tool-section">
        <div class="tool-label">入参</div>
        <pre class="tool-json">{{ maskedArgs }}</pre>
      </div>
      <div v-if="segment.status !== 'running'" class="tool-section">
        <div class="tool-label">结果</div>
        <pre class="tool-json">{{ resultText }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { LoadingOutlined, CheckCircleFilled, CloseCircleFilled, DownOutlined } from '@ant-design/icons-vue'
import type { ToolSegment } from '../types/chat'

const props = defineProps<{
  segment: ToolSegment
}>()

// 运行中自动展开（看得到活动），结束后自动折叠成一行
const expanded = ref(props.segment.status === 'running')
watch(
  () => props.segment.status,
  (status) => {
    expanded.value = status === 'running'
  }
)


// 框架内部参数（__ 前缀，如 MCP streamable 的 __sessionId）不展示；长值掩码
const maskedArgs = computed(() => {
  const visible = Object.fromEntries(
    Object.entries(props.segment.args ?? {})
      .filter(([k]) => !k.startsWith('__'))
  )
  return JSON.stringify(visible, null, 2)
})

const resultText = computed(() => {
  if (props.segment.error) return props.segment.error
  return props.segment.result ?? ''
})
</script>

<style scoped>
.tool-card {
  margin: 8px 0;
  border: 1px solid #eee;
  border-radius: 10px;
  background: #fafbfc;
  font-size: 13px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  color: #666;
}

.tool-header:hover {
  color: #333;
}

.tool-spinner {
  color: #1677ff;
}

.tool-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  flex: 1;
}

.status-icon.ok {
  color: #52c41a;
}

.status-icon.err {
  color: #ff4d4f;
}

.chevron {
  font-size: 10px;
  color: #bbb;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.tool-detail {
  border-top: 1px solid #f0f0f0;
  padding: 10px 12px;
}

.tool-section + .tool-section {
  margin-top: 8px;
}

.tool-label {
  font-size: 12px;
  color: #aaa;
  margin-bottom: 4px;
}

.tool-json {
  margin: 0;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow: auto;
  color: #555;
}
</style>
