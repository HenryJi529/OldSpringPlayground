package com.morningstar.old.demo.controller;

import com.morningstar.old.demo.pojo.po.Enterprise;
import com.morningstar.old.infra.exception.BaseException;
import com.morningstar.old.infra.response.ResponseCode;
import com.morningstar.old.system.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.server.IMcpServerEndpoint;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.annotation.Param;
import org.noear.solon.core.handle.Context;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 企业信息 MCP 服务端点（enableOutputSchema：工具声明携带 outputSchema，结果附带 structuredContent）
 */
@Slf4j
@McpServerEndpoint(channel = McpChannel.STREAMABLE, name = "enterprise-mcp", mcpEndpoint = "/mcp/enterprise", enableOutputSchema = true)
@Component
@RequiredArgsConstructor
public class EnterpriseMcpController implements IMcpServerEndpoint {

    private final JwtUtil jwtUtil;

    /**
     * 模糊匹配企业名：返回匹配到的企业列表（只含id和名称，不含具体数据，无需权限）
     */
    @ToolMapping(description = "根据关键字模糊匹配企业名称，返回匹配到的企业列表（只含id和名称）")
    public EnterpriseSearchResult searchEnterprise(@Param(description = "企业名称关键字") String keyword) {
        log.info("EnterpriseMcpController.searchEnterprise keyword={}", keyword);
        simulateLatency();
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BaseException("关键字不能为空");
        }

        String kw = keyword.trim().toLowerCase();
        List<Enterprise> matched = EnterpriseStore.DATA.values().stream()
                .filter(e -> e.getName().toLowerCase().contains(kw))
                // 搜索只是发现入口：复制一份只带id和名称的壳，具体数据和管户账号不出这个接口
                .map(e -> new Enterprise(e.getId(), e.getName(), null, null))
                .collect(Collectors.toList());

        return new EnterpriseSearchResult(matched);
    }

    /**
     * 按企业id查询企业数据：解析 token → 账号 → 校验该账号是否为企业的管户
     */
    @ToolMapping(description = "根据企业id查询企业具体数据（需要调用方已认证，且调用方必须是该企业的管户）")
    public Enterprise getEnterprise(@Param(description = "企业id") Long enterpriseId) {
        log.info("EnterpriseMcpController.getEnterprise enterpriseId={}", enterpriseId);
        simulateLatency();
        String account = jwtUtil.getAccount(callerToken());

        Enterprise enterprise = EnterpriseStore.DATA.get(enterpriseId);
        if (enterprise == null) {
            throw new BaseException("企业[" + enterpriseId + "]不存在");
        }

        // 数据权限：只有管户（权限账号集合内）才能查看该企业的数据
        if (!enterprise.getManagers().contains(account)) {
            log.warn("账号 {} 试图查询企业[{}]的数据，但管户是 {}", account, enterpriseId, enterprise.getManagers());
            throw new BaseException(ResponseCode.NO_PERMISSION);
        }

        return enterprise;
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
     * 企业搜索结果
     *
     * <p>不直接返回 {@code List<Enterprise>} 的原因：MCP 规范要求 outputSchema 根类型必须是
     * object；且 solon 填充 structuredContent 时按 Map 转换（ONode bean→Map），List 根会转失败。</p>
     */
    @Data
    @AllArgsConstructor
    public static class EnterpriseSearchResult {
        /**
         * 匹配到的企业列表（只含id和名称）
         */
        @Param(description = "匹配到的企业列表（只含id和名称）")
        private List<Enterprise> enterprises;
    }

    /**
     * 以 static 变量为数据源的内存企业数据存储（mock）
     * key = 企业id
     */
    public static class EnterpriseStore {
        private static final Map<Long, Enterprise> DATA = new ConcurrentHashMap<>();

        static {
            DATA.put(1L, new Enterprise(1L, "晨星科技有限公司", "注册资本5000万，主营软件开发，员工120人，年营收8000万", managerSet("100000", "100001")));
            DATA.put(2L, new Enterprise(2L, "晨星数据服务有限公司", "注册资本2000万，主营数据服务，员工45人，年营收3000万", managerSet("100001")));
            DATA.put(3L, new Enterprise(3L, "晨光生物科技有限公司", "注册资本8000万，主营生物医药研发，员工200人，年营收1.5亿", managerSet("100002")));
            DATA.put(4L, new Enterprise(4L, "老泉贸易有限公司", "注册资本1000万，主营进出口贸易，员工30人，年营收5000万", managerSet("100000", "100002")));
            DATA.put(5L, new Enterprise(5L, "晨光餐饮管理有限公司", "注册资本500万，主营连锁餐饮，员工80人，年营收2000万", managerSet("100001")));
        }

        private static Set<String> managerSet(String... accounts) {
            return new HashSet<>(Arrays.asList(accounts));
        }
    }

}
