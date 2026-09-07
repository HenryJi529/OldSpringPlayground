## Why

当前 MCP server（`/mcp/**`）的身份鉴定直接复用系统登录 token（HS256 共享密钥的 JWT）：调用链路上透传用户的系统 JWT，工具方法内用系统 `JwtUtil` 解析出 account。这把 MCP 端点锁死成了"本系统内部接口"——外部系统没有系统密钥，永远造不出合法 token，MCP 不具备对其他系统开放的可能性。需要把 MCP 认证从系统登录体系中解耦，换成调用方自持私钥签名的非对称方案。

## What Changes

- 新增 MCP 专用认证机制：调用方持有 RSA 私钥现场签发 mcp-token（RS256，claims = `iss` + `sub(account)` + `exp`），MCP 服务方按 `iss` 查公钥验签，取 `sub` 作为数据隔离的身份依据。
- 新增 `McpTokenUtil`：承载 `createToken(account)`（签名半区，依赖私钥配置）与 `parseToken(token)`（验签半区，依赖 iss→公钥名册）两个互不依赖的方法；当前两半区同体仅因调用方与服务方同应用，配置层面物理分开。
- `TokenRelayInterceptor` 从"透传用户系统 token"改为"现场签发 mcp-token"：`bindToken` 不再读 HTTP Authorization 头，改用 `AuthUtil.getUserId()` 拿 account 后签发。
- `PersonMcpController` / `EnterpriseMcpController` 中 `jwtUtil.getAccount(...)` 替换为 `mcpTokenUtil.parseToken(...)`。
- 新增 mcp-token 签发接口（`POST /demo/ai/mcp/token`，需认证）：仅以当前登录用户身份签发，是第三方 MCP 调用方（如 Claude Code）的取凭证入口，与真实链路共用同一个 `createToken` 入口。
- **BREAKING（配置）**：`app.ai.mcp.servers` 迁移为 `app.ai.mcp.client.endpoints`；新增 `app.ai.mcp.client.signing`（private-key / issuer / ttl-seconds）与 `app.ai.mcp.server.issuers`（iss → 公钥名册）。
- 生成 RSA 2048 密钥对（PKCS#8 私钥 / X.509 公钥，单行 base64），通过环境变量注入配置。

## Capabilities

### New Capabilities
- `mcp-auth`: MCP 端点的调用方认证机制——RS256 非对称签名、iss→公钥信任名册、调用方现场签发、工具内验签取身份、调试签发入口。

### Modified Capabilities
- `static-data-mcp`: 身份鉴定依据从"系统登录 token（HS256）"变更为"mcp-token（RS256，由调用方私钥签发）"，数据隔离语义（按 account）不变。

## Impact

- **代码**：新增 `McpTokenUtil`；改动 `TokenRelayInterceptor`、`AiDemoController`（bindToken 改为现场签发 + 新增 mcp-token 签发接口）、`PersonMcpController`、`EnterpriseMcpController`；`AiMcpProperties` 拆分为 `AiMcpClientProperties` / `AiMcpServerProperties`。
- **配置**：`application.yml` 的 `app.ai.mcp` 块结构变更（servers → client.endpoints，新增 signing / issuers）；新增环境变量 `MCP_PRIVATE_KEY` / `MCP_PUBLIC_KEY`。
- **依赖**：无新增（jjwt 现有依赖即支持 RS256，JDK 自带 SHA256withRSA）。
- **安全语义**：`/mcp/**` 仍在 Spring Security 白名单，认证维持在工具方法内完成（本次不改传输层形态）；信任模型从"共享系统密钥"变为"服务方只持有公钥，私钥不出调用方环境"。
