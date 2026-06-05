package cn.cloudcat.user.message.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * V2 会话双计时器（空闲 5min / 满意度 2min）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class UserSessionTimeoutService {

    private static final String TIMER_KEY = "session:timer:";
    private static final String SAT_TIMER_KEY = "session:sat:timer:";
    private static final long IDLE_SECONDS = 300;
    private static final long SAT_SECONDS = 120;

    private final StringRedisTemplate redisTemplate;
    private final SessionTemplateServiceBridge templateBridge;

    public UserSessionTimeoutService(StringRedisTemplate redisTemplate,
                                   SessionTemplateServiceBridge templateBridge) {
        this.redisTemplate = redisTemplate;
        this.templateBridge = templateBridge;
    }

    public void refreshIdleTimer(String conversationId) {
        setTtl(TIMER_KEY + conversationId, IDLE_SECONDS);
        redisTemplate.delete(SAT_TIMER_KEY + conversationId);
    }

    public void startSatisfactionTimer(String conversationId) {
        setTtl(SAT_TIMER_KEY + conversationId, SAT_SECONDS);
    }

    public void cancelTimers(String conversationId) {
        redisTemplate.delete(TIMER_KEY + conversationId);
        redisTemplate.delete(SAT_TIMER_KEY + conversationId);
    }

    public long getIdleRemainingSeconds(String conversationId) {
        return getTtl(TIMER_KEY + conversationId);
    }

    public boolean isIdleExpired(String conversationId) {
        return getIdleRemainingSeconds(conversationId) <= 0;
    }

    public boolean isSatisfactionExpired(String conversationId) {
        Long ttl = redisTemplate.getExpire(SAT_TIMER_KEY + conversationId, TimeUnit.SECONDS);
        return ttl != null && ttl == -2;
    }

    public String timeoutMessage() {
        return templateBridge.timeoutMessage();
    }

    private void setTtl(String key, long seconds) {
        try {
            redisTemplate.opsForValue().set(key, "1", seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("UserSessionTimeout set failed key={}", key, e);
        }
    }

    private long getTtl(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 避免 cs-session 模块依赖 icc-knowledge
 *
 * @author Kang
 * @since 2026-06-02
 */
    public interface SessionTemplateServiceBridge {
        String timeoutMessage();
    }
}
