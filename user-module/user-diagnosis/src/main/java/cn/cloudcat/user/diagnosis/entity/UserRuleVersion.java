package cn.cloudcat.user.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则版本实体，对应 acc_user_rule_version 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_rule_version")
public class UserRuleVersion {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码 */
    @TableField("scene_code")
    private String sceneCode;

    /** 版本号 */
    private String version;

    /** DMN 部署ID */
    @TableField("dmn_deploy_id")
    private String dmnDeployId;

    /** 发布人 */
    @TableField("published_by")
    private String publishedBy;

    /** 发布时间 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 回滚目标版本 */
    @TableField("rollback_to")
    private String rollbackTo;

    /** 状态：draft/published/archived */
    private String status;
}
