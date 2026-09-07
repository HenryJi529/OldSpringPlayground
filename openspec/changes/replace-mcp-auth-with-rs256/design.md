# Design: MCP 认证切换为 RS256 非对称签名

## 背景与目标

现状链路：用户系统 JWT（HS256，系统共享密钥）→ `TokenRelayInterceptor` 透传 → MCP client 放入 Authorization 头 → 工具内 `jwtUtil.getAccount()` 解析。三个耦合点：

1. **签发端耦合**：token 只能由本系统 `JwtUtil` 签发，外部系统无法构造合法 token；
2. **验证端耦合**：MCP 工具直接用系统 `JwtUtil`，MCP server 实为系统内部接口；
3. **身份来源耦合**：透传的是用户登录 token，调用方必须是"已登录用户"，不能是另一个后端系统。

目标：MCP 认证独立于系统登录体系，任何持有已登记私钥的系统都可调用。

## 关键决策

### D1: 非对称签名（RS256），而非共享密钥

信任模型从"共享同一个密钥"变为"服务方信任持有某私钥的调用方所断言的身份"。

- 私钥由调用方自己生成、自己保管，从不离开调用方环境；服务方只持有公钥。服务方被拖库，攻击者拿到的公钥无法伪造 token（对比 HS256：密钥泄漏 = 任何人可签任何账号）。
- **禁止多调用方共享同一私钥**：共享则无法归因（审计日志分不清谁调的）、吊销只能全量换钥、私钥分发过程本身扩大泄漏面。
- RS256 选型理由：jjwt 现有依赖直接支持，JDK 自带 SHA256withRSA，Java 8 兼容；Ed25519 需 BouncyCastle/Java 15+，本技术栈不折腾。密钥长度 RSA 2048。

### D2: iss → 公钥名册，而非单一公钥

token claims 最小集合：`iss`（签发方标识，验签路由键）+ `sub`（account，身份语义）+ `exp`（短 TTL）。`iss` 不是权限信息，是协议运转的必要部分——服务方靠它从名册中找到对应公钥。

- 当前名册只有 `self` 一条（自调用），结构上按多调用方设计：新调用方接入 = 该方自产密钥对 + 服务方名册加一条公钥，零代码改动。
- 配置形态（已定稿）：

```yaml
app:
  ai:
    mcp:
      client:                        # 调用方半区
        endpoints:                   #   服务发现（原 servers）
          person: "http://localhost:8088/mcp/person"
        signing:                     #   签名配置
          private-key: "${MCP_PRIVATE_KEY}"   # PKCS#8 单行 base64
          issuer: "self"
          ttl-seconds: 300
      server:                        # 服务方半区
        issuers:                     #   信任名册：iss → 公钥（X.509 单行 base64）
          self: "${MCP_PUBLIC_KEY}"
```

- 两个半区在 yml 层面物理分开：将来拆系统时 `client` 整块跟调用方走、`server` 整块跟服务方走，各自独立成立。

### D3: 单 util 承载两个半区，内部零耦合

`McpTokenUtil` 同时提供 `createToken` / `parseToken`，**仅仅因为**当前调用方与服务方同应用。约束：

- `parseToken` 不依赖 `createToken` 的任何配置与代码路径，反之亦然——将来拆成两个类/两个系统是纯移动代码，不是重构；
- token 带 `Bearer ` 前缀流转（与现有 `JwtUtil` 风格一致），`createToken` 签发时带上，`parseToken` 负责剥除，ThreadLocal 中流转的即可直接放入 Authorization 头的完整值；
- `parseToken` 失败（无前缀 / iss 未登记 / 验签失败 / 过期）返回 `null` 或抛带语义的异常，工具方法给出友好报错——`/mcp/**` 仍是白名单裸奔，无 token 请求会真实到达工具内（现状 `JwtUtil.getAccount` 的裸 NPE 借这次修掉）；
- 验签时显式校验 alg = RS256，防 alg 混淆攻击。

### D4: 验证层留在工具内（方案A），不上传输层过滤器

维持"`/mcp/**` 白名单 + 工具方法内鉴定身份"的现状形态，改动面最小，与项目既有约定一致。Solon 侧统一鉴权过滤器（方案B）形态更正，但本次不引入，留作后续演进选项。

**注意一个实现陷阱（已在实施中踩到并修复）**：`SecurityConfig` 的白名单只管授权层，`JwtAuthenticationFilter`（OncePerRequestFilter）对**每个**请求都会执行——只要请求带了 Authorization 头就会用系统 `JwtUtil`（HS256）解析，失败即短路返回 TOKEN_INVALID。透传系统 token 时代 mcp-token 恰好能被它解析所以相安无事；换 RS256 token 后 MCP 请求会被它误杀。因此该过滤器对 `/mcp/**` 显式 `shouldNotFilter` 跳过——两套 token 体系各管各的路径，互不解析。

### D5: 拦截器从"透传"改为"现场签发"

自调用场景下，mcp-token 的来源是：AI 对话请求进来 → `AuthUtil.getUserId()` 从 SecurityContext 拿 account → `createToken(account)` 现场签发短 TTL token → 塞 `McpTokenHolder`。收益：

- 系统 token 与 mcp-token 完全解耦，登录体系怎么改都不影响 MCP；
- mcp-token 短命，泄漏风险小；
- 自调用与外部调用走同一条验签路径，不自调用特殊化——协议正确性由自调用亲自验证；
- `bindToken` 不再依赖 `HttpServletRequest`，MCP 凭证与传输层解耦。

### D6: 签发入口以真实用户身份铸 token，不接受 account 参数

在 `AiDemoController` 新增 `POST /demo/ai/mcp/token`：**只以当前登录用户身份签发**（`AuthUtil.getUserId()`），不接受任何 account 参数，杜绝替他人铸 token。它不是一次性的调试接口，而是第三方 MCP 调用方（如 Claude Code）的正式取凭证入口：先 `/user/auth/login` 登录拿系统 token，再用系统 token 换 mcp-token，之后持 mcp-token 直连 `/mcp/**`。与 AI 对话链路（现场签发）共用同一个 `createToken` 实现，保证两条路径签出的 token 是同一个东西。该接口走正常认证，不进白名单。

## 身份语义的显式声明

mcp-token 中 `sub` 的 account 是**调用方系统断言的身份**，服务方无法分辨"调用方在替真实用户调用"还是"调用方伪造账号"。这不是缺陷，是信任模型的转移：服务方信任的是调用方系统这个实体（通过其登记公钥）。按 account 的数据隔离语义不变；将来若需调用方级权限范围（如只读/限定可代理账号），以 `iss` 为粒度扩展，本次不做。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 私钥进入 git / 日志 | 密钥只走环境变量，yml 中仅存 `${MCP_PRIVATE_KEY}` 占位 |
| PEM 多行文本配置出错 | 统一单行 base64 正文（去头尾/换行），加载时程序解码 |
| 时钟偏移导致 exp 误判 | jjwt 默认允许少量 clock skew；TTL 300s 足够覆盖 |
| 名册中 iss 与 token 的 iss 大小写/拼写不一致 | 精确匹配，未命中即拒绝并给出明确报错 |
