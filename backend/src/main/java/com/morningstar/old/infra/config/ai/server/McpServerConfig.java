package com.morningstar.old.infra.config.ai.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.Feature;
import org.noear.snack4.Options;
import org.noear.solon.Solon;
import org.noear.solon.ai.chat.prompt.MethodPromptProvider;
import org.noear.solon.ai.chat.resource.MethodResourceProvider;
import org.noear.solon.ai.chat.tool.MethodToolProvider;
import org.noear.solon.ai.mcp.server.IMcpServerEndpoint;
import org.noear.solon.ai.mcp.server.McpServerEndpointProvider;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.web.servlet.SolonServletFilter;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Field;
import java.util.List;

/**
 * 内嵌 Solon，把实现了 IMcpServerEndpoint 的 Spring 组件注册为 MCP 服务端点。
 * 独立一个目录，让 Solon 扫描范围最小化；通过 {@link SolonServletFilter} 把 /mcp/* 路由进 Solon。
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class McpServerConfig {

    private final List<IMcpServerEndpoint> serverEndpoints;

    @PostConstruct
    public void start() {
        enableSnack4WriteNulls();

        // 启动内嵌 Solon（禁用组件扫描，端点由 springCom2Endpoint 手动收集）
        Solon.start(McpServerConfig.class, new String[]{}, app -> app.enableScanning(false));

        springCom2Endpoint();
    }

    /**
     * 让 MCP 响应保留 null 字段。
     *
     * <p>snack4 默认不序列化 null 字段，而 solon-ai-mcp 的两条结果链路都写死用默认选项：
     * content 文本（{@code ToolCallResultConverterDefault} → ONode.serialize）和
     * structuredContent（{@code McpResultResponder} → ONode.ofBean().toBean(Map)），
     * 导致 LLM 分不清"字段没返回"和"字段为空"。structuredContent 链路没有官方扩展点，
     * 只能给全局默认选项 {@link Options#DEF_OPTIONS} 追加 {@link Feature#Write_Nulls}；
     * 该对象是只读的，故反射临时放开。升级 snack4 若失效，最坏退回默认行为（丢 null），不会报错。</p>
     */
    private static void enableSnack4WriteNulls() {
        try {
            Field readonlyField = Options.class.getDeclaredField("readonly");
            readonlyField.setAccessible(true);
            readonlyField.setBoolean(Options.DEF_OPTIONS, false);
            Options.DEF_OPTIONS.addFeatures(Feature.Write_Nulls);
            readonlyField.setBoolean(Options.DEF_OPTIONS, true);
        } catch (Exception e) {
            log.warn("开启 snack4 Write_Nulls 失败，MCP 响应将忽略 null 字段", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (Solon.app() != null) {
            // 停止 solon（根据配置，可支持两段式安全停止）
            Solon.stopBlock(false, Solon.cfg().stopDelay());
        }
    }

    /**
     * 提取容器里实现 IMcpServerEndpoint 接口的 bean，注册为 MCP 服务端点
     */
    protected void springCom2Endpoint() {
        for (IMcpServerEndpoint serverEndpoint : serverEndpoints) {
            Class<?> serverEndpointClz = AopUtils.getTargetClass(serverEndpoint);
            McpServerEndpoint anno = AnnotationUtils.findAnnotation(serverEndpointClz, McpServerEndpoint.class);

            if (anno == null) {
                continue;
            }

            McpServerEndpointProvider serverEndpointProvider = McpServerEndpointProvider.builder()
                    .from(serverEndpointClz, anno)
                    .mcpEndpoint(anno.mcpEndpoint()) // NOTE: 可以额外配置context-path
                    .build();

            serverEndpointProvider.addTool(new MethodToolProvider(serverEndpointClz, serverEndpoint));
            serverEndpointProvider.addResource(new MethodResourceProvider(serverEndpointClz, serverEndpoint));
            serverEndpointProvider.addPrompt(new MethodPromptProvider(serverEndpointClz, serverEndpoint));

            serverEndpointProvider.postStart();
            // MCP 放入 Solon 上下文
            Solon.context().wrapAndPut("MCP-" + serverEndpointProvider.getName(), serverEndpointProvider);
        }
    }

    @Bean
    public FilterRegistrationBean<SolonServletFilter> mcpServerFilter() {
        // 通过 Servlet Filter 实现 http 能力对接
        FilterRegistrationBean<SolonServletFilter> filter = new FilterRegistrationBean<>();
        filter.setName("SolonFilter");
        filter.addUrlPatterns("/mcp/*");
        filter.setFilter(new SolonServletFilter());
        return filter;
    }
}
