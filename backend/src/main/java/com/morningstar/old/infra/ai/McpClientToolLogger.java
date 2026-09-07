package com.morningstar.old.infra.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpClientToolLogger implements ToolEventListener {
    private final McpClientUtil mcpClientUtil;

    @Override
    public void onToolStart(String tool, Map<String, Object> args) {
        String account = mcpClientUtil.getAccount();

        log.info("[MCP-CLIENT] {} 调用 '{}': {}", account, tool, args);
    }

    @Override
    public void onToolEnd(String tool, String result, boolean isError) {

    }

    @Override
    public void onToolError(String tool, String error) {

    }
}
