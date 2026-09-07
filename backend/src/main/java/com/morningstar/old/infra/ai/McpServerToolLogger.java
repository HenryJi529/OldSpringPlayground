package com.morningstar.old.infra.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.noear.solon.ai.annotation.ToolMapping;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * MCP server 侧的工具调用日志（与 client 侧的 {@link McpClientToolLogger} 对应）：
 * 环绕所有 @ToolMapping 方法，在 server 接受工具请求时打印入参，结束时打印结果与耗时。
 *
 * <p>原理：端点是 Spring bean，McpServerConfig 注册时传给 MethodToolProvider 的是代理实例
 * （已用 AopUtils.getTargetClass 取注解），反射调用会经过本切面。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class McpServerToolLogger {

    private final McpServerUtil mcpServerUtil;

    @Around("@annotation(org.noear.solon.ai.annotation.ToolMapping)")
    public Object logToolCall(ProceedingJoinPoint pjp) throws Throwable {
        String tool = getToolTitleOrName(pjp);
        String account = mcpServerUtil.getAccount();
        String issuer = mcpServerUtil.getIssuer();
        String sessionId = mcpServerUtil.getSessionId();

        log.info("[MCP-SERVER][{}] {}({}) 调用 '{}': {}", sessionId, account, issuer, tool, formatArgs(pjp));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("[MCP-SERVER] '{}' 返回({}ms): {}", tool, System.currentTimeMillis() - start, result);
            return result;
        } catch (Throwable e) {
            log.warn("[MCP-SERVER] '{}' 异常({}ms): {}", tool, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /**
     * 取 @ToolMapping 的 title 作为日志里的工具名（与 client 侧打印的显示名对齐）；
     * 未配 title 时退化为方法名。注解声明在目标类方法上，代理方法上可能取不到，故先解析出最具体方法。
     */
    private String getToolTitleOrName(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        ToolMapping mapping = AnnotationUtils.findAnnotation(
                AopUtils.getMostSpecificMethod(method, pjp.getTarget().getClass()), ToolMapping.class);
        String title = mapping == null ? null : mapping.title();
        return (title == null || title.isEmpty()) ? method.getName() : title;
    }

    /**
     * 把入参格式化成 {参数名=值, ...}（对齐 client 侧的 Map 打印风格）；
     * 取不到参数名时（未开 -parameters 编译）退化为位置下标 arg0/arg1/...
     */
    private String formatArgs(ProceedingJoinPoint pjp) {
        Object[] values = pjp.getArgs();
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = (names != null && i < names.length) ? names[i] : "arg" + i;
            sb.append(name).append('=').append(values[i]);
        }
        return sb.append('}').toString();
    }
}
