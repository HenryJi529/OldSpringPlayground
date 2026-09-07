# Tasks

## 1. 密钥与配置

- [x] 1.1 生成 RSA 2048 密钥对（PKCS#8 私钥 / X.509 公钥，单行 base64 正文），写入 gitignore 的 `env.properties`（`MCP_PRIVATE_KEY` / `MCP_PUBLIC_KEY`）
- [x] 1.2 配置类按半区拆分：`AiMcpClientProperties`（`app.ai.mcp.client`：endpoints + signing）与 `AiMcpServerProperties`（`app.ai.mcp.server`：issuers）
- [x] 1.3 `application.yml` 同步为新结构（`signing.private-key`/`issuers.self` 走环境变量占位符）

## 2. McpTokenUtil

- [x] 2.1 新增 `McpTokenUtil`：`createToken(account)`（私钥签名半区）与 `parseToken(token)`（公钥验签半区），两半区配置与代码路径互不依赖
- [x] 2.2 `createToken`：RS256 签发，claims = iss/sub/iat/exp，返回带 `Bearer ` 前缀的完整值
- [x] 2.3 `parseToken`：剥前缀 → SigningKeyResolver 按 iss 路由名册公钥（未登记即失败）→ 验签（显式 RS256）→ 校验 exp；失败返回 null 并记日志，杜绝裸 NPE

## 3. 调用链改造

- [x] 3.1 `TokenRelayInterceptor` 保持"中转 token"职责，由 controller 现场签发后传入（注释语义已更新）
- [x] 3.2 `AiDemoController.bindToken`：不再读 HTTP Authorization 头，改用 `AuthUtil.getUserId()` 拿 account 调 `createToken` 签发；端点方法的 `HttpServletRequest` 参数随之移除
- [x] 3.3 `McpClients` 无需改动（httpFactory 从 `McpTokenHolder` 取值的机制不变，只是 ThreadLocal 里的值从系统 token 变为 mcp-token）

## 4. 服务方工具改造

- [x] 4.1 `PersonMcpController`：`jwtUtil.getAccount(...)` → `mcpTokenUtil.getAccount(...)`，未认证抛 `BaseException(AUTHENTICATION_FAILED)`
- [x] 4.2 `EnterpriseMcpController`：同上
- [x] 4.3 `JwtAuthenticationFilter` 对 `/mcp/**` 增加 `shouldNotFilter` 跳过（系统 JWT 过滤器会误杀 RS256 mcp-token：白名单只管授权层，OncePerRequestFilter 对每个请求都执行）

## 5. 签发入口

- [x] 5.1 `AiDemoController` 新增 `POST /demo/ai/mcp/token`：以当前登录用户身份签发 mcp-token（不接受 account 参数），走正常认证不进白名单；与真实链路共用 `createToken`

## 6. 验证

- [x] 6.1 编译通过（仅存量 Java 8 obsolete 警告）
- [x] 6.2 单元回环：`McpTokenUtilTest` 4/4 通过（签发验签回环 / 篡改拒绝 / 未登记 iss 拒绝 / 畸形 token 拒绝）
- [ ] 6.3 应用启动正常（内嵌 Solon + MCP 端点注册无异常）
- [ ] 6.4 全链路：登录拿系统 token → `/demo/ai/chat/sync` 触发工具调用 → 数据按账号隔离正确返回
- [ ] 6.5 调试链路：调 `/demo/mcp/token` 拿 mcp-token → 直接调 `/mcp/person` 工具 → 验签通过、身份正确
- [ ] 6.6 负向：无 token / 篡改 token / 过期 token，工具均返回明确未认证错误
