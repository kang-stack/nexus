package cn.cloudcat.user.common.constant;

/**
 * 情绪标签枚举 — 统一替换代码中的 "ABUSE" / "ANGRY" / "SAD" / "URGENT" / "THREATEN" / "NEUTRAL" 裸字符串。
 * <p>
 * 检测优先级（先命中先胜出）：
 * <ol>
 *   <li>{@link #ABUSE} — 辱骂（HIGH/建议转人工）</li>
 *   <li>{@link #THREATEN} — 威胁（HIGH/建议转人工）</li>
 *   <li>{@link #ANGRY} — 愤怒（MEDIUM/建议转人工）</li>
 *   <li>{@link #URGENT} — 急迫（MEDIUM/建议转人工）</li>
 *   <li>{@link #SAD} — 沮丧（LOW/安抚不转人工）</li>
 *   <li>{@link #NEUTRAL} — 中性（LOW/无安抚）</li>
 * </ol>
 * <p>
 * <b>数据库对应：</b>acc_user_kb_emotion_pattern.emotion_tag 列存储此枚举的 name()（如 "ABUSE"），
 * 代码中使用 {@link #valueOf(String)} 反序列化。
 *
 * @author Kang
 * @since 2026-06-03
 */
public enum EmotionTag {

    /** 辱骂 — 最高优先级，触发安抚 + 转人工建议 */
    ABUSE,
    /** 威胁 — 高优先级，触发转人工建议 */
    THREATEN,
    /** 愤怒 — 中等优先级 */
    ANGRY,
    /** 急迫 — 中等优先级 */
    URGENT,
    /** 沮丧 — 低优先级，安抚但不转人工 */
    SAD,
    /** 中性 — 无情绪 */
    NEUTRAL;

    /** 安全解析，未知标签返回 null（调用方自行处理） */
    public static EmotionTag fromTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        try {
            return valueOf(tag.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
