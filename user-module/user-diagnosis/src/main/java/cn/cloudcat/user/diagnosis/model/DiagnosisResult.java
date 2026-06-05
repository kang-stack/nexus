package cn.cloudcat.user.diagnosis.model;

import java.util.List;

/**
 * DMN 诊断聚合结果。
 *
 * @param primaryRootCause 主根因编码（如 UNKNOWN、SIGNAL_WEAK）
 * @param rankedCauses     按优先级排序的所有根因列表
 * @param ruleVersion      使用的规则版本标识
 *
 * @author Kang
 * @since 2026-06-02
 */
public record DiagnosisResult(
        String primaryRootCause,
        List<RankedCause> rankedCauses,
        String ruleVersion) {

    /** 优先级排名的根因项 */
    public record RankedCause(String code, int priority, String label) {}
}
