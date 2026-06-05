package cn.cloudcat.user.session.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话实体，对应 acc_user_conversation 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_conversation")
public class UserConversation {

    /** 会话主键ID */
    @TableId
    private String id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备序列号 */
    @TableField("device_sn")
    private String deviceSn;

    /** 渠道来源 */
    private String channel;

    /** 入口意图 */
    @TableField("entry_intent")
    private String entryIntent;

    /** 最近一次诊断运行ID */
    @TableField("latest_diagnosis_id")
    private String latestDiagnosisId;

    /** 话题标识 */
    @TableField("topic_key")
    private String topicKey;

    /** 对话轮次计数 */
    @TableField("turn_count")
    private Integer turnCount;

    /** 纯闲聊轮次计数 */
    @TableField("chitchat_only_count")
    private Integer chitchatOnlyCount;

    /** 是否已给出解决方案：0-未给出，1-已给出 */
    @TableField("solution_given")
    private Integer solutionGiven;

    /** AI 回复是否锁定（如转人工后） */
    @TableField("ai_reply_locked")
    private Integer aiReplyLocked;

    /** 会话状态 */
    private String status;

    /** 满意度状态 */
    @TableField("satisfaction_status")
    private String satisfactionStatus;

    /** 会话关闭原因 */
    @TableField("close_reason")
    private String closeReason;

    /** 用户最后发言时间 */
    @TableField("last_user_at")
    private LocalDateTime lastUserAt;

    /** AI 最后回复时间 */
    @TableField("last_ai_at")
    private LocalDateTime lastAiAt;

    /** 扩展JSON字段 */
    @TableField("ext_json")
    private String extJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
