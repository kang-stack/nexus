package cn.cloudcat.user.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则诊断运行记录实体，对应 acc_user_diagnosis_run 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_diagnosis_run")
public class UserDiagnosisRun {

    /** 诊断记录主键ID */
    @TableId
    private String id;

    /** 关联会话ID */
    @TableField("conversation_id")
    private String conversationId;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备序列号 */
    @TableField("device_sn")
    private String deviceSn;

    /** 诊断快照JSON */
    @TableField("snapshot_json")
    private String snapshotJson;

    /** 诊断发现JSON */
    @TableField("findings_json")
    private String findingsJson;

    /** 诊断报告JSON */
    @TableField("report_json")
    private String reportJson;

    /** 使用的规则版本 */
    @TableField("rule_version")
    private String ruleVersion;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
