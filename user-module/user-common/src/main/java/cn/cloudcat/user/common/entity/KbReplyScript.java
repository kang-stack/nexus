package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 回复话术脚本实体，对应 acc_user_kb_reply_script 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_reply_script")
public class KbReplyScript {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分支ID */
    @TableField("branch_id")
    private Long branchId;

    /** 脚本序号（分支内排序） */
    @TableField("seq_no")
    private Integer seqNo;

    /** 内容类型：text/markdown/buttons */
    @TableField("content_type")
    private String contentType;

    /** 回复文本模板 */
    @TableField("text_template")
    private String textTemplate;

    /** 附加负载JSON */
    @TableField("payload_json")
    private String payloadJson;

    /** 发送前延迟秒数 */
    @TableField("delay_seconds")
    private Integer delaySeconds;
}
