package cn.cloudcat.user.common.constant;

import lombok.Getter;

/**
 * 快速模型枚举 — 轻量任务使用的 fast/lightweight 模型
 * <p>
 * 每个枚举值绑定模型名和对应的 provider，provider 用于复用
 * {@code icc.model.providers} 中已配置的 baseUrl 和 apiKey。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Getter
public enum FastModel {

    DEEPSEEK_V4_FLASH("deepseek-v4-flash", "deepseek"),
    QWEN3_FLASH("qwen3.6-flash", "qwen");

    private final String modelName;
    private final String providerName;

    FastModel(String modelName, String providerName) {
        this.modelName = modelName;
        this.providerName = providerName;
    }

    public static FastModel fromModelName(String modelName) {
        for (FastModel fm : values()) {
            if (fm.modelName.equals(modelName)) {
                return fm;
            }
        }
        throw new IllegalArgumentException("Unknown fast model: " + modelName);
    }
}
