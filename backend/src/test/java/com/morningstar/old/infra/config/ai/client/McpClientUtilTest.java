package com.morningstar.old.infra.config.ai.client;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class McpClientUtilTest {
    private final McpClientUtil mcpClientUtil;
    @Test
    public void testCreateToken() {
        System.out.println(mcpClientUtil.createToken("100001"));
    }
}
