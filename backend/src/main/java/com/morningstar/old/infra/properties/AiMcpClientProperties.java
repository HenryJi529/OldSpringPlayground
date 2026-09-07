package com.morningstar.old.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 调用方半区配置（client 视角）：服务端点 + mcp-token 签发。
 *
 * <p>与服务方半区 {@link AiMcpServerProperties} 物理分开：当前两者同应用仅因自调用，
 * 将来拆系统时本类整块跟调用方走（见 change: replace-mcp-auth-with-rs256 / design D2/D3）。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.ai.mcp.client")
@Data
public class AiMcpClientProperties {

    /**
     * MCP server 列表：key = 名字（业务侧按名字取用），value = 端点 url
     */
    private Map<String, String> endpoints = new LinkedHashMap<>();

    /**
     * MCP 请求超时（秒）
     */
    private long timeoutSeconds;

    /**
     * mcp-token 签发配置（本应用作为调用方时持有）
     */
    private Signing signing = new Signing();

    @Data
    public static class Signing {
        /**
         * RSA 私钥
         */
        private String privateKey;

        /**
         * RSA 公钥
         */
        private String publicKey;

        /**
         * 本应用作为调用方时的签发方标识（iss），需与服务方信任名册中的 key 对应
         */
        private String issuer;

        /**
         * mcp-token 有效期（秒）：现场签发、用完即弃，宜短
         */
        private long ttlSeconds = 300;
    }
}
