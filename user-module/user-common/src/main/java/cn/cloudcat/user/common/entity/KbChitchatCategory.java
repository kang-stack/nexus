package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 闲聊分类实体，对应 acc_user_kb_chitchat_category 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_chitchat_category")
public class KbChitchatCategory {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类编码 */
    @TableField("category_code")
    private String categoryCode;

    /** 分类名称 */
    @TableField("category_name")
    private String categoryName;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
