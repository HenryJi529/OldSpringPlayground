package com.morningstar.old.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 服务端点配置：按名字维护多个 MCP server 地址
 */
@Component
@ConfigurationProperties(prefix = "app.ai.mcp")
@Data
public class AiMcpProperties {

    /**
     * MCP server 列表：key = 名字（业务侧按名字取用），value = 端点 url
     */
    private Map<String, String> servers = new LinkedHashMap<>();
}
