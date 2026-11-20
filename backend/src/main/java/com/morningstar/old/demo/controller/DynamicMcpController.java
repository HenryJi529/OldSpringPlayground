package com.morningstar.old.demo.controller;

import com.morningstar.old.demo.mcp.DemoEndpoint;
import com.morningstar.old.infra.exception.BaseException;
import com.morningstar.old.infra.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.ai.chat.prompt.FunctionPrompt;
import org.noear.solon.ai.chat.prompt.FunctionPromptDesc;
import org.noear.solon.ai.chat.resource.FunctionResource;
import org.noear.solon.ai.chat.resource.FunctionResourceDesc;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import org.noear.solon.ai.mcp.server.McpServerEndpointProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 动态 MCP 管理：运行期向已有 MCP 端点（demo）增删 Tool、Prompt 和 Resource，改动立即生效，
 * 无需重启或重新发布端点（与 {@link DemoEndpoint} 里注解的静态声明方式相对）。
 *
 * <p>端点 Provider 在启动时以端点名为 bean 名放进了 Solon 容器（复刻官方 McpPlugin
 * 的 {@code wrapAndPut} 做法），这里直接用 {@code Solon.context().getBean(端点名)} 获取，
 * 不需要额外的注册表。</p>
 */
@Tag(name = "动态MCP管理")
@RestController
@RequestMapping("/demo/dynamic-mcp")
@Slf4j
public class DynamicMcpController {

    /**
     * 复用的目标端点名（对应 DemoEndpoint 上 @McpServerEndpoint 的 name）
     */
    private static final String ENDPOINT_NAME = "MCP-demo";

    private static final String REPEAT_TOOL_NAME = "repeat";
    private static final String SUMMARY_PROMPT_NAME = "summarize";
    private static final String SERVER_INFO_RESOURCE_URI = "demo://dynamic/server-info";

    @Operation(summary = "动态注册 repeat 工具")
    @PostMapping("/tools/repeat")
    public R<String> addRepeatTool() {
        McpServerEndpointProvider endpoint = requireEndpoint();
        if (endpoint.hasTool(REPEAT_TOOL_NAME)) {
            return R.ok("工具[" + REPEAT_TOOL_NAME + "]已存在，无需重复注册");
        }
        endpoint.addTool(buildRepeatTool());
        log.info("动态注册MCP工具[{}]到端点[{}]", REPEAT_TOOL_NAME, ENDPOINT_NAME);
        return R.ok("注册成功");
    }

    @Operation(summary = "动态移除 repeat 工具")
    @DeleteMapping("/tools/repeat")
    public R<String> removeRepeat() {
        McpServerEndpointProvider endpoint = requireEndpoint();
        if (!endpoint.hasTool(REPEAT_TOOL_NAME)) {
            return R.ok("工具[" + REPEAT_TOOL_NAME + "]不存在，无需移除");
        }
        endpoint.removeTool(REPEAT_TOOL_NAME);
        log.info("动态移除MCP工具[{}]，端点[{}]", REPEAT_TOOL_NAME, ENDPOINT_NAME);
        return R.ok("移除成功");
    }

    @Operation(summary = "查看端点当前的全部工具名")
    @GetMapping("/tools")
    public R<List<String>> listTools() {
        return R.ok(requireEndpoint().getTools().stream().map(FunctionTool::name).collect(Collectors.toList()));
    }

    @Operation(summary = "动态注册 summarize 提示词（生成内容摘要任务）")
    @PostMapping("/prompts/summarize")
    public R<String> addSummaryPrompt() {
        McpServerEndpointProvider endpoint = requireEndpoint();
        if (endpoint.hasPrompt(SUMMARY_PROMPT_NAME)) {
            return R.ok("提示词[" + SUMMARY_PROMPT_NAME + "]已存在，无需重复注册");
        }
        endpoint.addPrompt(buildSummaryPrompt());
        log.info("动态注册MCP提示词[{}]到端点[{}]", SUMMARY_PROMPT_NAME, ENDPOINT_NAME);
        return R.ok("注册成功");
    }

    @Operation(summary = "动态移除 summarize 提示词")
    @DeleteMapping("/prompts/summarize")
    public R<String> removeSummaryPrompt() {
        McpServerEndpointProvider endpoint = requireEndpoint();
        if (!endpoint.hasPrompt(SUMMARY_PROMPT_NAME)) {
            return R.ok("提示词[" + SUMMARY_PROMPT_NAME + "]不存在，无需移除");
        }
        endpoint.removePrompt(SUMMARY_PROMPT_NAME);
        log.info("动态移除MCP提示词[{}]，端点[{}]", SUMMARY_PROMPT_NAME, ENDPOINT_NAME);
        return R.ok("移除成功");
    }

    @Operation(summary = "查看端点当前的全部提示词名")
    @GetMapping("/prompts")
    public R<List<String>> listPrompts() {
        return R.ok(requireEndpoint().getPrompts().stream().map(FunctionPrompt::name).collect(Collectors.toList()));
    }

    /**
     * 从 Solon 容器按端点名取 Provider；不存在时抛业务异常（通常意味着端点未启动或名字写错）
     */
    private static McpServerEndpointProvider requireEndpoint() {
        McpServerEndpointProvider endpoint = Solon.context().getBean(ENDPOINT_NAME);
        if (endpoint == null) {
            throw new BaseException("MCP端点[" + ENDPOINT_NAME + "]不存在或未启动");
        }
        return endpoint;
    }

    /**
     * 用编程方式（而非 @ToolMapping 注解）构建 repeat 工具：把入参 content 重复 3 次返回
     */
    private static FunctionTool buildRepeatTool() {
        FunctionToolDesc tool = new FunctionToolDesc(REPEAT_TOOL_NAME)
                .title("复读")
                .description("把入参内容重复3次后返回")
                .stringParamAdd("content", "要重复的内容")
                .doHandle(args -> {
                    String content = (String) args.get("content");
                    if (content == null || content.isEmpty()) {
                        throw new BaseException("入参[content]不能为空");
                    }
                    return content + " " + content + " " + content;
                });
        tool.metaPut("need_permission", false);
        return tool;
    }

    /**
     * 用编程方式构建摘要提示词；返回 String 时 Solon 会转换为一条 user 角色的 PromptMessage。
     */
    static FunctionPrompt buildSummaryPrompt() {
        return new FunctionPromptDesc(SUMMARY_PROMPT_NAME)
                .title("内容摘要")
                .description("根据指定内容生成中文摘要任务提示词")
                .paramAdd("content", "要总结的内容")
                .doHandle(args -> {
                    String content = (String) args.get("content");
                    if (content == null || content.trim().isEmpty()) {
                        throw new BaseException("入参[content]不能为空");
                    }
                    return "请用中文总结以下内容，先给出不超过100字的摘要，再列出关键要点：\n" + content;
                });
    }
}
