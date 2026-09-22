package com.morningstar.old.infra.ai;

import com.morningstar.old.infra.util.DaasDictCacheHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 为每次 MCP 工具调用开启Daas字典请求缓存（{@link DaasDictCacheHolder}），
 * 调用结束在 finally 中清理，避免缓存残留在池化线程上串请求。
 *
 * <p>与 {@link McpServerToolLogger} 同一切点；工具执行与切面在同一线程，ThreadLocal 可见。</p>
 */
@Aspect
@Component
public class DaasDictCacheAspect {

    @Around("@annotation(org.noear.solon.ai.annotation.ToolMapping)")
    public Object scopeCache(ProceedingJoinPoint pjp) throws Throwable {
        DaasDictCacheHolder.begin();
        try {
            return pjp.proceed();
        } finally {
            DaasDictCacheHolder.clear();
        }
    }
}