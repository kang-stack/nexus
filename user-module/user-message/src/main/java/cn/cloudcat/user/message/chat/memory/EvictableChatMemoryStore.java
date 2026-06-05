package cn.cloudcat.user.message.chat.memory;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 支持显式清除 Redis 缓存的 ChatMemoryStore 扩展。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface EvictableChatMemoryStore extends ChatMemoryStore {

    void evictCache(Object memoryId);
}
