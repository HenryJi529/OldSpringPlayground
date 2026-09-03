## 1. 工程脚手架

- [ ] 1.1 在仓库根创建 `frontend/`（Vite + Vue 3），安装依赖：vue-router、pinia、ant-design-vue、marked、highlight.js、@microsoft/fetch-event-source
- [ ] 1.2 配置 `vite.config.js`：`/api` proxy 到 `http://localhost:8088`（rewrite 去前缀）
- [ ] 1.3 配置 router（`/login`、`/chat`）与 Pinia，接入 antdv

## 2. 鉴权

- [ ] 2.1 `stores/auth`：token/account 状态 + localStorage 持久化 + login/logout action
- [ ] 2.2 `api/http`：fetch 薄封装（解 `R` 包、`code !== 'SUCCESS'` 抛错、401 清 token 跳登录），请求头带裸 token（无 Bearer 前缀）
- [ ] 2.3 路由守卫：无 token 访问 `/chat` 重定向 `/login`
- [ ] 2.4 `LoginView`：antdv 表单（必填校验）→ 登录成功跳 `/chat`，失败展示 `msg`

## 3. 流式对话核心

- [ ] 3.1 `api/chatStream`：fetch-event-source 封装，按 `ev.event` 分发回调（session/think/answer/tool_start/tool_end/tool_error/done/error），暴露 abort
- [ ] 3.2 `stores/chat`：消息列表 + segments 组装逻辑（同类相邻 delta 合并、answer 新段创建、tool 按"最早未闭合同名段"配对）、sessionId 内存管理、sendMessage/abort/newSession
- [ ] 3.3 断流/异常收尾：error 事件、网络错误、手动停止时将所有未闭合段标记完成

## 4. 对话页 UI

- [ ] 4.1 `ChatView`：消息时间线 + 底部输入区（Enter 发送 / Shift+Enter 换行），流式期间输入禁用、显示"停止"按钮
- [ ] 4.2 `ThinkBlock`：antdv Collapse，纯文本 pre-wrap 增量展示，流式期间展开、完成后折叠
- [ ] 4.3 `ToolCard`：工具名 + 状态 Tag（运行中/成功/失败）+ args/result 展示（JSON pretty）
- [ ] 4.4 `AnswerBlock`：marked 全文重渲染 + highlight.js 代码高亮；禁用原始 HTML（防 XSS）
- [ ] 4.5 顶栏：account 展示、"新会话"按钮、退出登录

## 5. 联调验证

- [ ] 5.1 登录流程：正确/错误密码、未登录访问 `/chat` 重定向
- [ ] 5.2 对话流程：首轮无 sessionId → 收到 session 事件 → 后续携带；done 正常收尾
- [ ] 5.3 ReAct 可视化：触发一次工具调用（如"查看我的数据"），确认 think 块、tool 卡片、answer 顺序与配对正确
- [ ] 5.4 异常路径：停止按钮、后端 error 事件、token 过期 401
- [ ] 5.5 XSS 验证：让模型输出含 `<img onerror>` 的内容，确认以文本展示不执行
- [ ] 5.6 markdown 验证：标题/列表/表格/代码块高亮渲染正常
