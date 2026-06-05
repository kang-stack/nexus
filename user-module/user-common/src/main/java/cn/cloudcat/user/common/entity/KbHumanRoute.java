package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 人工客服路由规则实体，对应 acc_user_kb_human_route 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_kb_human_route")
public class KbHumanRoute {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 路由编码 */
    @TableField("route_code")
    private String routeCode;

    /** 意图匹配规则 */
    @TableField("intent_match")
    private String intentMatch;

    /** 渠道路径 */
    @TableField("channel_path")
    private String channelPath;

    /** 优先级 */
    private Integer priority;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
