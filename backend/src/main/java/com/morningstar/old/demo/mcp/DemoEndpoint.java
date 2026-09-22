package com.morningstar.old.demo.mcp;

import com.morningstar.old.demo.pojo.bo.DaasFieldDictItem;
import com.morningstar.old.infra.exception.BaseException;
import com.morningstar.old.infra.response.ResponseCode;
import com.morningstar.old.infra.ai.McpServerUtil;
import com.morningstar.old.infra.util.DaasDictUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.server.IMcpServerEndpoint;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.annotation.Param;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 服务端点（enableOutputSchema：工具声明携带 outputSchema，结果附带 structuredContent）
 */
@Slf4j
@McpServerEndpoint(channel = McpChannel.STREAMABLE, name = "demo", mcpEndpoint = "/mcp/demo", enableOutputSchema = true)
@Component
@RequiredArgsConstructor
public class DemoEndpoint implements IMcpServerEndpoint {

    private final McpServerUtil mcpServerUtil;
    private final DaasDictUtil daasDictUtil;

    /**
     * 模糊匹配企业名：返回匹配到的企业列表（只含id和名称，不含具体数据，无需权限）
     */
    @ToolMapping(title = "搜索企业", description = "根据关键字模糊匹配企业名称，返回匹配到的企业列表（只含id和名称）")
    public EnterpriseSearchResult searchEnterprise(@Param(description = "企业名称关键字", required = false) String nameKeyword, @Param(description = "企业类型", required = false) String type) {
        simulateLatency();

        String daasId = "search_enterprise";
        HashMap<String, Object> map = new HashMap<>();
        if(nameKeyword != null && !nameKeyword.isEmpty()) {
            map.put("nameKeyword", nameKeyword);
        }
        if(type != null && !type.isEmpty()) {
            map.put("type", daasDictUtil.inputValueToCode(daasId, "type", type));
        }
        List<Enterprise> matched = EnterpriseStore.search(map);
        matched.forEach(e -> {
            e.setType(daasDictUtil.outputCodeToValue(daasId, "type", e.getType()));
            List<DaasFieldDictItem> candidates = Arrays.asList(
                    DaasFieldDictItem.builder().code("1").label("已上市").build(),
                    DaasFieldDictItem.builder().code("0").label("未上市").build()
            );
            e.setStatus(daasDictUtil.outputCodeToValue(candidates, e.getStatus()));
        });
        return new EnterpriseSearchResult(matched);
    }

    /**
     * 按企业id查询企业数据：解析 mcp-token → 账号 → 校验该账号是否为企业的管户
     */
    @ToolMapping(title = "查询指定企业", description = "根据企业id查询企业具体数据（需要调用方已认证，且调用方必须是该企业的管户）")
    public Enterprise getEnterprise(@Param(description = "企业id") String enterpriseId) {
        simulateLatency();

        String account = mcpServerUtil.getAccount();
        if (account == null) {
            throw new BaseException(ResponseCode.AUTHENTICATION_FAILED);
        }

        Enterprise enterprise = EnterpriseStore.DATA.stream().filter(e -> enterpriseId.equals(e.getId())).findFirst().orElse(null);
        if (enterprise == null) {
            throw new BaseException("企业[" + enterpriseId + "]不存在");
        }

        // 数据权限：只有管户（权限账号集合内）才能查看该企业的数据
        if (!enterprise.getManagers().contains(account)) {
            log.warn("账号 {} 试图查询企业[{}]的数据，但管户是 {}", account, enterpriseId, enterprise.getManagers());
            throw new BaseException(ResponseCode.NO_PERMISSION);
        }

        enterprise.setType(daasDictUtil.outputCodeToValue("get_enterprise", "type", enterprise.getType()));

        return enterprise;
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
        private static final List<Enterprise> DATA = Arrays.asList(
                new Enterprise("1", "晨星科技有限公司", "01", "1", "注册资本5000万，主营软件开发，员工120人，年营收8000万", managerSet("100000", "100001")),
                new Enterprise("2", "晨星数据服务有限公司", "01", "0", "注册资本2000万，主营数据服务，员工45人，年营收3000万", managerSet("100001")),
                new Enterprise("3", "晨光生物科技有限公司", "02", "1", "注册资本8000万，主营生物医药研发，员工200人，年营收1.5亿", managerSet("100002")),
                new Enterprise("4", "老泉贸易有限公司", "03", "0", "注册资本1000万，主营进出口贸易，员工30人，年营收5000万", managerSet("100000", "100002")),
                new Enterprise("5", "晨光餐饮管理有限公司", "04", "1", "注册资本500万，主营连锁餐饮，员工80人，年营收2000万", managerSet("100001"))
        );

        private static Set<String> managerSet(String... accounts) {
            return new HashSet<>(Arrays.asList(accounts));
        }

        public static List<Enterprise> search(HashMap<String, Object> paramMap){
            String nameKeyword = (String) paramMap.get("nameKeyword");
            String type = (String) paramMap.get("type");
            return EnterpriseStore.DATA.stream()
                    .filter(e -> nameKeyword == null || nameKeyword.trim().isEmpty() || e.getName().contains(nameKeyword.trim()))
                    .filter(e -> type == null || type.isEmpty() || e.getType().equals(type))
                    // 搜索只是发现入口：复制一份只带id和名称的壳，具体数据和管户账号不出这个接口
                    .map(e -> new Enterprise(e.getId(), e.getName(), e.getType(), e.getStatus(), null, null))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 输出 POJO 的字段一律 @Param(description = "...", required = false)，入参才用默认的 required=true。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "企业信息")
    public static class Enterprise {

        @Schema(description = "企业id")
        @Param(description = "企业id", required = false)
        private String id;

        @Schema(description = "企业名称")
        @Param(description = "企业名称", required = false)
        private String name;

        @Schema(description = "企业类型")
        @Param(description = "企业类型", required = false)
        private String type;

        @Schema(description = "上市状态")
        @Param(description = "上市状态", required = false)
        private String status;

        @Schema(description = "企业具体数据")
        @Param(description = "企业具体数据", required = false)
        private String data;

        @Schema(description = "管户账号集合")
        @Param(description = "管户账号集合", required = false)
        private Set<String> managers;
    }

}
