package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.message.diagnosis.DiagnosisPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 规则诊断 Skill — Phase 2 规则引擎第二步（order=21）。
 * <p>
 * 调用 DiagnosisPipeline 执行 DMN 两阶段诊断：
 * <ol>
 *   <li>evaluateFindings: 按 scene_code 评估每个维度的异常 Finding</li>
 *   <li>aggregate: 按优先级矩阵聚合 Finding 列表 → DiagnosisResult（primaryRootCause + rankedCauses）</li>
 * </ol>
 * 若 FactsCollect(20) 未加载设备事实，此处自动补加载。
 * 诊断结果写入 ctx.diagnosis，KnowledgeBranch(22) 读取 primaryRootCause 匹配分支。
 * <p>
 * Skill 链阶段：[Phase1 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class RuleDiagnosisChatSkill implements ChatSkill {

    private final DiagnosisPipeline diagnosisPipeline;

    @Override
    public int order() { return 21; }

    @Override
    public String name() { return "RuleDiagnosis"; }

    /** 未处理、不需要 RAG 且诊断未执行时触发 */
    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled() && !ctx.needRag() && ctx.diagnosis() == null && !ctx.skipRuleDiagnosis();
    }

    /** 确定意图 → 加载事实 → 执行诊断 → 设置结果 */
    @Override
    public void execute(SkillContext ctx) {
        String intent = resolveIntent(ctx);
        // 未收集设备事实时自动加载
        if (ctx.facts() == null) {
            var loaded = diagnosisPipeline.loadFacts(ctx.userId());
            ctx.setFacts(new DeviceFacts(
                    loaded.facts(), loaded.deviceSn(), Instant.now()));
        }
        // 执行 DSM DSL 规则诊断
        var dx = diagnosisPipeline.diagnoseWithFacts(
                ctx.userId(), intent, ctx.conversationId(), ctx.facts());
        ctx.setDiagnosis(dx.diagnosisResult());
        if (ctx.resolvedIntent() == null) {
            ctx.setResolvedIntent(intent);
        }
    }

    /** 按优先级解析意图：已识别意图 → 意图提示 → 用户原文 */
    private static String resolveIntent(SkillContext ctx) {
        if (ctx.resolvedIntent() != null && !ctx.resolvedIntent().isBlank()) {
            return ctx.resolvedIntent();
        }
        if (ctx.intentHint() != null && !ctx.intentHint().isBlank()) {
            return ctx.intentHint();
        }
        return ctx.userText();
    }
}
