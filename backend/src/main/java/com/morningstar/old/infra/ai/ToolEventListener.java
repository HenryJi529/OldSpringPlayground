package com.morningstar.old.infra.ai;

import java.util.Map;

/**
 * 工具调用事件监听：on_tool_start / on_tool_end / on_tool_error 的强类型回调。
 * 在工具调用前后触发，实现方决定事件去向（SSE 推送 / 日志 / 审计...）。
 */
public interface ToolEventListener {

    /**
     * 工具开始执行
     */
    void onToolStart(String tool, Map<String, Object> args);

    /**
     * 工具执行完成
     *
     * @param isError 工具结果是错误内容（如 MCP 侧业务异常被转为 error result）
     */
    void onToolEnd(String tool, String result, boolean isError);

    /**
     * 工具执行抛异常
     */
    void onToolError(String tool, String error);
}
