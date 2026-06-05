package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分支实体，对应 acc_user_kb_branch 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_branch")
public class KbBranch {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联意图编码 */
    @TableField("intent_code")
    private String intentCode;

    /** 分支编码 */
    @TableField("branch_code")
    private String branchCode;

    /** 分支名称 */
    @TableField("branch_name")
    private String branchName;

    /**
     * 已废弃：分支匹配仅使用 {@link #conditionJson}（见 BranchConditionMatcher）。
     */
    @Deprecated
    @TableField("condition_expr")
    private String conditionExpr;

    /** 分支匹配条件 JSON（AND/OR + field/op/val） */
    @TableField("condition_json")
    private String conditionJson;

    /** 优先级（数值越小越优先） */
    private Integer priority;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;

    /** 版本号 */
    private String version;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
