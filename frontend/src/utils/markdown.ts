import { marked, type RendererThis, type Tokens } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    code({ text, lang }: Tokens.Code): string {
      // 提取语言名：防御 "js {1-3}" 等元信息导致高亮失效，并过滤非法字符（防御 class 属性注入）
      const rawLang = (lang || '').trim().split(/\s+/)[0]
      const cleanLang = /^[a-zA-Z0-9_\-+.#]+$/.test(rawLang) ? rawLang : ''
      const language = cleanLang && hljs.getLanguage(cleanLang) ? cleanLang : 'plaintext'

      // 着色（hljs 内部恒定完成 HTML 转义，未知语言退化为 plaintext 原样转义输出）
      const highlighted = hljs.highlight(text, { language, ignoreIllegals: true }).value
      return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`
    },
    // 链接新窗口打开
    link(this: RendererThis, { href, title, tokens }: Tokens.Link): string {
      // 1. 解析链接内部的可见文本 (支持 **加粗**、*斜体* 等)
      const text = this.parser.parseInline(tokens)

      // 2. 仅对 http/https 外链补充新标签页属性
      const targetAttr = /^https?:\/\//i.test(href) ? ' target="_blank"' : ''

      // 3. title 存在才拼属性
      const titleAttr = title ? ` title="${title}"` : ''

      return `<a href="${href || ''}"${titleAttr}${targetAttr}>${text}</a>`
    },
  },
})

/** 渲染 answer 段全文（流式期间每帧全文重渲染，聊天文本量无压力） */
export function renderMarkdown(text: string): string {
  return marked.parse(text || '', { async: false })
}
