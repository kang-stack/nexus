package cn.cloudcat.user.portal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备实体类，映射 acc_user_device 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_device")
public class PortalDevice {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备唯一标识 */
    @TableField("device_id")
    private String deviceId;

    /** 绑定的用户ID */
    @TableField("user_id")
    private String userId;

    /** 设备型号 */
    private String deviceModel;

    /** 序列号 */
    private String sn;

    /** IMEI号 */
    private String imei;

    /** 激活时间 */
    private LocalDateTime activationTime;

    /** 数据套餐名称 */
    private String dataPlan;

    /** 已用流量（GB） */
    private BigDecimal dataUsedGb;

    /** 总流量（GB） */
    private BigDecimal dataTotalGb;

    /** 套餐过期时间 */
    private LocalDateTime expireTime;

    /** 设备状态（active/disabled等） */
    private String status;

    /** 备注信息 */
    private String remark;

    /** 诊断维度覆盖：激活状态（已激活/未激活），为空则按 activation_time 推导 */
    @TableField("diag_activation_status")
    private String diagActivationStatus;

    /** 诊断维度覆盖：实名状态 */
    @TableField("diag_realname_status")
    private String diagRealnameStatus;

    /** 诊断维度覆盖：在线状态 */
    @TableField("diag_online_status")
    private String diagOnlineStatus;

    /** 诊断维度覆盖：信号值 */
    @TableField("diag_signal_value")
    private String diagSignalValue;

    /** 诊断维度覆盖：设备状态 */
    @TableField("diag_device_status")
    private String diagDeviceStatus;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
