## MODIFIED Requirements

### Requirement: 通过调用方 token 鉴定用户身份
系统 SHALL 从当前 MCP 请求的 Authorization 头读取 mcp-token（RS256，见 `mcp-auth` capability），使用 `McpTokenUtil.parseToken` 验签（含 iss 名册、签名、过期校验）并解析出 `sub` 作为当前用户账号；解析失败时拒绝访问并返回明确的未认证错误。

#### Scenario: 有效 mcp-token 解析出身份
- **WHEN** 传入由已登记签发方私钥签发、签名有效且未过期的 mcp-token，其 `sub` 为 100000
- **THEN** 系统判定当前用户为账号 100000

#### Scenario: 无效 token 被拒绝
- **WHEN** 传入签名无效、已过期、或 iss 未登记的 token
- **THEN** 系统返回明确的未认证错误，且不返回、不修改任何数据
