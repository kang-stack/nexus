package cn.cloudcat.user.message.human;

import cn.cloudcat.user.common.constant.EmotionTag;
import cn.cloudcat.user.knowledge.service.KbHumanRouteService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
/**
 * 人工客服路由服务（PRD 5.4.4 + 设计文档 Phase 4 增强）。
 *
 * 功能：
 * - 工作时间判断（8:30-23:30，全年无休）
 * - 连续转人工检测（同会话 ≥3 次转人工 → 自动标记为高优先级）
 * - 非工作时间 → 提示留言
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class HumanRoutingService {

    private static final LocalTime WORK_START = LocalTime.of(8, 30);
    private static final LocalTime WORK_END = LocalTime.of(23, 30);
    private static final int CONSECUTIVE_THRESHOLD = 3;

    private static final String TRANSFER_COUNT_KEY_PREFIX = "icc:human:transfer_count:";
    private static final String EMOTION_KEY_PREFIX = "icc:emotion:last:";

    private final StringRedisTemplate redisTemplate;
    private final KbHumanRouteService humanRouteService;

    public HumanRoutingService(StringRedisTemplate redisTemplate, KbHumanRouteService humanRouteService) {
        this.redisTemplate = redisTemplate;
        this.humanRouteService = humanRouteService;
    }

    /**
     * 判断当前是否在工作时间内。
     */
    public boolean isWithinWorkingHours() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(WORK_START) && !now.isAfter(WORK_END);
    }

    /**
     * 记录一次转人工请求，返回当前会话的转人工次数。
     */
    public int recordTransferAttempt(String conversationId) {
        String key = TRANSFER_COUNT_KEY_PREFIX + conversationId;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(2));
        int cnt = count != null ? count.intValue() : 1;
        if (cnt >= CONSECUTIVE_THRESHOLD) {
            log.info("HumanRouting: consecutive transfer threshold reached, conversationId={} count={}",
                    conversationId, cnt);
        }
        return cnt;
    }

    /**
     * 检查是否应标记为高优先级（连续 ≥3 次转人工）。
     */
    public boolean isHighPriority(String conversationId) {
        String key = TRANSFER_COUNT_KEY_PREFIX + conversationId;
        String val = redisTemplate.opsForValue().get(key);
        int count = val != null ? Integer.parseInt(val) : 0;
        return count >= CONSECUTIVE_THRESHOLD || isEmotionHighPriority(conversationId);
    }

    /** 辱骂/愤怒情绪标记的会话优先排队 */
    public boolean isEmotionHighPriority(String conversationId) {
        if (conversationId == null) {
            return false;
        }
        try {
            String raw = redisTemplate.opsForValue().get(EMOTION_KEY_PREFIX + conversationId);
            EmotionTag tag = EmotionTag.fromTag(raw);
            return tag == EmotionTag.ABUSE || tag == EmotionTag.ANGRY;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取非工作时间提示。
     */
    public String getOffHoursMessage() {
        return "抱歉，当前人工客服已下班（服务时间：8:30-23:30）。"
                + "您的问题我会尽力帮您解决，如仍需人工服务，请在工作时间内再来哦～";
    }

    /**
     * 路由决策结果。
     */
    public record RoutingResult(boolean canTransfer, String message, boolean highPriority, String channel) {
        public static RoutingResult ok(boolean highPriority, String channel) {
            return new RoutingResult(true, null, highPriority, channel);
        }

        public static RoutingResult reject(String reason) {
            return new RoutingResult(false, reason, false, null);
        }
    }

    /**
     * 根据意图解析对应的人工渠道 URL。
     * 优先级：投诉 > 套餐升级 > 默认。
     */
    public String resolveChannel(String intent) {
        return humanRouteService.resolveChannel(intent);
    }

    /**
     * 综合路由决策（无意图信息，退化为仅工作时间 + 优先级检查）。
     */
    public RoutingResult route(String conversationId) {
        return route(conversationId, null);
    }

    /**
     * 综合路由决策（含意图信息，可路由到不同人工渠道）。
     */
    public RoutingResult route(String conversationId, String intent) {
        if (!isWithinWorkingHours()) {
            return RoutingResult.reject(getOffHoursMessage());
        }
        boolean highPriority = isHighPriority(conversationId);
        String channel = resolveChannel(intent);
        return RoutingResult.ok(highPriority, channel);
    }
}
