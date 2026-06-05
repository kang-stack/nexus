package cn.cloudcat.user.diagnosis.model;

import java.util.Map;

/**
 * DMN 诊断发现项（单条结论）。
 *
 * @param factKey 发现编码（如 SIGNAL_WEAK、DATA_EXHAUSTED）
 * @param level   严重级别（INFO / WARN / ERROR）
 * @param message 人类可读描述
 * @param meta    附加元数据
 *
 * @author Kang
 * @since 2026-06-02
 */
public record Finding(String factKey, String level, String message, Map<String, Object> meta) {}
