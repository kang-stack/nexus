package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 闲聊匹配模式实体，对应 acc_user_kb_chitchat_pattern 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_chitchat_pattern")
public class KbChitchatPattern {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类编码 */
    @TableField("category_code")
    private String categoryCode;

    /** 匹配模式文本 */
    private String pattern;

    /** 匹配类型：exact/contains/regex */
    @TableField("match_type")
    private String matchType;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
