package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * FAQ 别名实体，对应 acc_user_kb_faq_alias 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_faq_alias")
public class KbFaqAlias {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 别名文本 */
    @TableField("alias_text")
    private String aliasText;

    /** 关联意图编码 */
    @TableField("intent_code")
    private String intentCode;

    /** 关联 FAQ ID */
    @TableField("faq_id")
    private String faqId;

    /** 匹配类型：exact/fuzzy */
    @TableField("match_type")
    private String matchType;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
