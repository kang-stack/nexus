package cn.cloudcat.user.message.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 同会话重复提问检测（PRD 边界条件）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class DuplicateQuestionService {

    private static final String KEY_PREFIX = "icc:dup:question:";
    private static final int MAX_HISTORY = 5;
    private static final double SIMILARITY_THRESHOLD = 0.8;

    private final StringRedisTemplate redisTemplate;

    public DuplicateQuestionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 同会话重复提问检测。
     * <p>
     * 策略：
     * <ol>
     *   <li>对提问做归一化（去空白）后与 Redis 中最近 5 条历史逐一比对</li>
     *   <li>精确匹配或 Jaccard 相似度 >= 0.8 视为重复</li>
     *   <li>非重复时写入 Redis List（左推 + trim 保持最新 5 条），1 小时过期</li>
     * </ol>
     *
     * @return true 表示重复，应走快捷回复而非重新调用 LLM
     */
    public boolean isDuplicate(String conversationId, String question) {
        if (conversationId == null || question == null || question.isBlank()) {
            return false;
        }
        String trimmed = normalize(question);
        try {
            String key = KEY_PREFIX + conversationId;
            // 读取同会话最近 5 条历史提问
            List<String> recent = redisTemplate.opsForList().range(key, 0, -1);
            if (recent != null) {
                for (String prev : recent) {
                    String prevTrimmed = normalize(prev);
                    // 精确匹配 或 Jaccard 相似度 >= 阈值
                    if (prevTrimmed.equals(trimmed) || similarity(prevTrimmed, trimmed) >= SIMILARITY_THRESHOLD) {
                        log.info("Duplicate question detected conv={}", conversationId);
                        return true;
                    }
                }
            }
            // 非重复：写入历史（左推最新，保留最多 5 条，1h TTL）
            redisTemplate.opsForList().leftPush(key, trimmed);
            redisTemplate.opsForList().trim(key, 0, MAX_HISTORY - 1);
            redisTemplate.expire(key, Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Duplicate check failed conv={}", conversationId, e);
        }
        return false;
    }

    // 去除所有空白字符用于精确比对
    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", "");
    }

    // Jaccard 相似度：|A ∩ B| / |A ∪ B|，基于字符集合
    static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<Character> setA = new HashSet<>();
        Set<Character> setB = new HashSet<>();
        for (char c : a.toCharArray()) {
            setA.add(c);
        }
        for (char c : b.toCharArray()) {
            setB.add(c);
        }
        Set<Character> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<Character> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }
}
