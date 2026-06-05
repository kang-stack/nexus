package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.common.constant.EmotionTag;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.message.emotion.EmotionDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情绪检测 Skill — Phase 1 预处理（order=11）。
 * <p>
 * 基于 acc_user_kb_emotion_pattern 词库检测用户输入中的辱骂/愤怒/沮丧/急迫/威胁情绪。
 * 命中 ABUSE/HIGH 时安抚并标记风险，供后续 UserMessageReview 运营审核使用。
 * 情绪检测不 markHandled，链继续执行正常业务逻辑。
 * <p>
 * Skill 链阶段：[Phase1 预处理 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class EmotionDetectionChatSkill implements ChatSkill {

    private static final String EMOTION_KEY_PREFIX = "icc:emotion:last:";

    private final EmotionDetectionService emotionDetectionService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public int order() {
        return 11;
    }

    @Override
    public String name() {
        return "EmotionDetection";
    }

    /** 未处理且有用户输入时触发 */
    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled() && ctx.userText() != null && !ctx.userText().isBlank();
    }

    /** 检测情绪标签和风险等级，对辱骂类自动安抚并建议转人工 */
    @Override
    public void execute(SkillContext ctx) {
        EmotionDetectionService.EmotionResult result = emotionDetectionService.detect(ctx.userText());
        ctx.setEmotionTag(result.tag().name());
        ctx.setEmotionRisk(result.riskLevel().name());
        ctx.setSuggestHumanTransfer(result.suggestHuman());

        // 缓存情绪标签到 Redis（存储枚举 name()），供其他技能查询
        cacheEmotion(ctx.conversationId(), result.tag().name());

        // 辱骂类：自动回复安抚 + "转人工客服"按钮
        if (result.tag() == EmotionTag.ABUSE && result.sootheHint() != null) {
            ReplySegment soothe = new ReplySegment("TEXT", result.sootheHint(), 1, 2);
            Map<String, Object> button = new LinkedHashMap<>();
            button.put("text", "转人工客服");
            button.put("type", "action");
            button.put("action", "TRANSFER_TO_HUMAN");
            soothe.setButton(button);
            ctx.addSegments(List.of(soothe));
        }
    }

    /** 将情绪标签缓存到 Redis，30 分钟 TTL */
    private void cacheEmotion(String conversationId, String tag) {
        if (conversationId == null || tag == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(EMOTION_KEY_PREFIX + conversationId, tag, Duration.ofMinutes(30));
        } catch (Exception ignored) {
            // Redis 不可用时跳过
        }
    }
}
