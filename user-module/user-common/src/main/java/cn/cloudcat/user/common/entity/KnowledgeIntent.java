package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库意图实体，对应 acc_user_kb_intent 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_intent")
public class KnowledgeIntent {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 意图编码 */
    @TableField("intent_code")
    private String intentCode;

    /** 意图名称 */
    @TableField("intent_name")
    private String intentName;

    /** 分类 */
    private String category;

    /** 同义词列表（逗号分隔或 JSON 数组） */
    private String synonyms;

    /** 是否需要诊断：0-不需要，1-需要 */
    @TableField("need_diagnosis")
    private Integer needDiagnosis;

    /** 关联规则场景编码 */
    @TableField("rule_scene_code")
    private String ruleSceneCode;

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
