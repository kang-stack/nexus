package cn.cloudcat.user.message.device;

import cn.cloudcat.user.message.profile.ConsultationProfileService;

import cn.cloudcat.user.common.model.WifiDeviceSnapshot;
import cn.cloudcat.user.common.spi.UserDeviceReader;
import cn.cloudcat.user.common.constant.DimensionKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备状态查询服务（PRD 5.5 + 设计文档 Phase 5）。
 *
 * 查询用户绑定的设备，构建条件求值变量，替换阶段 2 的 mock 数据。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class DeviceStateService {

    private final UserDeviceReader userDeviceReader;
    private final ConsultationProfileService profileService;

    public DeviceStateService(UserDeviceReader userDeviceReader,
                              ConsultationProfileService profileService) {
        this.userDeviceReader = userDeviceReader;
        this.profileService = profileService;
    }

    /**
     * 构建 DMN / acc_user_kb_branch 匹配用设备事实（中文维度名 → 值）。
     */
    public Map<String, Object> buildDeviceFactMap(String userId) {
        List<WifiDeviceSnapshot> devices = userDeviceReader.listByUserId(userId);
        WifiDeviceSnapshot device = devices.isEmpty() ? null : devices.get(0);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.putAll(mapDeviceDimensions(device));
        if (device != null) {
            String deviceId = device.getDeviceId();
            ctx.put(DimensionKey.WECHAT_OFFICIAL, "");
            ctx.put(DimensionKey.WECHAT_OFFICIAL_ALT, "");
            ctx.put(DimensionKey.INVOICE_TICKET, "工单信息不存在");
            ctx.put(DimensionKey.REFUND_TICKET_STATUS, "");
            if (deviceId != null) {
                if (profileService.isHighFrequency(deviceId)) {
                    ctx.put(DimensionKey.DEVICE_TYPE_CLASSIFY, "高频用户");
                }
            }
        }
        return ctx;
    }

    /** 与 {@link #buildDeviceFactMap(String)} 相同，供 Facts 采集调用。 */
    public Map<String, Object> buildDeviceVariables(String userId) {
        return buildDeviceFactMap(userId);
    }

    /** 将设备实体映射为扁平维度 Map（中文键名） */
    private Map<String, Object> mapDeviceDimensions(WifiDeviceSnapshot device) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        // 无设备时的默认值
        if (device == null) {
            ctx.put(DimensionKey.BOUND, "否");
            ctx.put(DimensionKey.DEVICE_STATUS, "未激活");
            ctx.put(DimensionKey.ONLINE_STATUS, "离线");
            ctx.put(DimensionKey.REALNAME_STATUS, "未实名");
            ctx.put(DimensionKey.DEVICE_REALNAME_STATUS, "未实名");
            ctx.put(DimensionKey.ACTIVATION_STATUS, "未激活");
            ctx.put(DimensionKey.CARD_COUNT, "单卡");
            ctx.put(DimensionKey.SPEED_LIMITED, "否");
            ctx.put(DimensionKey.SPEED_LIMITED_STATUS, "否");
            ctx.put(DimensionKey.SIGNAL_VALUE, "信号差");
            ctx.put(DimensionKey.AUTO_RENEW, "否");
            return ctx;
        }

        ctx.put(DimensionKey.BOUND, device.getUserId() != null ? "是" : "否");
        String realname = overrideOr(mapRealNameStatus(device), device.getDiagRealnameStatus());
        String activation = overrideOr(
                device.getActivationTime() != null ? "已激活" : "未激活",
                device.getDiagActivationStatus());
        String online = overrideOr(
                "active".equals(device.getStatus()) ? "在线" : "离线",
                device.getDiagOnlineStatus());
        String signal = overrideOr(
                estimateSignalLevel(device) >= 3 ? "信号好" : "信号差",
                device.getDiagSignalValue());
        String deviceStatus = overrideOr(mapDeviceStatus(device), device.getDiagDeviceStatus());

        ctx.put(DimensionKey.DEVICE_STATUS, deviceStatus);
        ctx.put(DimensionKey.ONLINE_STATUS, online);
        ctx.put(DimensionKey.REALNAME_STATUS, realname);
        ctx.put(DimensionKey.DEVICE_REALNAME_STATUS, realname);
        ctx.put(DimensionKey.ACTIVATION_STATUS, activation);
        ctx.put(DimensionKey.CARD_COUNT, "单卡");
        ctx.put(DimensionKey.PLAN_SPEED, "中");
        ctx.put(DimensionKey.PLAN_SPEED_ALT, "中");
        ctx.put(DimensionKey.SPEED_LIMITED, "否");
        ctx.put(DimensionKey.SPEED_LIMITED_STATUS, "否");
        ctx.put(DimensionKey.CARD_STATUS, "在用");
        ctx.put(DimensionKey.CARRIER, "电信");
        ctx.put(DimensionKey.SIGNAL_VALUE, signal);
        ctx.put(DimensionKey.AUTO_RENEW, "否");
        ctx.put(DimensionKey.DEVICE_TYPE, device.getDeviceModel() != null ? device.getDeviceModel() : "");
        ctx.put(DimensionKey.DEVICE_TYPE_CLASSIFY, device.getDeviceModel() != null ? device.getDeviceModel() : "");
        ctx.put(DimensionKey.MERCHANT, "");
        ctx.put(DimensionKey.WIFI_CONNECTIONS, "1");
        ctx.put(DimensionKey.WIFI_CONNECTIONS_ALT, "1");

        // 未显式覆盖设备状态时，由套餐/流量推导
        if (device.getDiagDeviceStatus() == null || device.getDiagDeviceStatus().isBlank()) {
            if (device.getExpireTime() != null && device.getExpireTime().isBefore(LocalDateTime.now())) {
                ctx.put(DimensionKey.DEVICE_STATUS, "服务到期");
            }
            if (device.getDataTotalGb() != null && device.getDataUsedGb() != null
                    && device.getDataUsedGb().compareTo(device.getDataTotalGb()) >= 0) {
                ctx.put(DimensionKey.DEVICE_STATUS, "流量用完");
                ctx.put(DimensionKey.DISCONNECT_TYPE, "达量断网");
            }
        }
        if (device.getActivationTime() != null) {
            long days = java.time.Duration.between(device.getActivationTime(), LocalDateTime.now()).toDays();
            ctx.put(DimensionKey.ACTIVATION_DAYS, String.valueOf(days));
        }
        return ctx;
    }

    private static String overrideOr(String computed, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return computed;
    }

    /** 将设备状态映射为中文描述：未激活 → 设备禁用 → 服务到期 → 可用 */
    private String mapDeviceStatus(WifiDeviceSnapshot device) {
        if (device.getActivationTime() == null) {
            return "未激活";
        }
        if ("disabled".equalsIgnoreCase(device.getStatus())) {
            return "设备禁用";
        }
        if (device.getExpireTime() != null && device.getExpireTime().isBefore(LocalDateTime.now())) {
            return "服务到期";
        }
        return "可用";
    }

    /** 根据 SN 和设备激活状态映射实名状态 */
    private String mapRealNameStatus(WifiDeviceSnapshot device) {
        if (device.getSn() == null || device.getSn().isBlank()) {
            return "未实名";
        }
        if (device.getActivationTime() != null) {
            return "全部实名";
        }
        return "部分实名";
    }

    /**
     * @deprecated 旧嵌套变量结构，请使用 {@link #buildDeviceFactMap(String)}
     */
    @Deprecated
    public Map<String, Object> buildVariables(String userId) {
        return buildDeviceFactMap(userId);
    }

    private Map<String, Object> buildDeviceVariables(WifiDeviceSnapshot device) {
        return mapDeviceDimensions(device);
    }

    private Map<String, Object> buildUserVariables(WifiDeviceSnapshot device) {
        String deviceId = device != null ? device.getDeviceId() : null;
        return Map.of("user", Map.of(
                "highFreqFlag", deviceId != null && profileService.isHighFrequency(deviceId),
                "networkIssueFlag", deviceId != null && profileService.isNetworkIssue(deviceId)
        ));
    }

    private Map<String, Object> buildDeviceInfo(WifiDeviceSnapshot device) {
        Map<String, Object> info = new LinkedHashMap<>();

        // 设备在线状态（有 user_id 绑定 + 状态 active → online）
        String status = "active".equals(device.getStatus()) ? "online" : "offline";
        info.put("status", status);

        // 信号强度（无直接字段，从数据使用情况推断）
        info.put("signalLevel", estimateSignalLevel(device));

        // 流量使用
        info.put("dataUsedGB", device.getDataUsedGb() != null
                ? device.getDataUsedGb().doubleValue() : 0);
        info.put("dataTotalGB", device.getDataTotalGb() != null
                ? device.getDataTotalGb().doubleValue() : 0);

        // 套餐是否到期
        boolean expired = device.getExpireTime() != null
                && device.getExpireTime().isBefore(LocalDateTime.now());
        info.put("expired", expired);

        // 设备型号
        info.put("deviceModel", device.getDeviceModel() != null ? device.getDeviceModel() : "");

        return info;
    }

    /**
     * 估算信号强度（1-5）。无真实数据时返回默认值 3。
     */
    private int estimateSignalLevel(WifiDeviceSnapshot device) {
        // 有 SIM 卡绑定 + 激活 → 假设正常信号
        if (device.getSn() != null && device.getActivationTime() != null) {
            return 3;
        }
        return 0;
    }
}
