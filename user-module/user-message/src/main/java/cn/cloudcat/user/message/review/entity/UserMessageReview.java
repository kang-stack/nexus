package cn.cloudcat.user.message.review.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息审核记录实体，对应 acc_user_message_review 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_message_review")
public class UserMessageReview {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被审核消息ID */
    @TableField("message_id")
    private String messageId;

    /** 关联会话ID */
    @TableField("conversation_id")
    private String conversationId;

    /** 审核状态：pending/reviewed/escalated */
    @TableField("review_status")
    private String reviewStatus;

    /** 风险等级：low/medium/high/critical */
    @TableField("risk_level")
    private String riskLevel;

    /** 情绪标签 */
    @TableField("emotion_tag")
    private String emotionTag;

    /** 审核备注 */
    @TableField("review_note")
    private String reviewNote;

    /** 审核管理员ID */
    @TableField("reviewer_admin_id")
    private Long reviewerAdminId;

    /** 审核时间 */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
