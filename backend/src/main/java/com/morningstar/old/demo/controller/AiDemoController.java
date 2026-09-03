package com.morningstar.old.demo.controller;

import com.morningstar.old.infra.response.R;
import com.morningstar.old.infra.ai.AiChatStream;
import com.morningstar.old.infra.ai.TokenRelayInterceptor;
import com.morningstar.old.infra.ai.ToolTraceInterceptor;
import com.morningstar.old.infra.ai.McpClients;
import com.morningstar.old.infra.constant.RedisConstant;
import com.morningstar.old.system.util.AuthUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.*;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * AI 对话接口：自然语言查询 / 更新数据，基于 ChatModel + MCP client
 */
@Tag(name = "AI示例相关接口定义")
@RestController
@RequestMapping("/demo/ai")
@RequiredArgsConstructor
@Slf4j
public class AiDemoController {

    /**
     * 本接口使用的 MCP server 名字列表（对应 application.yml 中 app.ai.mcp.servers 的 key）
     */
    private static final List<String> MCP_NAMES = Arrays.asList("person", "enterprise");

    private final ChatModel chatModel;
    private final ChatSessionFactory chatSessionFactory;
    private final McpClients mcpClients;

    /**
     * SSE 总超时（含多轮工具调用的整体时长）
     */
    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();

    /**
     * 系统提示词：只定角色和行为规则，不枚举工具（工具清单由 MCP 动态发现，模型自行探索）
     */
    private static final String SYSTEM_PROMPT =
            "你是一个数据助手，辅助用户查询/更新数据" +
                    "规则：" +
                    "1) 调用工具无需关心任何凭证，系统会自动携带，不要向用户索要；" +
                    "2) 思考和回答必须都使用中文；" +
                    "3) 只能操作用户自己的数据；" +
                    "4) 只有用户明确指定企业时，才能查询对应企业的信息，不要一次查多个企业的信息给用户；";

    @Operation(summary = "对话（同步）")
    @PostMapping("/chat/sync")
    public R<AiChatResponseVo> chatSync(@Valid @RequestBody AiChatRequestVo req, HttpServletRequest request) {
        // 认证已由过滤器完成，账号来自 SecurityContext
        String account = AuthUtil.getUserId();

        String sessionId = resolveSessionId(req.getSessionId());
        ChatSession session = openSession(account, sessionId);

        ChatResponse resp;
        try {
            resp = chatModel.prompt(buildMessages(session, req.getMessage()))
                    // 工具在请求期绑定（此时应用已就绪，MCP client 可连上内嵌 server 拉取工具列表）
                    // toolAdd 可重复调用，按名字逐个注入多个 MCP server 的工具（内部按工具名聚合）
                    .options(o -> {
                        bindTools(o);
                        bindToken(o, request);
                    })
                    .call();
        } catch (Exception e) {
            log.error("AI 调用失败, account={}", account, e);
            return R.error("AI 调用失败: " + e.getMessage());
        }

        if (resp.getError() != null) {
            log.error("AI 返回错误, account={}: {}", account, resp.getError().getMessage());
            return R.error("AI 返回错误: " + resp.getError().getMessage());
        }

        String answer = resp.getResultContent();
        if (answer == null || answer.isEmpty()) {
            answer = "（模型没有返回文本内容）";
        }

        // 回写会话（只保留用户消息 + 最终回答，中间的 tool_call 过程不入历史）
        session.addMessage(ChatMessage.ofUser(req.getMessage()));
        session.addMessage(ChatMessage.ofAssistant(answer));

        return R.ok(new AiChatResponseVo(answer, sessionId));
    }

    /**
     * 流式对话（SSE）：实时推送 ReAct 过程 —— 工具调用事件 + 模型 token 流。
     * 事件序列：session → (tool_start → tool_end/tool_error)* → token* → done；
     * POST 请求无法使用原生 EventSource，前端用 @microsoft/fetch-event-source 按 SSE 格式解析。
     */
    @Operation(summary = "对话（流式）")
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequestVo req, HttpServletRequest request) {
        String account = AuthUtil.getUserId();

        String sessionId = resolveSessionId(req.getSessionId());
        ChatSession session = openSession(account, sessionId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AiChatStream stream = new AiChatStream(emitter);

        // 第一个事件：告知前端会话 id（新会话时前端此前不知道）
        stream.session(sessionId);

        StringBuilder answer = new StringBuilder();

        // 先订阅拿到 subscription，再注册回调 —— 这样回调直接用它，不需要额外的"盒子"
        // （Spring 在本方法 return 之后才启动异步处理，回调不会提前触发，这个顺序是安全的）
        Disposable subscription = chatModel.prompt(buildMessages(session, req.getMessage()))
                .options(o -> {
                    bindTools(o);
                    bindToken(o, request);
                    // 按请求注册拦截器：工具事件绑定到当前请求的 SSE 出口
                    o.interceptorAdd(new ToolTraceInterceptor(stream));
                })
                .stream()
                .subscribe(
                        chunk -> {
                            if (chunk.getError() != null) {
                                stream.error(chunk.getError().getMessage());
                                return;
                            }
                            // 一个 chunk 可能含多条消息（如 </think> 哨兵帧 + 正式内容帧挤在一起），逐条按 isThinking 分流
                            for (ChatChoice choice : chunk.getChoices()) {
                                AssistantMessage msg = choice.getMessage();
                                if (msg == null || !msg.hasContent()) {
                                    continue;
                                }
                                String delta = msg.getContent();
                                if (msg.isThinking()) {
                                    // 思考帧：跳过 <think>/</think> 边界哨兵，只推思考增量
                                    if (!"<think>".equals(delta) && !"</think>".equals(delta)) {
                                        stream.think(delta);
                                    }
                                } else {
                                    answer.append(delta);
                                    stream.answer(delta);
                                }
                            }
                        },
                        ex -> {
                            log.error("AI 流式调用失败, account={}, session={}", account, sessionId, ex);
                            stream.error(ex.getMessage());
                            emitter.complete();
                        },
                        () -> {
                            String finalAnswer = answer.length() > 0 ? answer.toString() : "（模型没有返回文本内容）";

                            // 回写会话（只保留用户消息 + 最终回答，工具过程不入历史）
                            session.addMessage(ChatMessage.ofUser(req.getMessage()));
                            session.addMessage(ChatMessage.ofAssistant(finalAnswer));

                            stream.done(finalAnswer);
                            emitter.complete();
                        });

        // 客户端断开或超时：停掉模型流，别让它对着空气说完
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });

        return emitter;
    }

    @Data
    @Schema(description = "AI 对话请求")
    public static class AiChatRequestVo {
        @Schema(description = "用户自然语言消息")
        @NotBlank(message = "message 不能为空")
        private String message;

        @Schema(description = "会话 id：首轮不传表示开新会话（响应中返回），后续轮次带上以延续上下文")
        private String sessionId;
    }

    @Data
    @AllArgsConstructor
    @Schema(description = "AI 对话响应")
    public static class AiChatResponseVo {
        @Schema(description = "AI 回答")
        private String answer;

        @Schema(description = "会话 id")
        private String sessionId;
    }

    /**
     * 无 sessionId 开新会话；历史已过期的 sessionId 等价于带旧 id 的空会话
     */
    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.trim().isEmpty())
                ? UUID.randomUUID().toString()
                : sessionId;
    }

    /**
     * 按"账号+会话id"定位会话（天然做到数据权限隔离）
     */
    private ChatSession openSession(String account, String sessionId) {
        return chatSessionFactory.getSession(account + RedisConstant.KEY_SEPARATOR + sessionId);
    }

    /**
     * 组装消息：系统提示词 + 历史上下文 + 本轮用户消息
     */
    private List<ChatMessage> buildMessages(ChatSession session, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.ofSystem(SYSTEM_PROMPT));
        messages.addAll(session.getMessages());
        messages.add(ChatMessage.ofUser(userMessage));
        return messages;
    }

    /**
     * 绑定 MCP 工具（工具在请求期绑定：启动期 MCP client 还没就绪）
     */
    private void bindTools(ChatOptions options) {
        MCP_NAMES.forEach(name -> options.toolAdd(mcpClients.get(name)));
    }

    /**
     * 绑定调用方凭证：透传当前请求的 Authorization 头
     */
    private void bindToken(ChatOptions options, HttpServletRequest request) {
        options.interceptorAdd(new TokenRelayInterceptor(request.getHeader(HttpHeaders.AUTHORIZATION)));
    }
}
