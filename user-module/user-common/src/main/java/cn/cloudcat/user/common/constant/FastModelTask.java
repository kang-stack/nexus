package cn.cloudcat.user.common.constant;

import lombok.Getter;

/**
 * 快速模型任务类型枚举
 * <p>
 * 每个枚举值对应配置文件 {@code icc.model.fast.task-mapping} 中的 key，
 * 运行时通过 task→model 映射决定使用哪个快速模型。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Getter
public enum FastModelTask {

    INTENT_RECOGNITION("intent-recognition"),
    QUERY_REWRITE("query-rewrite");

    private final String configKey;

    FastModelTask(String configKey) {
        this.configKey = configKey;
    }
}
