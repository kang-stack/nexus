package cn.cloudcat.user.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则场景实体，对应 acc_user_rule_scene 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_rule_scene")
public class UserRuleScene {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码 */
    @TableField("scene_code")
    private String sceneCode;

    /** 场景名称 */
    @TableField("scene_name")
    private String sceneName;

    /** 维度定义JSON Schema */
    @TableField("dimension_schema_json")
    private String dimensionSchemaJson;

    /** DMN 主决策表资源路径 */
    @TableField("dmn_resource_path")
    private String dmnResourcePath;

    /** DMN 优先级决策表路径 */
    @TableField("priority_dmn_path")
    private String priorityDmnPath;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
