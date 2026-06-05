package cn.cloudcat.user.message.review;

import cn.cloudcat.user.session.service.UserMessageLifecycleListener;
import cn.cloudcat.user.common.constant.EmotionTag;
import cn.cloudcat.user.common.constant.RiskLevel;
import cn.cloudcat.user.session.entity.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 助手消息落库后，按会话最近情绪标记写入审核队列。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class UserMessageReviewLifecycleListener implements UserMessageLifecycleListener {

    private static final String EMOTION_KEY_PREFIX = "icc:emotion:last:";

    private final UserMessageReviewService reviewService;
    private final StringRedisTemplate redisTemplate;

    /** 消息落库后：读取缓存的会话情绪标签，按需写入审核队列 */
    @Override
    public void onMessageSaved(UserMessage message) {
        String emotionTag = readCachedEmotion(message.getConversationId());
        String risk = emotionRisk(emotionTag);
        reviewService.enqueueIfNeeded(message, emotionTag, risk);
    }

    /** 从 Redis 读取会话的最近情绪标签，默认 NEUTRAL */
    private String readCachedEmotion(String conversationId) {
        if (conversationId == null) {
            return "NEUTRAL";
        }
        try {
            String tag = redisTemplate.opsForValue().get(EMOTION_KEY_PREFIX + conversationId);
            return tag != null ? tag : "NEUTRAL";
        } catch (Exception e) {
            return "NEUTRAL";
        }
    }

    /** 将情绪标签映射为风险等级 */
    private static String emotionRisk(String rawTag) {
        EmotionTag tag = EmotionTag.fromTag(rawTag);
        if (tag == EmotionTag.ABUSE) {
            return RiskLevel.HIGH.name();
        }
        if (tag == EmotionTag.ANGRY) {
            return RiskLevel.MEDIUM.name();
        }
        return RiskLevel.LOW.name();
    }
}
