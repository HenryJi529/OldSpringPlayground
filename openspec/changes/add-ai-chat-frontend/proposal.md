## Why

后端（Spring Boot，端口 8088）已具备 JWT 登录（`/user/auth/login`）与 AI 流式对话能力（`/demo/ai/chat/stream`，SSE 推送 ReAct 全过程：think / tool_start / tool_end / answer 事件）。目前没有任何前端入口，对话过程只能用 curl 观察。需要一个最小可用的 Vue 3 前端，让登录与 AI 对话（含思考、工具调用过程）可视化。

## What Changes

- 新增 `frontend/` 目录：Vite + Vue 3 + vue-router + Pinia + Ant Design Vue + marked + highlight.js + @microsoft/fetch-event-source。
- **登录页**：账号/密码表单，调 `/user/auth/login`，成功存 token 并跳转对话页。不做注册页。
- **AI 对话页**：消息时间线 + 底部输入框。通过 fetch-event-source 以 POST + Authorization 头消费 SSE 流。
- **ReAct 过程可视化**：一个 assistant 回合按事件到达顺序组装为 segments（think 折叠块 / tool 卡片 / answer 正文），思考过程与工具调用的参数、结果均可见。
- **正文 Markdown 渲染**：marked（GFM）+ highlight.js，`html: false` 防 XSS；纯 markdown 契约，不做 JSON 智能表格升级。
- **无历史会话**：刷新页面即新会话，sessionId 仅存内存；提供"新会话"按钮。
- Vite dev server 配置 proxy 将 `/api` 转发至 `localhost:8088`（后端 CORS 虽已全开放，走 proxy 避免硬编码地址）。

## Capabilities

### New Capabilities
- `ai-chat-frontend`: Vue 3 单页应用，提供登录鉴权与 AI 流式对话界面，可视化展示 ReAct 过程（思考流、工具调用、流式正文）。

### Modified Capabilities
<!-- 无既有 capability 的需求变更（纯新增前端，后端零改动） -->

## Impact

- **新增**：`frontend/` 整个目录（独立 npm 工程，不影响 `backend/` Maven 构建）。
- **后端**：零改动。仅消费既有接口 `/user/auth/login` 与 `/demo/ai/chat/stream`。
- **运行**：开发态 `npm run dev`（Vite，proxy 到 8088）；后端需先行启动。
- **依赖**：Node.js 环境；npm 依赖见 design.md 决策。
