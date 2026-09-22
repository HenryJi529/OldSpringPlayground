package com.morningstar.old.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 服务方配置（server 视角）：信任名册。
 *
 * <p>与调用方 {@link AiMcpClientProperties} 物理分开：服务方只持有各签发方的公钥，
 * 从不接触任何私钥；新调用方接入 = 对方自产密钥对 + 此处登记其公钥，无需改代码
 * （见 change: replace-mcp-auth-with-rs256 / design D2/D3）。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.ai.mcp.server")
@Data
public class AiMcpServerProperties {

    /**
     * 信任名册：iss → 该签发方的公钥
     */
    private Map<String, String> issuers = new LinkedHashMap<>();
}
