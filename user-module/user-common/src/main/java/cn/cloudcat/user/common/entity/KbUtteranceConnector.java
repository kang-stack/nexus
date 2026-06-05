package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 话语分句连接词，对应 acc_user_kb_utterance_connector 表。
 */
@Data
@TableName("acc_user_kb_utterance_connector")
public class KbUtteranceConnector {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("connector_text")
    private String connectorText;

    private Integer enabled;

    @TableField("sort_order")
    private Integer sortOrder;
}
