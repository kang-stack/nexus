package cn.cloudcat.user.message.chat.memory;

import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.user.session.service.UserMessageService;
import cn.cloudcat.user.session.service.UserMessageServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import cn.cloudcat.user.message.chat.memory.EvictableChatMemoryStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * V2 会话记忆：基于 acc_user_message + Redis 缓存，供 LangChain4j RAG 使用。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserChatMemoryStore implements EvictableChatMemoryStore {

    private static final String REDIS_KEY_PREFIX = "icc:cs-chat-memory:";
    private static final int MAX_MESSAGES = 10;
    private static final long CACHE_TTL_HOURS = 1;

    private final UserMessageService messageService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(json));
            }
        } catch (Exception e) {
            log.warn("Redis 读取 cs 聊天记忆失败, memoryId={}", memoryId, e);
        }

        List<dev.langchain4j.data.message.ChatMessage> messages = loadFromDatabase(memoryId.toString());
        saveToRedis(key, messages);
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        saveToRedis(buildKey(memoryId), trimMessages(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            stringRedisTemplate.delete(buildKey(memoryId));
        } catch (Exception e) {
            log.warn("Redis 删除 cs 聊天记忆失败, memoryId={}", memoryId, e);
        }
    }

    public void evictCache(Object memoryId) {
        try {
            stringRedisTemplate.delete(buildKey(memoryId));
        } catch (Exception e) {
            log.warn("Redis 清除 cs 聊天记忆缓存失败, memoryId={}", memoryId, e);
        }
    }

    private List<dev.langchain4j.data.message.ChatMessage> loadFromDatabase(String conversationId) {
        List<UserMessage> dbMessages = messageService.getRecentMessages(conversationId, MAX_MESSAGES);
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (int i = dbMessages.size() - 1; i >= 0; i--) {
            UserMessage msg = dbMessages.get(i);
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            if (UserMessageServiceImpl.ROLE_USER.equals(msg.getRole())) {
                messages.add(dev.langchain4j.data.message.UserMessage.from(msg.getContent()));
            } else if (UserMessageServiceImpl.ROLE_ASSISTANT.equals(msg.getRole())) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }
        return messages;
    }

    private void saveToRedis(String key, List<dev.langchain4j.data.message.ChatMessage> messages) {
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 保存 cs 聊天记忆失败, key={}", key, e);
        }
    }

    private List<dev.langchain4j.data.message.ChatMessage> trimMessages(
            List<dev.langchain4j.data.message.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        if (messages.size() <= MAX_MESSAGES) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - MAX_MESSAGES, messages.size()));
    }

    private String buildKey(Object memoryId) {
        return REDIS_KEY_PREFIX + memoryId;
    }
}
