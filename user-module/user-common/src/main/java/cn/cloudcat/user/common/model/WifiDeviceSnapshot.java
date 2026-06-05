package cn.cloudcat.user.common.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户绑定设备的只读快照，供 CS 对话与 RAG 维度采集使用（与 admin 实体解耦）。
 */
@Data
@Builder
public class WifiDeviceSnapshot {

    private String deviceId;
    private String userId;
    private String deviceModel;
    private String sn;
    private LocalDateTime activationTime;
    private BigDecimal dataUsedGb;
    private BigDecimal dataTotalGb;
    private LocalDateTime expireTime;
    private String status;
    private String diagActivationStatus;
    private String diagRealnameStatus;
    private String diagOnlineStatus;
    private String diagSignalValue;
    private String diagDeviceStatus;
}
