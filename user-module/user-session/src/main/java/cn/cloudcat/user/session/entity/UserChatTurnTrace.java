package cn.cloudcat.user.session.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话轮次决策轨迹，对应 acc_user_chat_turn_trace 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_chat_turn_trace")
public class UserChatTurnTrace {

    /** 轮次 traceId（雪花 ID，与 turn_id 相同） */
    @TableId
    private String id;

    @TableField("turn_id")
    private String turnId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("user_message_id")
    private String userMessageId;

    @TableField("user_id")
    private Long userId;

    /** 用户业务 ID（JWT sub，与 acc_user_account.user_id 一致） */
    @TableField("user_biz_id")
    private String userBizId;

    /** 用户名快照（写入时从 acc_user_account 解析） */
    private String username;

    /** 用户原文（截断存储） */
    @TableField("user_text")
    private String userText;

    /** 最终路径：BRANCH / RAG / CHITCHAT / DUPLICATE / ERROR */
    private String outcome;

    @TableField("resolved_intent")
    private String resolvedIntent;

    @TableField("primary_root_cause")
    private String primaryRootCause;

    @TableField("segment_count")
    private Integer segmentCount;

    @TableField("total_cost_ms")
    private Long totalCostMs;

    @TableField("trace_json")
    private String traceJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
