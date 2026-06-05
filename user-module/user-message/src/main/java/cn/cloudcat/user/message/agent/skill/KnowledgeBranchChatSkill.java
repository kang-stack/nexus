package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.knowledge.service.KnowledgeBranchService;
import cn.cloudcat.user.message.diagnosis.DiagnosisPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识分支匹配 Skill — Phase 2 规则引擎第三步（order=22）。
 * <p>
 * 核心职责：根据 IntentRecognition/SynonymMatch 识别的 intent_code +
 * RuleDiagnosis DMN 诊断的 primaryRootCause，匹配 acc_user_kb_branch 条件 →
 * acc_user_kb_reply_script 话术模板 → 组装 ReplySegment 列表。
 * <p>
 * 支持复合意图（compound intent）：最多合并 2 个子意图的分支回复。
 * 命中 → markHandled → RagFallback(30) 跳过。
 * 未命中 → 不 markHandled → RagFallback(30) 执行 LLM 兜底。
 * <p>
 * Skill 链阶段：[Phase1 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBranchChatSkill implements ChatSkill {

    private final KnowledgeBranchService knowledgeBranchService;
    private final DiagnosisPipeline diagnosisPipeline;

    @Override
    public int order() { return 22; }

    @Override
    public String name() { return "KnowledgeBranch"; }

    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled() && !ctx.needRag(); // 仅在前序 Skill 未处理且不需要 RAG 时执行
    }

    @Override
    public void execute(SkillContext ctx) {
        prependNotices(ctx);
        if (ctx.compoundIntent()) {
            // 复合意图：多意图分别匹配分支，合并回复
            handleCompound(ctx);
            return;
        }
        // 单意图：直接匹配分支
        applyBranchReply(ctx, resolveIntent(ctx), ctx.primaryRootCause(), ctx.userText(), 0, 1);
    }

    // 处理复合意图（最多 2 个子意图）：分别 diagnosis → resolve → 合并 segments
    private void handleCompound(SkillContext ctx) {
        List<String> intents = ctx.compoundIntentCodes();
        int taskTotal = Math.min(intents.size(), 2); // 最多 2 个子任务
        List<ReplySegment> merged = new ArrayList<>();
        for (int i = 0; i < taskTotal; i++) {
            String intentCode = intents.get(i);
            String rootCause = resolveRootCauseForIntent(ctx, intentCode); // 为每个子意图单独诊断
            List<ReplySegment> part = knowledgeBranchService.resolve(
                    ctx.userText(), intentCode, rootCause, ctx.userId(), buildBranchContext(ctx));
            if (part.isEmpty()) {
                continue;
            }
            for (ReplySegment seg : part) {
                seg.setIntentLabel(intentCode);
                seg.setTaskIndex(i);
                seg.setTaskTotal(taskTotal);
                seg.setNewBubble(i > 0 || !merged.isEmpty());
            }
            merged.addAll(part);
        }
        if (merged.isEmpty()) {
            return;
        }
        ctx.addSegments(merged);
        renumberSegments(ctx);
        if (ctx.conversation() != null) {
            ctx.conversation().setSolutionGiven(1);
        }
        ctx.markHandled();
    }

    // 通过 acc_user_kb_branch + acc_user_kb_reply_script 组装回复段列表，设置序号/气泡/结束标记
    private void applyBranchReply(SkillContext ctx, String intent, String rootCause,
                                  String userText, int taskIndex, int taskTotal) {
        var segments = knowledgeBranchService.resolve(
                userText, intent, rootCause, ctx.userId(), buildBranchContext(ctx));
        if (segments.isEmpty()) {
            log.info("[SkillChain] KnowledgeBranch MISS intent={} rootCause={} -> will try RAG",
                    intent, rootCause);
            return;
        }
        if (segments.stream().anyMatch(s -> ReplySegment.HUMAN_SERVICE.equals(s.getMessageType()))) {
            ctx.setSuggestHumanTransfer(true);
        }
        int offset = ctx.replySegments().size();
        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).setNewBubble(offset > 0 || i > 0);
            segments.get(i).setTaskIndex(taskIndex);
            segments.get(i).setTaskTotal(taskTotal);
        }
        ctx.addSegments(segments);
        renumberSegments(ctx);
        if (ctx.conversation() != null) {
            ctx.conversation().setSolutionGiven(1); // 标记已给方案
        }
        ctx.markHandled();
    }

    // 为复合意图的单个子意图单独执行 DMN 诊断
    private String resolveRootCauseForIntent(SkillContext ctx, String intentCode) {
        if (ctx.facts() == null) {
            return ctx.primaryRootCause();
        }
        try {
            var dx = diagnosisPipeline.diagnoseWithFacts(
                    ctx.userId(), intentCode, ctx.conversationId(), ctx.facts());
            if (dx.diagnosisResult() != null) {
                return dx.diagnosisResult().primaryRootCause();
            }
        } catch (Exception ignored) {
            // 子意图诊断失败时回退到主诊断结果
        }
        return ctx.primaryRootCause();
    }

    private static void renumberSegments(SkillContext ctx) {
        List<ReplySegment> all = ctx.replySegments();
        int total = all.size();
        for (int i = 0; i < total; i++) {
            ReplySegment seg = all.get(i);
            seg.setSequenceNumber(i + 1);
            seg.setTotalSequence(total);
        }
    }

    private java.util.Map<String, Object> buildBranchContext(SkillContext ctx) {
        java.util.Map<String, Object> matchCtx = new java.util.LinkedHashMap<>(ctx.factMap());
        if (ctx.dialogueState() != null) {
            matchCtx.put("activeTask", ctx.dialogueState().getActiveTask());
            matchCtx.put("dialogueStep", String.valueOf(ctx.dialogueState().getStep()));
        }
        return matchCtx;
    }

    private static void prependNotices(SkillContext ctx) {
        if (ctx.pendingIntentNotice() != null && !ctx.pendingIntentNotice().isBlank()) {
            ReplySegment notice = new ReplySegment("TEXT", ctx.pendingIntentNotice(), 1, 1);
            notice.setNewBubble(false);
            ctx.addSegments(List.of(notice));
        }
        if (ctx.utterancePlan() != null && ctx.utterancePlan().mixedChitchat()) {
            String ack = ctx.utterancePlan().prefixChitchatAck();
            if (ack != null && !ack.isBlank()) {
                ReplySegment ackSeg = new ReplySegment("TEXT", ack, 1, 1);
                ackSeg.setNewBubble(false);
                ctx.addSegments(List.of(ackSeg));
            }
        }
    }

    // 获取待匹配的 intent：resolvedIntent > intentHint > userText（fallback）
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
