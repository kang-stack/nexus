package cn.cloudcat.user.diagnosis.model;

import java.time.Instant;
import java.util.Map;

/**
 * 设备事实数据快照，供 DMN 诊断引擎作为输入变量。
 *
 * @param values      事实键值对（如信号强度、套餐状态等）
 * @param deviceSn    设备序列号
 * @param collectedAt 采集时间戳
 *
 * @author Kang
 * @since 2026-06-02
 */
public record DeviceFacts(Map<String, Object> values, String deviceSn, Instant collectedAt) {}
