package com.morningstar.old.infra.config.ai.client;

import com.morningstar.old.infra.properties.AiMcpClientProperties;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class McpClientUtilTest {
    private final McpClientUtil mcpClientUtil;
    private final AiMcpClientProperties clientProperties;
    @Test
    public void testCreateToken() {
        long ttl = 604800;
        clientProperties.getSigning().setTtlSeconds(ttl);
        String token = mcpClientUtil.createToken("100001");
        Claims claims = mcpClientUtil.parseToken(token);
        assert claims.getExpiration().getTime()-claims.getIssuedAt().getTime() == ttl*1000;
        log.info(token);
    }
}
