package com.morningstar.old.infra.ai;

import org.noear.solon.ai.chat.interceptor.ChatInterceptor;
import org.noear.solon.ai.chat.interceptor.ToolChain;
import org.noear.solon.ai.chat.interceptor.ToolRequest;
import org.noear.solon.ai.chat.tool.ToolResult;

/**
 * MCP 凭证透传拦截器：工具调用执行期间，把现场签发的 mcp-token 写入 {@link McpTokenHolder}，
 * 由 MCP client 的 httpFactory 读出并作为 Authorization 请求头发给 MCP server。
 *
 * <p>挂在 solon-ai 的 {@link ToolChain} 上，call 和 stream 两种模式都会经过；
 * 工具执行（含 MCP HTTP 请求的发起）与拦截器在同一线程，ThreadLocal 可见。</p>
 *
 * <p>必须按请求 new 实例（闭包捕获当前请求签发的 mcp-token），不要做成全局单例。</p>
 */
public class TokenRelayInterceptor implements ChatInterceptor {

    private final String token;

    public TokenRelayInterceptor(String token) {
        this.token = token;
    }

    @Override
    public ToolResult interceptTool(ToolRequest req, ToolChain chain) throws Throwable {
        McpTokenHolder.set(token);
        try {
            return chain.doIntercept(req);
        } finally {
            McpTokenHolder.clear();
        }
    }
}
