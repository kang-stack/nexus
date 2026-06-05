package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.knowledge.engine.IntentResolverService;
import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import cn.cloudcat.user.message.ai.service.MultiIntentRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图识别 Skill — Phase 1 预处理 LLM 调用（order=13）。
 * <p>
 * 调用 MultiIntentRecognitionService LLM 多标签意图识别，支持复合意图。
 * <b>仅在 SynonymMatch(12) 未命中时执行</b>（supports 检查 resolvedIntent==null）。
 * 意图不相关时标记 needRag，后续 RagFallback(30) 走 RAG 兜底。
 * <p>
 * Skill 链阶段：[Phase1 预处理 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntentRecognitionChatSkill implements ChatSkill {

    private final MultiIntentRecognitionService intentRecognitionService;
    private final IntentResolverService intentResolver;

    @Override
    public int order() { return 13; }

    @Override
    public String name() { return "IntentRecognition"; }

    /** 已处理/已识别意图/需要 RAG 时跳过；有意图提示且与原文不同时不触发 */
    @Override
    public boolean supports(SkillContext ctx) {
        if (ctx.handled() || ctx.needRag()) {
            return false;
        }
        if (ctx.continueActiveTask()) {
            return false;
        }
        if (ctx.utterancePlan() != null && ctx.utterancePlan().llmTurnPolicyAttempted()) {
            return false;
        }
        if (ctx.resolvedIntent() != null && !ctx.resolvedIntent().isBlank()) {
            return false;
        }
        String hint = ctx.intentHint();
        return hint == null || hint.isBlank() || hint.equals(ctx.userText());
    }

    /** 调用 LLM 意图识别，标准化结果，处理复合意图 */
    @Override
    public void execute(SkillContext ctx) {
        try {
            IntentRecognitionResult raw = intentRecognitionService.chat(
                    ctx.conversationId(), ctx.effectiveUserTextForIntent());
            IntentRecognitionResult result = raw != null ? raw.normalized() : IntentRecognitionResult.unrelated();
            // 不相关则跳过，交由后续 RAG 兜底
            if (!result.related()) {
                log.debug("intent unrelated conv={}", ctx.conversationId());
                return;
            }
            // 解析主意图编码
            String primary = result.intents().isEmpty() ? result.intent() : result.intents().get(0).intent();
            String code = intentResolver.resolve(primary);
            if (code == null || code.isBlank()) {
                code = primary;
            }
            ctx.setResolvedIntent(code);
            // 处理复合意图（多个子句 → 多个意图）
            if (result.isCompound()) {
                List<String> codes = new ArrayList<>();
                for (IntentRecognitionResult.IntentItem item : result.intents()) {
                    String c = intentResolver.resolve(item.intent());
                    if (c != null && !c.isBlank() && !codes.contains(c)) {
                        codes.add(c);
                    }
                }
                if (codes.size() > 1) {
                    ctx.setCompoundIntents(codes);
                    log.info("IntentRecognition compound conv={} intents={}", ctx.conversationId(), codes);
                }
            }
            log.info("IntentRecognition conv={} intent={}", ctx.conversationId(), code);
        } catch (Exception e) {
            log.warn("IntentRecognition failed conv={}", ctx.conversationId(), e);
        }
    }
}
