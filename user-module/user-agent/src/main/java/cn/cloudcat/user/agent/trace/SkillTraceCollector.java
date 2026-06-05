package cn.cloudcat.user.agent.trace;

import cn.cloudcat.user.agent.skill.SkillContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮对话 Skill 链决策轨迹收集器，供落库 trace_json 与日志复盘。
 *
 * @author Kang
 * @since 2026-06-02
 */
public class SkillTraceCollector {

    private final List<Map<String, Object>> skills = new ArrayList<>();
    private String outcome;
    private long totalCostMs;
    private String resolvedIntent;
    private String primaryRootCause;
    private int segmentCount;
    private boolean handled;
    private boolean needRag;
    private boolean compoundIntent;
    private List<String> compoundIntentCodes = List.of();

    /** 记录 Skill 被跳过 */
    public void recordSkip(String name, int order, String reason) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", name);
        step.put("order", order);
        step.put("action", "SKIP");
        step.put("outcome", "SKIP");
        step.put("detail", Map.of("reason", reason != null ? reason : ""));
        skills.add(step);
    }

    /** 记录 Skill 执行结果 */
    public void recordRun(String name, int order, long costMs, String outcome, Map<String, Object> detail) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", name);
        step.put("order", order);
        step.put("action", "RUN");
        step.put("costMs", costMs);
        step.put("outcome", outcome != null ? outcome : "MISS");
        if (detail != null && !detail.isEmpty()) {
            step.put("detail", detail);
        }
        skills.add(step);
    }

    /** Skill 链结束后写入轮次级摘要 */
    public void finalizeSummary(SkillContext ctx, String outcome, long totalCostMs) {
        this.outcome = outcome;
        this.totalCostMs = totalCostMs;
        this.resolvedIntent = ctx.resolvedIntent();
        this.primaryRootCause = ctx.primaryRootCause();
        this.segmentCount = ctx.replySegments().size();
        this.handled = ctx.handled();
        this.needRag = ctx.needRag();
        this.compoundIntent = ctx.compoundIntent();
        this.compoundIntentCodes = ctx.compoundIntentCodes().isEmpty()
                ? List.of()
                : List.copyOf(ctx.compoundIntentCodes());
    }

    /** 转为可 JSON 序列化的 trace 结构 */
    public Map<String, Object> toTraceJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("skills", skills);
        root.put("finalPath", outcome);
        root.put("totalCostMs", totalCostMs);
        root.put("resolvedIntent", resolvedIntent);
        root.put("primaryRootCause", primaryRootCause);
        root.put("segmentCount", segmentCount);
        root.put("handled", handled);
        root.put("needRag", needRag);
        root.put("compoundIntent", compoundIntent);
        if (!compoundIntentCodes.isEmpty()) {
            root.put("compoundIntentCodes", compoundIntentCodes);
        }
        return root;
    }

    public String outcome() {
        return outcome;
    }

    public long totalCostMs() {
        return totalCostMs;
    }
}
