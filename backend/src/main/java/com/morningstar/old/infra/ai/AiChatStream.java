package com.morningstar.old.infra.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morningstar.old.infra.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 对话的 SSE 事件流：封装 SseEmitter，对外只暴露强类型的发送方法。
 * 事件名与 payload 结构统一定义在本类中（前端契约的唯一真相）；
 * 发送加锁，允许模型流线程与工具执行线程并发写。
 */
@Slf4j
public class AiChatStream implements ToolEventListener {

    private static final ObjectMapper MAPPER = JsonUtil.objectMapper();

    private final SseEmitter emitter;
    private final Object sendLock = new Object();

    public AiChatStream(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * SSE 事件名
     */
    @Getter
    @AllArgsConstructor
    public enum Event {
        SESSION("session"),
        THINK("think"),
        ANSWER("answer"),
        TOOL_START("tool_start"),
        TOOL_END("tool_end"),
        TOOL_ERROR("tool_error"),
        DONE("done"),
        ERROR("error");

        private final String wireName;
    }

    @Data
    @AllArgsConstructor
    public static class SessionPayload {
        private String sessionId;
    }

    @Data
    @AllArgsConstructor
    public static class DeltaPayload {
        private String delta;
    }

    @Data
    @AllArgsConstructor
    public static class ToolStartPayload {
        private String tool;
        private Map<String, Object> args;
    }

    @Data
    @AllArgsConstructor
    public static class ToolEndPayload {
        private String tool;
        private String result;
        private boolean isError;
    }

    @Data
    @AllArgsConstructor
    public static class ToolErrorPayload {
        private String tool;
        private String error;
    }

    @Data
    @AllArgsConstructor
    public static class DonePayload {
        private String answer;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorPayload {
        private String message;
    }

    /**
     * 会话事件：第一个发送，告知前端会话 id
     */
    public void session(String sessionId) {
        send(Event.SESSION, new SessionPayload(sessionId));
    }

    /**
     * 模型思考增量（reasoning 内容，页面上与正式回答分开展示）
     */
    public void think(String delta) {
        send(Event.THINK, new DeltaPayload(delta));
    }

    /**
     * 模型输出增量
     */
    public void answer(String delta) {
        send(Event.ANSWER, new DeltaPayload(delta));
    }

    /**
     * 正常收尾：完整回答（sessionId 已在开头的 session 事件中下发）
     */
    public void done(String answer) {
        send(Event.DONE, new DonePayload(answer));
    }

    /**
     * 异常收尾
     */
    public void error(String message) {
        send(Event.ERROR, new ErrorPayload(message));
    }

    @Override
    public void onToolStart(String tool, Map<String, Object> args) {
        send(Event.TOOL_START, new ToolStartPayload(tool, args));
    }

    @Override
    public void onToolEnd(String tool, String result, boolean isError) {
        send(Event.TOOL_END, new ToolEndPayload(tool, result, isError));
    }

    @Override
    public void onToolError(String tool, String error) {
        send(Event.TOOL_ERROR, new ToolErrorPayload(tool, error));
    }

    private void send(Event event, Object payload) {
        synchronized (sendLock) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getWireName())
                        .data(MAPPER.writeValueAsString(payload), MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // 客户端断开等：记录即可，由 flux 侧收尾
                log.debug("SSE 发送失败 event={}: {}", event, e.getMessage());
            }
        }
    }
}
