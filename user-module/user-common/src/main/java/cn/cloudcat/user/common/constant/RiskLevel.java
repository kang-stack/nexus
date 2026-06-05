package cn.cloudcat.user.common.constant;

/**
 * 风险等级枚举 — 统一替换代码中的 "HIGH" / "MEDIUM" / "LOW" 裸字符串。
 * <p>
 * 用于情绪检测结果、消息审核优先级等场景。
 * <p>
 * <b>数据库对应：</b>acc_user_kb_emotion_pattern.risk_level 列存储此枚举的 name()，
 * 代码中使用 {@link #valueOf(String)} 反序列化。
 *
 * @author Kang
 * @since 2026-06-03
 */
public enum RiskLevel {

    /** 高风险：辱骂/威胁 — 建议转人工 */
    HIGH,
    /** 中风险：愤怒/急迫 — 可安抚或转人工 */
    MEDIUM,
    /** 低风险：沮丧/中性 — 安抚即可 */
    LOW;

    /** 安全解析，未知等级返回 MEDIUM 作为保守默认值 */
    public static RiskLevel fromLevel(String level) {
        if (level == null || level.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
