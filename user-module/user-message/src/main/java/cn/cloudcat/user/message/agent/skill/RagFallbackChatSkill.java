package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.diagnosis.model.DiagnosisResult;
import cn.cloudcat.user.message.diagnosis.DiagnosisPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * RAG 兜底 Skill — Phase 3 兜底（order=30）。
 * <p>
 * <b>仅在 Phase 2 规则引擎未命中时执行</b>（KnowledgeBranch 无匹配分支 → handled=false）。
 * 构建增强查询（用户原文 + DMN 诊断结果），标记 needRag，
 * 由 UserChatTurnOrchestrator.streamRag() 走 LangChain4j ES 检索 + LLM 流式生成。
 * <p>
 * Skill 链阶段：[Phase1 预处理 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class RagFallbackChatSkill implements ChatSkill {

    private final DiagnosisPipeline diagnosisPipeline;

    @Override
    public int order() { return 30; }

    @Override
    public String name() { return "RagFallback"; }

    /** 前序技能未处理且无回复段时触发 */
    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled() && ctx.replySegments().isEmpty();
    }

    /** 必要时先补诊断/事实，然后构建增强查询并标记需要 RAG */
    @Override
    public void execute(SkillContext ctx) {
        // 未诊断也未收集事实时，先执行诊断
        if (ctx.diagnosis() == null && ctx.facts() == null) {
            String intentHint = resolveIntent(ctx);
            DiagnosisPipeline.DiagnosisContext dx =
                    diagnosisPipeline.diagnoseForIntent(ctx.userId(), intentHint, ctx.conversationId());
            ctx.setFacts(new cn.cloudcat.user.diagnosis.model.DeviceFacts(
                    dx.facts(), null, java.time.Instant.now()));
            if (dx.diagnosisResult() != null) {
                ctx.setDiagnosis(dx.diagnosisResult());
            }
            if (ctx.resolvedIntent() == null) {
                ctx.setResolvedIntent(intentHint);
            }
        }
        // 将诊断结果拼入查询，提升 ES 检索召回质量
        ctx.setRagQuery(buildEnhancedQuery(ctx));
        ctx.markNeedRag();
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

    /** 构建增强查询：用户原文 + primaryRootCause 或排名诊断原因 */
    private String buildEnhancedQuery(SkillContext ctx) {
        String base = ctx.userText();
        // 优先使用已解析的根本原因
        if (ctx.primaryRootCause() != null && !ctx.primaryRootCause().isBlank()
                && !"UNKNOWN".equals(ctx.primaryRootCause())) {
            return base + "；设备诊断：" + ctx.primaryRootCause();
        }
        // 回退：拼接排名诊断原因列表
        DiagnosisResult dx = ctx.diagnosis();
        if (dx != null && dx.rankedCauses() != null && !dx.rankedCauses().isEmpty()) {
            String causes = dx.rankedCauses().stream()
                    .map(c -> c.code() + (c.label() != null ? "(" + c.label() + ")" : ""))
                    .collect(Collectors.joining("；"));
            if (!causes.isBlank()) {
                return base + "；诊断结果：" + causes;
            }
        }
        return base;
    }
}
