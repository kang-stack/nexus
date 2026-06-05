package cn.cloudcat.user.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 规则场景维度映射实体，对应 acc_user_scene_dimension 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_scene_dimension")
public class UserSceneDimension {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码 */
    @TableField("scene_code")
    private String sceneCode;

    /** 事实变量 key */
    @TableField("fact_key")
    private String factKey;

    /** 维度显示名称 */
    private String label;

    /** 数据类型：string/number/boolean/enum */
    @TableField("data_type")
    private String dataType;

    /** 数据来源 API 路径 */
    @TableField("source_api")
    private String sourceApi;

    /** 枚举值映射 JSON */
    @TableField("enum_mapping_json")
    private String enumMappingJson;

    /** 排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;
}
