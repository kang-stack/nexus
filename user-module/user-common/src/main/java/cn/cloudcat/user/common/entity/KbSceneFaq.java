package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 场景 FAQ 入口实体，对应 acc_user_kb_scene_faq 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_scene_faq")
public class KbSceneFaq {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 意图编码 */
    @TableField("intent_code")
    private String intentCode;

    /** 显示标签 */
    private String label;

    /** 图标 */
    private String icon;

    /** 入口引导消息 */
    @TableField("entry_message")
    private String entryMessage;

    /** 排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
