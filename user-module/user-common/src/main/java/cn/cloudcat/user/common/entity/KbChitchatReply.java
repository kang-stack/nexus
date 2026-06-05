package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 闲聊回复模板实体，对应 acc_user_kb_chitchat_reply 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_chitchat_reply")
public class KbChitchatReply {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类编码 */
    @TableField("category_code")
    private String categoryCode;

    /** 会话阶段：opening/closing/idle */
    @TableField("session_phase")
    private String sessionPhase;

    /** 回复文本模板 */
    @TableField("text_template")
    private String textTemplate;

    /** 是否追加FAQ快捷提问 */
    @TableField("append_faq_chips")
    private Integer appendFaqChips;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
