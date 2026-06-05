package cn.cloudcat.user.common.constant;

import lombok.Getter;

/**
 * 重排序模型枚举
 * <p>
 * 枚举值绑定配置名称（与 {@code icc.reranker.models[].name} 对应）和模型类型，
 * 类型决定初始化方式：onnx 本地加载，dashscope 远程 API 调用。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Getter
public enum RerankerModel {

    BGE_ONNX("bge-onnx", "onnx"),
    QWEN3_RERANK("qwen3-rerank", "dashscope");

    private final String configName;
    private final String type;

    RerankerModel(String configName, String type) {
        this.configName = configName;
        this.type = type;
    }

    public static RerankerModel fromConfigName(String configName) {
        for (RerankerModel rm : values()) {
            if (rm.configName.equals(configName)) {
                return rm;
            }
        }
        throw new IllegalArgumentException("Unknown reranker model: " + configName);
    }
}
