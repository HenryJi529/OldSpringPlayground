<template>
  <div class="think-block">
    <div class="think-header" @click="expanded = !expanded">
      <span class="think-icon">💭</span>
      <span class="think-title">
        {{ segment.done ? '思考过程' : '正在思考' }}
        <span v-if="!segment.done" class="think-dots"><i>...</i></span>
      </span>
      <DownOutlined class="chevron" :class="{ open: expanded }" />
    </div>
    <div v-show="expanded" class="think-content">{{ segment.text }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { DownOutlined } from '@ant-design/icons-vue'
import type { ThinkSegment } from '../types/chat'

const props = defineProps<{
  segment: ThinkSegment
}>()

// 流式期间展开，完成后自动折叠
const expanded = ref(true)
watch(
  () => props.segment.done,
  (done) => {
    if (done) expanded.value = false
  }
)
</script>

<style scoped>
.think-block {
  margin: 6px 0;
}

.think-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  color: #999;
  font-size: 13px;
  padding: 2px 0;
}

.think-header:hover {
  color: #666;
}

.think-icon {
  font-size: 14px;
}

.chevron {
  font-size: 10px;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.think-content {
  margin: 6px 0 6px 4px;
  padding: 4px 0 4px 12px;
  border-left: 2px solid #e8e8e8;
  color: #999;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.think-dots i {
  font-style: normal;
  animation: blink 1.2s infinite;
}

.think-dots i:nth-child(2) {
  animation-delay: 0.2s;
}

.think-dots i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 60%, 100% { opacity: 0.2; }
  30% { opacity: 1; }
}
</style>
