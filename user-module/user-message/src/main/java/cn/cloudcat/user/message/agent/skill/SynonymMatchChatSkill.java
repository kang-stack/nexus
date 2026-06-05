package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.knowledge.engine.FaqAliasMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 同义词/别名匹配 Skill — 位于 LLM 意图识别<b>之前</b>的 FastPath 短路（order=12）。
 * <p>
 * <b>为什么在 IntentRecognition(13) 之前？</b>
 * acc_user_kb_faq_alias 是运营维护的高频业务别名表（"连不上网"→网络问题），
 * 精确匹配/包含匹配无需 LLM，可直接设置 resolvedIntent。
 * 命中后 IntentRecognitionSkill 的 supports() 检查 ctx.resolvedIntent()!=null 会跳过 LLM 调用，
 * 节省 Token 成本 + 首响延迟。
 * <p>
 * 完整 Skill 链顺序：
 * Chitchat(10) → Emotion(11) → <b>Synonym(12)</b> → Intent(13) → Facts(14) →
 * Closing(15) → RuleDiagnosis(16) → KnowledgeBranch(20) → Rag(30) → SessionState(90)
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SynonymMatchChatSkill implements ChatSkill {

    private final FaqAliasMatchService faqAliasMatchService;

    @Override
    public int order() { return 12; }

    @Override
    public String name() { return "SynonymMatch"; }

    /** 话语理解未预填意图时，再走 FAQ 别名 FastPath */
    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled() && !ctx.continueActiveTask() && ctx.resolvedIntent() == null
                && (ctx.utterancePlan() == null || ctx.utterancePlan().businessIntents().isEmpty());
    }

    /**
     * 通过 FAQ 别名匹配用户输入（EXACT / CONTAINS / REGEX）。
     * <p>
     * 命中 → 直接设置 ctx.resolvedIntent，后续 IntentRecognitionSkill(13) 的 supports()
     * 检查 resolvedIntent!=null 会跳过 LLM 意图识别，直接进入 Facts→DMN→KnowledgeBranch 链路。
     * 未命中 → 不做任何操作，由 IntentRecognitionSkill 走 LLM 识别。
     */
    @Override
    public void execute(SkillContext ctx) {
        List<String> intents = faqAliasMatchService.matchAllIntents(ctx.effectiveUserTextForIntent());
        if (intents.isEmpty()) {
            return;
        }
        ctx.setResolvedIntent(intents.get(0));
        if (intents.size() > 1) {
            int cap = Math.min(intents.size(), 2);
            ctx.setCompoundIntents(intents.subList(0, cap));
            log.info("SynonymMatch compound conv={} intents={}", ctx.conversationId(), intents.subList(0, cap));
        }
    }
}
