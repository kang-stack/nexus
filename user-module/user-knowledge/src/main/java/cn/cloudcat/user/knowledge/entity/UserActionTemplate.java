package cn.cloudcat.user.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 客服动作模板实体，对应 acc_user_action_template 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_action_template")
public class UserActionTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 动作编码 */
    @TableField("action_code")
    private String actionCode;

    /** 动作名称 */
    @TableField("action_name")
    private String actionName;

    /** 执行成功后的提示消息（列名 success_message_template） */
    @TableField("success_message_template")
    private String successMessage;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
