package com.morningstar.old.infra.ai;

import com.morningstar.old.infra.properties.AiMcpClientProperties;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.client.McpClientProvider;
import org.noear.solon.net.http.HttpUtils;
import org.noear.solon.net.http.HttpUtilsFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MCP 客户端注册表：按名字管理多个 MCP client。
 *
 * <p>启动期为配置里的每个 MCP server 预建一个 {@link McpClientProvider}
 * （懒连接，首个请求时才完成 initialize 握手，启动期不会 Connection refused）；
 * 业务侧按名字取用并注入 ChatModel。</p>
 */
@Component
public class McpClients implements DisposableBean {

    private final Map<String, McpClientProvider> providers;

    public McpClients(AiMcpClientProperties properties) {
        Map<String, McpClientProvider> map = new LinkedHashMap<>();
        properties.getEndpoints().forEach((name, url) ->
                map.put(name, McpClientProvider.builder()
                        .channel(McpChannel.STREAMABLE)
                        .url(url)
                        // 超时（默认 30s 太短，模型慢时自调用端点排队会触发
                        // "TimeoutException: 30000ms in 'source(MonoDeferContextual)'"）：
                        // 一个 timeout 同时驱动 HTTP 超时和 requestTimeout/initializationTimeout 的回落值
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        // 凭证按 MCP 规范走 Authorization 请求头：
                        // httpFactory 每次发请求都会被调用，从 ThreadLocal 取当前请求的 token 写进头
                        // （无 token 时如 initialize/tools/list 握手请求则不带）
                        .httpFactory(tokenRelayFactory())
                        .build()));
        this.providers = Collections.unmodifiableMap(map);
    }

    /**
     * 按名字取 MCP client（未配置时抛异常，便于尽早发现配置错误）
     */
    public McpClientProvider get(String name) {
        McpClientProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("未配置名为 " + name + " 的 MCP server，已配置: " + providers.keySet());
        }
        return provider;
    }

    /**
     * 已配置的 MCP server 名字
     */
    public Set<String> names() {
        return providers.keySet();
    }

    /**
     * 带凭证透传的 HttpUtils 工厂：发请求时若 {@link McpTokenHolder} 里有 token，
     * 则作为 Authorization 头带上（token 由 {@link TokenRelayInterceptor} 在工具调用期间写入）
     */
    private static HttpUtilsFactory tokenRelayFactory() {
        return url -> {
            HttpUtils http = HttpUtils.http(url);
            String token = McpTokenHolder.get();
            if (token != null) {
                http.header(HttpHeaders.AUTHORIZATION, token);
            }
            return http;
        };
    }

    @Override
    public void destroy() {
        providers.values().forEach(McpClientProvider::close);
    }
}
