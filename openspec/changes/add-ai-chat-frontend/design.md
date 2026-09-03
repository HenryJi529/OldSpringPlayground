## Context

后端契约已固化（`AiChatStream.java` 是前端契约的唯一真相）：

- **登录**：`POST /user/auth/login`，请求 `{account, password}`，响应 `R {code, msg, data: {account, token}}`，成功时 `code === "SUCCESS"`。
- **流式对话**：`POST /demo/ai/chat/stream`，请求 `{message, sessionId?}`，SSE 推送事件序列 `session → (think | tool_start → tool_end/tool_error | answer)* → done | error`。
  - 事件 payload：`session{sessionId}`、`think/answer{delta}`、`tool_start{tool, args}`、`tool_end{tool, result, isError}`、`tool_error{tool, error}`、`done{answer}`、`error{message}`。
- **鉴权头格式**：`Authorization: <token>` **裸 token，无 `Bearer ` 前缀**（`JwtAuthenticationFilter` 对 header 值直接 `jwtUtil.parse`）。
- 后端 CORS 全开放；服务端 SSE 总超时 10 分钟。

用户决策（探索阶段已确认）：UI 库用 Ant Design Vue（用户只熟悉这一级别的组件库）；不做历史会话，刷新即新会话；正文纯 markdown 渲染，**放弃 JSON 智能表格方案**，渲染库用 **marked**（用户既往项目熟悉，本项目无 markdown-it 独有需求）；只做登录页，不做注册。

## Goals / Non-Goals

**Goals:**
- 登录页：表单校验、登录、token 持久化（localStorage）、路由守卫。
- 对话页：发送消息、SSE 流式消费、ReAct 过程（think/tool/answer）按到达顺序可视化、markdown 渲染正文、代码高亮。
- 断线/错误可见：`error` 事件与网络异常在 UI 上有明确反馈；支持中断正在进行的回答。
- 新会话按钮；sessionId 存内存，刷新即新会话。

**Non-Goals:**
- 历史会话列表 / 会话持久化（后端无列表接口，且用户明确不需要）。
- 注册页。
- JSON 代码块智能升级为表格（纯 markdown 契约，模型输出什么渲染什么）。
- 移动端适配、深色模式、国际化。
- 生产构建部署（只做开发态可用）。

## Decisions

1. **SSE 客户端：`@microsoft/fetch-event-source`**
   对话接口是 POST + 自定义 Authorization 头，原生 `EventSource`（仅 GET、不能带自定义头）不可用。后端 controller 注释亦如此建议。用其 `onmessage(ev)` 按 `ev.event` 分发到各事件处理器。

2. **assistant 回合的数据结构：segments 数组（按到达顺序）**
   ReAct 循环中 think / tool / answer 会多轮交错，不能用"三个累积字符串"。每个 assistant 回合维护：
   ```js
   segments: [
     { type: 'think',  text, done: boolean },
     { type: 'tool',   tool, args, result?, status: 'running'|'done'|'error', error? },
     { type: 'answer', text },
   ]
   ```
   同类相邻事件合并进当前末尾段（如连续 think delta 追加到末尾 think 段）；answer delta 到来时若末尾不是 answer 段则新建。**这是整个前端的核心模型**。

3. **tool_start/tool_end 配对：按 tool 名 + 最早未闭合段**
   事件无 callId，只有工具名。`tool_end` 到达时，从后往前找第一个 `status === 'running'` 且同名的 tool 段闭合之。同名工具并发调用在本后端不存在（工具事件来自单模型流的串行执行），此策略足够。

4. **渲染库：marked + highlight.js**
   用户熟悉 marked；本项目定制需求（代码复制按钮、链接新窗口）用 DOM 后处理即可，不需要 markdown-it 的 token 流定制。`marked` 开 GFM（默认），**不做 HTML 注入**（marked 默认不 sanitize，采用"渲染后只用 `v-html` 输出 marked 产物 + 不允许原始 HTML"策略：通过 `marked.use({ renderer: { html: () => '' } })` 或后处理剥离原始 HTML，确保 XSS 安全——实现时在两条路径中选一条并验证）。highlight.js 对 `pre code` 高亮。
   - 流式期间：answer delta 到来后对当前 answer 段**全文重新 render**（聊天级文本量，性能无压力），不引入增量解析库。

5. **think 段不套 markdown**：思考流按纯文本 `pre-wrap` 展示，模型的思考常含半截 markdown，渲染反而乱。

6. **UI 组件：Ant Design Vue**
   登录用 `Form/Input/Button`，思考块用 `Collapse`，工具卡片用 `Card` + `Tag`（运行中/成功/失败状态色），消息列表自绘气泡。整体风格保持简单，不追求聊天产品级打磨。

7. **状态管理：Pinia**
   `auth` store：token（localStorage 持久化）、account、login/logout。`chat` store：sessionId（仅内存）、消息列表、streaming 状态、sendMessage/abort。路由守卫读 auth store，未登录跳 `/login`。

8. **HTTP 层**：普通请求（登录）用 fetch 薄封装（统一解 `R` 包、`code !== 'SUCCESS'` 抛错、401 清 token 跳登录）；SSE 走 fetch-event-source 独立通道，不复用该封装。

9. **Vite proxy**：`/api → http://localhost:8088`（rewrite 去掉 `/api` 前缀）。前端代码里只写 `/api/...`，零硬编码后端地址。

10. **目录结构**（仓库根新增 `frontend/`）：
    ```
    frontend/
      src/
        api/          # http 封装、login、chatStream(fetch-event-source)
        stores/       # auth.ts、chat.ts
        views/        # LoginView.vue、ChatView.vue
        components/   # MessageList、AssistantSegments、ThinkBlock、ToolCard、AnswerBlock、ChatInput
        router/       # 路由 + 守卫
        utils/        # markdown 渲染封装
    ```

## Risks / Trade-offs

- [marked 默认不转义原始 HTML，模型输出可注入 `<script>`] → 决策 4：禁用原始 HTML 输出并在联调时用恶意样本验证（如让模型输出 `<img onerror>` 测试）。
- [SSE 事件乱序/并发写] → 后端 `AiChatStream.send` 已加锁，事件按发送顺序到达；前端按到达顺序处理即可，无需排序逻辑。
- [JWT 过期发生在对话中途] → SSE 请求直接 401（非 SSE 流），fetch-event-source 的 `onerror`/`response.status` 捕获后清 token 跳登录。
- [think 与 answer 在 done 前未闭合（如断流）] → abort/error 时将所有未闭合段标记 done，保留已收内容。
- [10 分钟 SSE 超时] → 后端超时即断流，前端视为 error 处理；长 ReAct 链场景提示用户重试。
- [直接改写 push 进 reactive 数组的 raw 对象不触发 Vue 更新] → 已踩坑：UI 表现为"回答一次性出现"。写入前必须先从 `messages[messages.length - 1]` 取回 reactive 代理再改。
- [antdv 4.x 体积] → 演示项目可接受全量引入，不做按需加载优化。

## Migration Plan

纯新增，无存量迁移。回滚 = 删除 `frontend/` 目录。

## Open Questions

- 后端 system prompt 是否追加"表格数据用 markdown 表格呈现、不要贴原始 JSON"（Layer 1 契约）——属后端改动，不在本 change 范围内，联调时视模型输出质量再决定是否另起 change。
