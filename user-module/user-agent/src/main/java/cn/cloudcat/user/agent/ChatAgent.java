package cn.cloudcat.user.agent;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.agent.trace.ChatTurnMdc;
import cn.cloudcat.user.common.model.ReplySegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Skill 责任链编排器：按 order 依次执行各 {@link ChatSkill}，并在 INFO 级别输出命中追踪日志。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class ChatAgent {

    private final List<ChatSkill> skills;

    public ChatAgent(List<ChatSkill> skills) {
        this.skills = skills.stream()
                .sorted(Comparator.comparingInt(ChatSkill::order))
                .toList();
        log.info("[SkillChain] registered {} skills: {}",
                this.skills.size(),
                this.skills.stream().map(s -> s.name() + "(" + s.order() + ")").toList());
    }

    /**
     * 按优先级执行 Skill 责任链（5 阶段设计）。
     * <p>
     * 短路规则：handled=true（寒暄/规则命中/结束语）或 needRag=true（需 RAG 兜底）时停止。
     *
     * @param ctx 对话上下文（用户消息、会话信息、分段累积区）
     * @return 更新后的上下文，含 replySegments 和相关标记
     */
    public SkillContext handle(SkillContext ctx) {
        ChatTurnMdc.bind(ctx.turnId(), ctx.conversationId());
        long chainStartMs = System.currentTimeMillis();
        try {
            return doHandle(ctx, chainStartMs);
        } finally {
            ChatTurnMdc.clear();
        }
    }

    private SkillContext doHandle(SkillContext ctx, long chainStartMs) {
        log.info("[SkillChain] START turn={} conv={} text=\"{}\" hint={}",
                nullToDash(ctx.turnId()), ctx.conversationId(), abbrev(ctx.userText()), nullToDash(ctx.intentHint()));

        boolean chainBroken = false;
        String breakReason = null;

        for (ChatSkill skill : skills) {
            if (chainBroken) {
                log.info("[SkillChain]   SKIP {} order={} reason=chain-short-circuited ({})",
                        skill.name(), skill.order(), breakReason);
                ctx.trace().recordSkip(skill.name(), skill.order(),
                        "chain-short-circuited:" + breakReason);
                continue;
            }
            if (!skill.supports(ctx)) {
                log.info("[SkillChain]   SKIP {} order={} reason=unsupported",
                        skill.name(), skill.order());
                ctx.trace().recordSkip(skill.name(), skill.order(), "unsupported");
                continue;
            }

            SkillSnapshot before = SkillSnapshot.from(ctx);
            long startMs = System.currentTimeMillis();
            log.info("[SkillChain]   RUN  {} order={}", skill.name(), skill.order());

            skill.execute(ctx);

            long costMs = System.currentTimeMillis() - startMs;
            SkillSnapshot after = SkillSnapshot.from(ctx);
            String outcome = after.inferOutcome(before);
            log.info("[SkillChain]   OUT  {} order={} cost={}ms {} | {}",
                    skill.name(), skill.order(), costMs,
                    outcome, after.summary());
            ctx.trace().recordRun(skill.name(), skill.order(), costMs, outcome, after.detailDelta(before));

            if (!skill.runInPostPhase() && (ctx.handled() || ctx.needRag())) {
                chainBroken = true;
                breakReason = ctx.handled() ? "handled" : "needRag";
                log.info("[SkillChain]   BREAK after {} reason={}", skill.name(), breakReason);
            }
        }

        runPostPhase(ctx);

        long chainCostMs = System.currentTimeMillis() - chainStartMs;
        log.info("[SkillChain] END turn={} conv={} intent={} handled={} needRag={} chitchat={} segments={} emotion={}/{} human={} costMs={}",
                nullToDash(ctx.turnId()),
                ctx.conversationId(),
                nullToDash(ctx.resolvedIntent()),
                ctx.handled(),
                ctx.needRag(),
                ctx.chitchat(),
                ctx.replySegments().size(),
                nullToDash(ctx.emotionTag()),
                nullToDash(ctx.emotionRisk()),
                ctx.suggestHumanTransfer(),
                chainCostMs);

        return ctx;
    }

    /** 后处理阶段：结束语、会话状态等（不受 handled 短路影响） */
    private void runPostPhase(SkillContext ctx) {
        for (ChatSkill skill : skills) {
            if (!skill.runInPostPhase() || !skill.supports(ctx)) {
                continue;
            }
            SkillSnapshot before = SkillSnapshot.from(ctx);
            long startMs = System.currentTimeMillis();
            log.info("[SkillChain]   POST {} order={}", skill.name(), skill.order());
            skill.execute(ctx);
            long costMs = System.currentTimeMillis() - startMs;
            SkillSnapshot after = SkillSnapshot.from(ctx);
            String outcome = after.inferOutcome(before);
            log.info("[SkillChain]   POST {} order={} cost={}ms {} | {}",
                    skill.name(), skill.order(), costMs, outcome, after.summary());
            ctx.trace().recordRun(skill.name(), skill.order(), costMs, "POST " + outcome,
                    after.detailDelta(before));
        }
    }

    /** 是否有分支回复（知识库命中），即 replySegments 非空 */
    public boolean hasBranchReplies(SkillContext ctx) {
        return ctx.replySegments() != null && !ctx.replySegments().isEmpty();
    }

    /** 是否应该下发满意度卡片：仅 ClosingScript 等显式标记 appendSatisfaction 时触发 */
    public boolean shouldDispatchSatisfaction(SkillContext ctx) {
        return ctx.appendSatisfaction();
    }

    private static String abbrev(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.replace('\n', ' ').trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 57) + "...";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Skill 执行前后上下文快照，用于推断命中/未命中。
     */
    private static final class SkillSnapshot {
        private final boolean handled;
        private final boolean needRag;
        private final boolean chitchat;
        private final boolean closingTriggered;
        private final boolean appendSatisfaction;
        private final boolean suggestHumanTransfer;
        private final boolean compoundIntent;
        private final int segments;
        private final String resolvedIntent;
        private final String primaryRootCause;
        private final String emotionTag;
        private final String emotionRisk;
        private final List<String> compoundIntentCodes;
        private final boolean factsLoaded;
        private final int factKeyCount;
        private final boolean utteranceReady;

        private SkillSnapshot(
                boolean handled,
                boolean needRag,
                boolean chitchat,
                boolean closingTriggered,
                boolean appendSatisfaction,
                boolean suggestHumanTransfer,
                boolean compoundIntent,
                int segments,
                String resolvedIntent,
                String primaryRootCause,
                String emotionTag,
                String emotionRisk,
                List<String> compoundIntentCodes,
                boolean factsLoaded,
                int factKeyCount,
                boolean utteranceReady) {
            this.handled = handled;
            this.needRag = needRag;
            this.chitchat = chitchat;
            this.closingTriggered = closingTriggered;
            this.appendSatisfaction = appendSatisfaction;
            this.suggestHumanTransfer = suggestHumanTransfer;
            this.compoundIntent = compoundIntent;
            this.segments = segments;
            this.resolvedIntent = resolvedIntent;
            this.primaryRootCause = primaryRootCause;
            this.emotionTag = emotionTag;
            this.emotionRisk = emotionRisk;
            this.compoundIntentCodes = compoundIntentCodes;
            this.factsLoaded = factsLoaded;
            this.factKeyCount = factKeyCount;
            this.utteranceReady = utteranceReady;
        }

        static SkillSnapshot from(SkillContext ctx) {
            boolean factsLoaded = ctx.facts() != null;
            int factKeys = factsLoaded ? ctx.facts().values().size() : 0;
            boolean utteranceReady = ctx.utterancePlan() != null;
            return new SkillSnapshot(
                    ctx.handled(),
                    ctx.needRag(),
                    ctx.chitchat(),
                    ctx.closingTriggered(),
                    ctx.appendSatisfaction(),
                    ctx.suggestHumanTransfer(),
                    ctx.compoundIntent(),
                    ctx.replySegments().size(),
                    ctx.resolvedIntent(),
                    ctx.primaryRootCause(),
                    ctx.emotionTag(),
                    ctx.emotionRisk(),
                    new ArrayList<>(ctx.compoundIntentCodes()),
                    factsLoaded,
                    factKeys,
                    utteranceReady
            );
        }

        /** 根据快照差异推断本 Skill 是否产生有效命中 */
        String inferOutcome(SkillSnapshot before) {
            List<String> hits = new ArrayList<>();
            if (handled && !before.handled) {
                hits.add("handled");
            }
            if (needRag && !before.needRag) {
                hits.add("needRag");
            }
            if (chitchat && !before.chitchat) {
                hits.add("chitchat");
            }
            if (closingTriggered && !before.closingTriggered) {
                hits.add("closing");
            }
            if (appendSatisfaction && !before.appendSatisfaction) {
                hits.add("satisfaction");
            }
            if (suggestHumanTransfer && !before.suggestHumanTransfer) {
                hits.add("humanTransfer");
            }
            if (segments > before.segments) {
                hits.add("segments+" + (segments - before.segments));
            }
            if (!Objects.equals(resolvedIntent, before.resolvedIntent) && resolvedIntent != null) {
                hits.add("intent=" + resolvedIntent);
            }
            if (!Objects.equals(primaryRootCause, before.primaryRootCause) && primaryRootCause != null) {
                hits.add("rootCause=" + primaryRootCause);
            }
            if (!Objects.equals(emotionTag, before.emotionTag) && emotionTag != null) {
                hits.add("emotion=" + emotionTag + "/" + nullToDash(emotionRisk));
            }
            if (compoundIntent && !before.compoundIntent) {
                hits.add("compound=" + compoundIntentCodes);
            }
            if (factsLoaded && !before.factsLoaded) {
                hits.add("facts+" + factKeyCount);
            }
            if (utteranceReady && !before.utteranceReady) {
                hits.add("utterance");
            }
            if (hits.isEmpty()) {
                return "MISS";
            }
            return "HIT " + String.join(", ", hits);
        }

        /** 提取本 Skill 产生的结构化差异，写入 trace_json */
        Map<String, Object> detailDelta(SkillSnapshot before) {
            Map<String, Object> detail = new LinkedHashMap<>();
            if (handled && !before.handled) {
                detail.put("handled", true);
            }
            if (needRag && !before.needRag) {
                detail.put("needRag", true);
            }
            if (chitchat && !before.chitchat) {
                detail.put("chitchat", true);
            }
            if (closingTriggered && !before.closingTriggered) {
                detail.put("closing", true);
            }
            if (appendSatisfaction && !before.appendSatisfaction) {
                detail.put("satisfaction", true);
            }
            if (suggestHumanTransfer && !before.suggestHumanTransfer) {
                detail.put("humanTransfer", true);
            }
            if (segments > before.segments) {
                detail.put("segmentsAdded", segments - before.segments);
            }
            if (!Objects.equals(resolvedIntent, before.resolvedIntent) && resolvedIntent != null) {
                detail.put("intent", resolvedIntent);
            }
            if (!Objects.equals(primaryRootCause, before.primaryRootCause) && primaryRootCause != null) {
                detail.put("rootCause", primaryRootCause);
            }
            if (!Objects.equals(emotionTag, before.emotionTag) && emotionTag != null) {
                detail.put("emotion", emotionTag);
                detail.put("emotionRisk", nullToDash(emotionRisk));
            }
            if (compoundIntent && !before.compoundIntent) {
                detail.put("compoundIntents", compoundIntentCodes);
            }
            if (factsLoaded && !before.factsLoaded) {
                detail.put("factsKeys", factKeyCount);
            }
            return detail;
        }

        /** 当前快照摘要，便于 END 行对照 */
        String summary() {
            return "state[handled=" + handled
                    + ", needRag=" + needRag
                    + ", intent=" + nullToDash(resolvedIntent)
                    + ", facts=" + (factsLoaded ? factKeyCount : 0)
                    + ", segments=" + segments + "]";
        }
    }
}
