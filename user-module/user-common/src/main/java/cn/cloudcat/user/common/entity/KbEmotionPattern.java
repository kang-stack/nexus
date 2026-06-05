package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 情绪检测词库实体，对应 acc_user_kb_emotion_pattern 表。
 * <p>
 * 支持多语言（ZH/EN/PINYIN/UNIVERSAL）、多匹配模式（CONTAINS/REGEX）、
 * 多情绪标签（ABUSE/ANGRY/SAD/URGENT/THREATEN）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_emotion_pattern")
public class KbEmotionPattern {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 情绪标签：ABUSE=辱骂, ANGRY=愤怒, SAD=沮丧, URGENT=急迫, THREATEN=威胁 */
    @TableField("emotion_tag")
    private String emotionTag;

    /** 匹配模式：CONTAINS=包含子串, REGEX=正则表达式 */
    @TableField("match_type")
    private String matchType;

    /** 匹配关键词或正则表达式 */
    private String pattern;

    /** 语言：ZH=中文, EN=英文, PINYIN=拼音, UNIVERSAL=通用（标点/数字） */
    private String language;

    /** 风险等级：HIGH/MEDIUM/LOW */
    @TableField("risk_level")
    private String riskLevel;

    /** 是否建议转人工（1=是, 0=否） */
    @TableField("suggest_human")
    private Integer suggestHuman;

    /** 安抚话术（可空，为空时使用默认安抚模板） */
    @TableField("soothe_hint")
    private String sootheHint;

    /** 优先级（越小越先匹配） */
    private Integer priority;

    /** 是否启用（1=是, 0=否） */
    private Integer enabled;
}
