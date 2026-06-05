package cn.cloudcat.user.session.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服消息实体，对应 acc_user_message 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_message")
public class UserMessage {

    /** 消息主键ID */
    @TableId
    private String id;

    /** 关联会话ID */
    @TableField("conversation_id")
    private String conversationId;

    /** 消息角色：user/assistant/system */
    private String role;

    /** 内容类型：text/markdown/json/buttons */
    @TableField("content_type")
    private String contentType;

    /** 消息正文内容 */
    private String content;

    /** 附加负载JSON（按钮、动作等） */
    @TableField("payload_json")
    private String payloadJson;

    /** 消息触发类型 */
    @TableField("trigger_type")
    private String triggerType;

    /** 触发源引用 */
    @TableField("trigger_ref")
    private String triggerRef;

    /** 回复来源：rag/rule/chitchat/diagnosis */
    @TableField("reply_source")
    private String replySource;

    /** 意图编码 */
    @TableField("intent_code")
    private String intentCode;

    /** 知识库分支ID */
    @TableField("branch_id")
    private Long branchId;

    /** 关联诊断ID */
    @TableField("diagnosis_id")
    private String diagnosisId;

    /** 消息序号（会话内递增） */
    @TableField("seq_no")
    private Integer seqNo;

    /** 客户端消息ID（幂等去重） */
    @TableField("client_msg_id")
    private String clientMsgId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
