package com.morningstar.old.infra.config;

import com.morningstar.old.infra.ai.McpClients;
import com.morningstar.old.infra.ai.RedisChatSession;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;

/**
 * AI 基础设施 Bean 装配（与具体业务无关，各业务模块注入使用）：
 * <ul>
 *     <li>{@link McpClients}：MCP 客户端注册表，按名字管理多个 MCP server 连接；</li>
 *     <li>{@link ChatModel}：模型客户端（OpenAI 兼容），业务侧按需绑定 MCP 工具做自动调用；</li>
 *     <li>{@link ChatSessionFactory}：Redis 会话工厂（按"账号+会话id"隔离，纯 Redis 读写，无进程内缓存）。</li>
 * </ul>
 */
@Configuration
public class AiChatConfig {

    /**
     * 聊天模型（不在构建期绑工具：MCP 工具在首个请求时才拉取，启动期连接自己还没就绪会 Connection refused）
     */
    @Bean
    public ChatModel chatModel(@Value("${app.ai.chat.base-url}") String baseUrl,
                               @Value("${app.ai.chat.api-key:}") String apiKey,
                               @Value("${app.ai.chat.model}") String model,
                               @Value("${app.ai.chat.timeout-seconds}") Integer timeoutSeconds) {
        HashMap<String, Object> chatTemplateKwargs = new HashMap<>();
        chatTemplateKwargs.put("enable_thinking", true);
        return ChatModel.of(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .modelOptions(o -> o
                        .thinking(true)
                        .optionSet("chat_template_kwargs", chatTemplateKwargs)
                )
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /**
     * 会话工厂：入参为会话标识（业务侧约定为 {@code <账号>:<会话UUID>}，天然做到数据权限隔离。
     * 每次请求都新建轻量会话对象、直连 Redis 读写，进程内不缓存任何会话状态，
     * 单实例/多实例/重启上下文一致（Redis 是唯一真相）。
     */
    @Bean
    public ChatSessionFactory chatSessionFactory(StringRedisTemplate stringRedisTemplate) {
        return sessionKey -> new RedisChatSession(sessionKey, stringRedisTemplate);
    }
}
