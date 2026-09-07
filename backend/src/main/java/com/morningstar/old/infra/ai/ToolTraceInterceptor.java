package com.morningstar.old.infra.ai;

import org.noear.solon.ai.chat.interceptor.ChatInterceptor;
import org.noear.solon.ai.chat.interceptor.ToolChain;
import org.noear.solon.ai.chat.interceptor.ToolRequest;
import org.noear.solon.ai.chat.tool.ToolResult;

import java.util.Arrays;
import java.util.List;

/**
 * 工具调用轨迹拦截器：把每次工具调用转成 onToolStart / onToolEnd / onToolError 回调。
 * 挂接在 solon-ai 的 {@link ToolChain} 上，call 和 stream 两种模式都会经过。
 * 必须按请求 new 实例（闭包捕获当前请求的监听器），不要做成全局单例，否则并发请求的事件会串。
 */
public class ToolTraceInterceptor implements ChatInterceptor {

    private final List<ToolEventListener> listeners;

    public ToolTraceInterceptor(ToolEventListener... listeners) {
        this.listeners = Arrays.asList(listeners);
    }

    @Override
    public ToolResult interceptTool(ToolRequest req, ToolChain chain) throws Throwable {
        String toolName = chain.getTool().name();
        String toolTitle = chain.getTool().title();
        String tool = toolTitle.isEmpty() ? toolName: toolTitle;

        listeners.forEach(l -> l.onToolStart(tool, req.getArgs()));

        try {
            ToolResult result = chain.doIntercept(req);
            listeners.forEach(l -> l.onToolEnd(tool, result.getContent(), result.isError()));
            return result;
        } catch (Throwable e) {
            // 调用本身失败（传输错误、server 不可达等），没拿到任何 ToolResult
            listeners.forEach(l -> l.onToolError(tool, e.getMessage()));
            throw e;
        }
    }
}
