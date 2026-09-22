<template>
  <span class="answer-wrap">
    <div class="answer-block markdown-body" v-html="DOMPurify.sanitize(html)"></div>
    <span v-if="streaming" class="stream-cursor">▍</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdown'
import DOMPurify from 'dompurify'
import type { AnswerSegment } from '../types/chat'

const props = defineProps<{
  segment: AnswerSegment
  streaming?: boolean
}>()

const html = computed(() => renderMarkdown(props.segment.text))
</script>

<style scoped>
.markdown-body {
  font-size: 15px;
  line-height: 1.8;
  color: #333;
  word-break: break-word;
}

.stream-cursor {
  color: #1677ff;
  animation: cursor-blink 0.8s infinite;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.markdown-body :deep(p) {
  margin: 0 0 12px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 20px 0 10px;
  line-height: 1.4;
}

.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child) {
  margin-top: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0 12px;
  padding-left: 24px;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 4px 14px;
  border-left: 3px solid #d6e4ff;
  color: #666;
  background: #fafcff;
  border-radius: 0 8px 8px 0;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 16px 0;
}

.markdown-body :deep(pre) {
  background: #f6f8fa;
  border: 1px solid #eee;
  padding: 12px 14px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(code) {
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.markdown-body :deep(:not(pre) > code) {
  background: #f2f3f5;
  padding: 2px 6px;
  border-radius: 5px;
  color: #c7254e;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 14px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e8e8e8;
  padding: 6px 14px;
}

.markdown-body :deep(th) {
  background: #fafafa;
  font-weight: 600;
}

.markdown-body :deep(a) {
  color: #1677ff;
}
</style>
