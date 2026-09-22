package com.morningstar.old.infra.ai;

import com.morningstar.old.infra.constant.RedisConstant;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.ChatSession;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 会话历史存储（纯 Redis 读写，无进程内缓存）。
 *
 * <p>结构：每会话一个 List，key 形如 {@code ai:chat:session:<account>:<sessionId>}，
 * 每条消息以 {@link ChatMessage#toJson} 的 JSON RPUSH 入队；读取时 LRANGE 加载。
 * System 消息不落 Redis（每次请求由业务现场组装）。</p>
 */
public class RedisChatSession implements ChatSession {

    /**
     * Redis 中保留的最大消息条数
     */
    public static final int MAX_MESSAGES = 20;

    /**
     * 会话过期时间（每次写入续期）
     */
    public static final Duration TTL = Duration.ofDays(7);

    private final String sessionKey;
    private final StringRedisTemplate redisTemplate;

    public RedisChatSession(String sessionKey, StringRedisTemplate redisTemplate) {
        this.sessionKey = sessionKey;
        this.redisTemplate = redisTemplate;
    }

    private String key() {
        return RedisConstant.AI_CHAT_SESSION + RedisConstant.KEY_SEPARATOR + sessionKey;
    }

    @Override
    public String getSessionId() {
        return sessionKey;
    }

    @Override
    public List<ChatMessage> getMessages() {
        return loadMessages(0, -1);
    }

    @Override
    public List<ChatMessage> getLatestMessages(int windowSize) {
        if (windowSize <= 0) {
            return new ArrayList<>();
        }
        // 从右往左数最近 windowSize 条
        return loadMessages(-windowSize, -1);
    }

    /**
     * 从 Redis 加载指定索引范围的聊天消息（LRANGE）
     */
    private List<ChatMessage> loadMessages(long start, long end) {
        List<String> rawList = redisTemplate.opsForList().range(key(), start, end);
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }
        return rawList.stream().map(ChatMessage::fromJson).collect(Collectors.toList());
    }

    @Override
    public void removeLatestMessage(int windowSize) {
        if (windowSize <= 0) {
            return;
        }
        // 从最右端（最新）逐个弹出；弹出最后一个元素时 Redis 会自动删除整个 key
        for (int i = 0; i < windowSize; i++) {
            redisTemplate.opsForList().rightPop(key());
        }
        // 若仍有剩余，续期
        Long size = redisTemplate.opsForList().size(key());
        if (size != null && size > 0) {
            redisTemplate.expire(key(), TTL);
        }
    }

    @Override
    public void addMessage(Collection<? extends ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // System 消息不落盘（每次请求由业务现场组装）
        List<String> jsonList = messages.stream()
                .filter(m -> m.getRole() != ChatRole.SYSTEM)
                .map(ChatMessage::toJson)
                .collect(Collectors.toList());
        if (jsonList.isEmpty()) {
            return;
        }

        // 追加到列表尾部
        redisTemplate.opsForList().rightPushAll(key(), jsonList);
        // 限长：只留最近 MAX_MESSAGES 条
        redisTemplate.opsForList().trim(key(), -MAX_MESSAGES, -1);
        // 续期
        redisTemplate.expire(key(), TTL);
    }

    @Override
    public boolean isEmpty() {
        Long size = redisTemplate.opsForList().size(key());
        return size == null || size == 0;
    }

    @Override
    public void clear() {
        redisTemplate.delete(key());
    }

    @Override
    public Map<String, Object> attrs() {
        // 临时属性，不持久化（当前业务未使用）
        return new HashMap<>();
    }
}


