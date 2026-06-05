package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 会话模板实体，对应 acc_user_kb_session_template 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_session_template")
public class KbSessionTemplate {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码 */
    @TableField("template_code")
    private String templateCode;

    /** 模板文本 */
    @TableField("text_template")
    private String textTemplate;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;

    /** 版本号 */
    private String version;
}
