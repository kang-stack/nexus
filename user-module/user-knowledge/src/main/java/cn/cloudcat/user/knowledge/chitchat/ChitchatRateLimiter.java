package cn.cloudcat.user.knowledge.chitchat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 寒暄同分类 60s 限频（Redis key: chitchat:rate:{convId}:{category}）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@Slf4j
public class ChitchatRateLimiter {

    private static final String KEY_PREFIX = "chitchat:rate:";
    private static final long TTL_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    public ChitchatRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** @return true 允许回复；false 限频拦截 */
    public boolean allow(String conversationId, String categoryCode) {
        if (conversationId == null || categoryCode == null) {
            return true;
        }
        String key = KEY_PREFIX + conversationId + ":" + categoryCode;
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("chitchat rate limit check failed key={}", key, e);
            return true;
        }
    }
}
