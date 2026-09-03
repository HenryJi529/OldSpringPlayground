package com.morningstar.old.demo.controller;

import com.morningstar.old.system.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.server.IMcpServerEndpoint;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.annotation.Param;
import org.noear.solon.core.handle.Context;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@McpServerEndpoint(channel = McpChannel.STREAMABLE, name = "person-mcp", mcpEndpoint = "/mcp/person")
@Component
@RequiredArgsConstructor
public class PersonMcpController implements IMcpServerEndpoint {

    private final JwtUtil jwtUtil;

    /**
     * 查看数据：解析调用方 token → 账号 → 返回该账号自己的数据
     */
    @ToolMapping(description = "查看数据（需要调用方已认证，返回该账号自己的数据）")
    public String viewData() {
        simulateLatency();
        String account = jwtUtil.getAccount(callerToken());
        String data = AppDataStore.get(account);
        return data == null ? "账号 " + account + " 暂无数据" : data;
    }

    /**
     * 修改数据：解析调用方 token → 账号 → 仅修改该账号自己的数据
     */
    @ToolMapping(description = "修改数据（需要调用方已认证，仅修改该账号自己的数据）")
    public String modifyData(@Param(description = "新数据") String value) {
        simulateLatency();
        return AppDataStore.set(jwtUtil.getAccount(callerToken()), value);
    }

    /**
     * 取调用方凭证：从当前 MCP 请求的 Authorization 头读取（工具执行期间 Context 已绑定到当前请求）
     */
    private static String callerToken() {
        return Context.current().header(HttpHeaders.AUTHORIZATION);
    }

    /**
     * 模拟工具执行耗时
     */
    private static void simulateLatency() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 以 static 变量为数据源的内存数据存储（按账号隔离一份数据）
     * 每个账号只有一份数据，模拟"数据权限"：调用方只能查看/修改自己的那份
     */
    public static class AppDataStore {
        /**
         * key = 账号（account），value = 该账号自己的数据
         */
        private static final Map<String, String> DATA = new ConcurrentHashMap<>();

        static {
            DATA.put("100000", "王二的初始数据");
            DATA.put("100001", "张三的初始数据");
            DATA.put("100002", "李四的初始数据");
        }

        public static String get(String account) {
            return DATA.get(account);
        }

        public static String set(String account, String value) {
            DATA.put(account, value);
            return value;
        }
    }

}
