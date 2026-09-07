## ADDED Requirements

### Requirement: mcp-token 格式
mcp-token SHALL 为 RS256 签名的 JWT，带 `Bearer ` 前缀流转（前缀值可直接作为 Authorization 头）。claims 最小集合为 `iss`（签发方标识，验签路由键）、`sub`（账号，数据隔离的身份依据）、`iat`/`exp`（TTL 由调用方配置，默认 300 秒），不得携带权限、角色等其他 claims。

#### Scenario: 签发的 token 结构完整
- **WHEN** 以 account=100001 调用签发入口
- **THEN** 得到带 `Bearer ` 前缀的 RS256 JWT，claims 含 iss/sub/iat/exp，无其他业务 claims

### Requirement: 调用方签发（签名半区）
系统 SHALL 提供 `createToken(account)`：读取 `app.ai.mcp.client.signing` 配置（PKCS#8 单行 base64 私钥、issuer、ttl-seconds），以 RS256 签发满足格式要求的 mcp-token。签发 SHALL NOT 依赖服务方验签半区的任何配置。

#### Scenario: 签发可用 token
- **WHEN** 配置了合法私钥与 issuer，调用 `createToken("100001")`
- **THEN** 返回的 token 能被验签半区用对应公钥验证通过，`sub` 为 100001，`iss` 为配置的 issuer

### Requirement: 服务方验签（验签半区）
系统 SHALL 提供 `parseToken(token)`：校验 `Bearer ` 前缀 → 读取 `iss` → 从 `app.ai.mcp.server.issuers` 名册查公钥（未登记即失败）→ 以该公钥验签（显式要求 alg=RS256）→ 校验 `exp` → 返回 `sub`。任一环节失败 SHALL 返回 null 或抛出带语义的异常，SHALL NOT 抛裸 NPE。验签 SHALL NOT 依赖签发半区的任何配置（两半区零耦合）。

#### Scenario: 已登记签发方的合法 token 通过
- **WHEN** token 由名册中已登记 iss 对应私钥签发、未过期、alg 为 RS256
- **THEN** `parseToken` 返回 `sub` 中的账号

#### Scenario: 未登记签发方被拒绝
- **WHEN** token 的 `iss` 不在名册中
- **THEN** `parseToken` 判定失败，并给出可区分于"签名无效"的错误信息

#### Scenario: 篡改或过期的 token 被拒绝
- **WHEN** token 签名无效、alg 非 RS256、或已过期
- **THEN** `parseToken` 判定失败

### Requirement: 自调用现场签发
AI 对话触发 MCP 工具调用时，本应用作为调用方 SHALL 按当前请求的 account（来自 SecurityContext）现场签发 mcp-token 并放入 Authorization 头，SHALL NOT 透传用户的系统登录 token。

#### Scenario: 工具调用携带现场签发的 mcp-token
- **WHEN** 账号 100001 的用户发起 AI 对话并触发 MCP 工具调用
- **THEN** MCP 请求的 Authorization 头是 `sub=100001`、`iss` 为本应用 issuer 的新签 mcp-token，而非用户的系统 JWT

### Requirement: 工具内身份鉴定
MCP 工具方法 SHALL 通过 `parseToken` 从当前请求的 Authorization 头解析账号，并据此做按账号的数据隔离；无 token 或 token 非法时 SHALL 返回明确的未认证错误信息。

#### Scenario: 合法 mcp-token 取出身份
- **WHEN** 工具收到 `sub=100001` 的合法 mcp-token
- **THEN** 工具以 100001 为数据范围执行

#### Scenario: 缺失 token 给出明确错误
- **WHEN** 请求未携带 Authorization 头
- **THEN** 工具返回明确的未认证错误，而非裸 NPE 或栈信息

### Requirement: mcp-token 签发入口
系统 SHALL 提供需认证的 HTTP 签发接口（`POST /demo/ai/mcp/token`），**仅以当前登录用户的身份**（SecurityContext 中的 account）签发 mcp-token，SHALL NOT 接受 account 参数，以防替他人铸 token。该接口是第三方 MCP 调用方（如 Claude Code）的取凭证入口，且 SHALL 与自调用链路（现场签发）共用同一个 `createToken` 实现。

#### Scenario: 登录用户换取自己的 mcp-token
- **WHEN** 账号 100001 的用户携系统 token 调用签发接口
- **THEN** 返回 `sub=100001` 的 mcp-token，可用它直接调用 MCP 工具并以 100001 为身份执行

#### Scenario: 无法为他人铸 token
- **WHEN** 调用方试图在请求中指定其他 account
- **THEN** 接口不接收该参数，签发的 token 的 `sub` 仍为调用者自己的账号

### Requirement: 配置结构
`app.ai.mcp` 配置 SHALL 分为两个物理分开的半区：`client.endpoints`（MCP server 名册，替代原 `servers`）、`client.signing`（签发配置）、`server.issuers`（iss → 公钥信任名册）。密钥 SHALL 通过环境变量注入，配置文件只存占位符。

#### Scenario: 名册支持多调用方
- **WHEN** 名册中配置了多个 iss（如 self、lingxi、guangnian）各自对应的公钥
- **THEN** 任一已登记 iss 签发的合法 token 均可验签通过，未登记的被拒绝
