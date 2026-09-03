package com.morningstar.old.infra.ai;

/**
 * MCP 调用凭证持有者：以 ThreadLocal 暂存当前请求的调用方 token（Authorization 头原值）。
 *
 * <p>MCP client 的连接在启动期预建（{@link McpClients}），请求头是静态配置，
 * 无法按请求传 token；因此在工具调用期间由 {@link TokenRelayInterceptor}
 * 把 token 放进 ThreadLocal，httpFactory 发 HTTP 请求时（同一线程）读取并写入请求头。</p>
 *
 * <p>仅限工具调用执行窗口内有效，用完即清，不会泄漏到其他请求。</p>
 */
public final class McpTokenHolder {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private McpTokenHolder() {
    }

    public static void set(String token) {
        TOKEN.set(token);
    }

    /**
     * 当前线程持有的 token（可能为 null，如 initialize/tools/list 等握手请求）
     */
    public static String get() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}
