## ADDED Requirements

### Requirement: 登录页
系统 SHALL 提供登录页（路由 `/login`），包含账号、密码输入框与登录按钮，调用 `POST /user/auth/login`（请求体 `{account, password}`），成功（响应 `code === "SUCCESS"`）后保存 `data.token` 并跳转至对话页。

#### Scenario: 登录成功进入对话页
- **WHEN** 用户输入正确账号密码并提交
- **THEN** 系统将 token 与 account 持久化到 localStorage，并跳转至 `/chat`

#### Scenario: 登录失败提示
- **WHEN** 后端返回 `code !== "SUCCESS"`（如密码错误）
- **THEN** 页面展示后端返回的 `msg`，不跳转

#### Scenario: 表单校验
- **WHEN** 账号或密码为空时点击登录
- **THEN** 前端阻止提交并提示必填，不发出请求

### Requirement: 路由守卫与鉴权头
系统 SHALL 对对话页做路由守卫：无 token 时访问 `/chat` 重定向至 `/login`；所有后端请求携带 `Authorization: <token>`（裸 token，无 `Bearer ` 前缀）；收到 401 响应时清除 token 并跳转登录页。

#### Scenario: 未登录访问对话页
- **WHEN** localStorage 中无 token，用户访问 `/chat`
- **THEN** 系统重定向至 `/login`

#### Scenario: token 过期
- **WHEN** 任一请求收到 401 响应
- **THEN** 系统清除本地 token，跳转 `/login`

### Requirement: 流式对话（SSE 消费）
系统 SHALL 通过 `@microsoft/fetch-event-source` 以 POST + Authorization 头请求 `/demo/ai/chat/stream`（请求体 `{message, sessionId?}`），按 SSE 事件名分发处理 `session` / `think` / `answer` / `tool_start` / `tool_end` / `tool_error` / `done` / `error` 事件；首个 `session` 事件的 sessionId 存入内存，后续轮次自动携带。

#### Scenario: 发送消息并接收流式回答
- **WHEN** 已登录用户在对话页输入消息并发送
- **THEN** 页面出现用户消息气泡，随后 assistant 回合随 SSE 事件流逐步渲染，收到 `done` 后该回合标记完成

#### Scenario: 新会话与续聊
- **WHEN** 页面加载后首次发送消息（无 sessionId）
- **THEN** 请求体不含 sessionId；收到 `session` 事件后保存其 sessionId，之后发送的消息均携带该 sessionId

#### Scenario: 服务端错误事件
- **WHEN** 收到 `error` 事件
- **THEN** 该 assistant 回合标记完成，并展示错误消息内容

#### Scenario: 中断回答
- **WHEN** 流式进行期间用户点击"停止"
- **THEN** 前端中断 SSE 连接，已接收内容保留，该回合标记完成

### Requirement: ReAct 过程分段可视化
系统 SHALL 将一个 assistant 回合组装为按事件到达顺序排列的 segments（think / tool / answer 三类），并按类型区别展示：think 段为可折叠的"思考过程"块（默认流式期间展开、完成后折叠）；tool 段为卡片，展示工具名、入参（args）、结果（result）与状态（运行中/成功/失败）。

#### Scenario: 思考过程展示
- **WHEN** 收到 `think` 事件（delta）
- **THEN** 思考内容以纯文本（pre-wrap）增量追加到当前 think 段，与正式正文视觉区分

#### Scenario: 工具调用展示
- **WHEN** 收到 `tool_start {tool, args}`，随后收到对应 `tool_end {tool, result, isError}`
- **THEN** 页面出现以该工具名命名的卡片，展示入参与结果；`isError` 为 true 或收到 `tool_error` 时卡片标记为失败状态

#### Scenario: 同名工具多次调用
- **WHEN** 同一回合内同一工具被多次调用
- **THEN** 每次调用生成独立的 tool 卡片，`tool_end` 按"最早未闭合的同名段"配对

#### Scenario: 多轮 ReAct 交错
- **WHEN** 事件序列为 think → tool_start → tool_end → think → answer
- **THEN** 页面按此顺序展示两个独立 think 段、一个 tool 卡片、一个 answer 段，不按类型归并

### Requirement: 正文 Markdown 渲染
系统 SHALL 使用 marked（GFM）渲染 answer 段正文，支持标题、列表、加粗、表格、代码块；代码块使用 highlight.js 高亮；模型输出中的原始 HTML SHALL 被禁用或转义，不得注入 DOM。

#### Scenario: markdown 正文流式渲染
- **WHEN** 收到 `answer` 事件（delta）
- **THEN** 当前 answer 段的全文重新经 marked 渲染并展示，用户看到正文逐段增长

#### Scenario: XSS 防护
- **WHEN** 模型输出包含原始 HTML（如 `<img onerror=...>`）
- **THEN** 该 HTML 以文本形式展示或被移除，不执行任何脚本

### Requirement: 会话生命周期
系统 SHALL 不提供历史会话功能：sessionId 仅存于内存，刷新页面即为新会话；对话页提供"新会话"按钮，点击后清空消息列表与 sessionId。

#### Scenario: 刷新页面
- **WHEN** 用户刷新对话页
- **THEN** 消息列表清空，下一条消息以无 sessionId 方式发起新会话（登录态不受影响）

#### Scenario: 手动新会话
- **WHEN** 用户点击"新会话"按钮
- **THEN** 消息列表与内存中的 sessionId 被清空，新一轮对话从空会话开始
