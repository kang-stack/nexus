package cn.cloudcat.user.message.policy;

import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.DialogueTasks;
import cn.cloudcat.user.knowledge.engine.IntentResolverService;
import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import cn.cloudcat.user.message.ai.service.MultiIntentRecognitionService;
import cn.cloudcat.user.message.config.DialoguePolicyProperties;
import cn.cloudcat.user.message.utterance.IntentPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Phase 3：规则话语理解未产出业务意图时，用 LLM 做 Turn Policy 调度（仅意图标签，不判设备/DMN）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmTurnPolicyService {

    private static final int MAX_INTENTS_THIS_TURN = 2;
    private static final String CHITCHAT_LABEL = "闲聊";

    private final DialoguePolicyProperties properties;
    private final TopicSwitchDetector topicSwitchDetector;
    private final MultiIntentRecognitionService intentRecognitionService;
    private final IntentResolverService intentResolver;

    /**
     * 在 Turn Policy 之前补全 {@link UtterancePlan} 的业务意图（若适用）。
     */
    public void enrichIfNeeded(UtterancePlan plan, DialogueState state, String conversationId,
                               String userText, String intentHint) {
        if (!shouldRun(plan, state, userText, intentHint)) {
            return;
        }
        String llmInput = plan.businessTextForLlm() != null && !plan.businessTextForLlm().isBlank()
                ? plan.businessTextForLlm()
                : userText;
        plan.setLlmTurnPolicyAttempted(true);
        try {
            IntentRecognitionResult raw = intentRecognitionService.chat(conversationId, llmInput);
            IntentRecognitionResult result = raw != null ? raw.normalized() : IntentRecognitionResult.unrelated();
            if (!result.related()) {
                log.info("llm turn policy unrelated conv={}", conversationId);
                return;
            }
            List<String> codes = resolveBusinessIntentCodes(result);
            if (codes.isEmpty()) {
                log.info("llm turn policy no business intents conv={}", conversationId);
                return;
            }
            applyIntentsToPlan(plan, codes, llmInput);
            plan.setLlmTurnPolicyHit(true);
            log.info("llm turn policy hit conv={} intents={} deferred={}",
                    conversationId, plan.businessIntents(), plan.deferredIntents());
        } catch (Exception e) {
            log.warn("llm turn policy failed conv={}", conversationId, e);
        }
    }

    private boolean shouldRun(UtterancePlan plan, DialogueState state, String userText, String intentHint) {
        if (!properties.isLlmTurnPolicyEnabled() || plan == null) {
            return false;
        }
        if (plan.pureChitchat() || !plan.businessIntents().isEmpty()) {
            return false;
        }
        if (userText == null || userText.isBlank()) {
            return false;
        }
        if (intentHint != null && !intentHint.isBlank() && !intentHint.equals(userText)) {
            return false;
        }
        if (state != null && DialogueTasks.isActive(state.getActiveTask())
                && !topicSwitchDetector.isTopicSwitch(state, plan)) {
            return false;
        }
        return true;
    }

    private List<String> resolveBusinessIntentCodes(IntentRecognitionResult result) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (IntentRecognitionResult.IntentItem item : result.intents()) {
            if (item == null || item.intent() == null || item.intent().isBlank()) {
                continue;
            }
            if (CHITCHAT_LABEL.equals(item.intent().trim())) {
                continue;
            }
            String code = intentResolver.resolve(item.intent());
            if (code == null || code.isBlank() || CHITCHAT_LABEL.equals(code)) {
                continue;
            }
            codes.add(code);
        }
        return IntentPriority.sortByPriority(new ArrayList<>(codes));
    }

    private static void applyIntentsToPlan(UtterancePlan plan, List<String> sorted, String llmInput) {
        List<String> thisTurn = sorted.size() <= MAX_INTENTS_THIS_TURN
                ? sorted
                : sorted.subList(0, MAX_INTENTS_THIS_TURN);
        List<String> deferred = sorted.size() <= MAX_INTENTS_THIS_TURN
                ? List.of()
                : sorted.subList(MAX_INTENTS_THIS_TURN, sorted.size());
        plan.setBusinessIntents(thisTurn);
        plan.setDeferredIntents(deferred);
        plan.setPrimaryIntent(IntentPriority.primaryOf(thisTurn));
        if (plan.businessTextForLlm() == null || plan.businessTextForLlm().isBlank()) {
            plan.setBusinessTextForLlm(llmInput);
        }
    }
}
