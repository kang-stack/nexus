package cn.cloudcat.user.message.utterance;

import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.agent.utterance.UtteranceSegment;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.knowledge.chitchat.ChitchatInterceptService;
import cn.cloudcat.user.knowledge.engine.FaqAliasMatchService;
import cn.cloudcat.user.knowledge.service.SessionTemplateService;
import cn.cloudcat.user.knowledge.utterance.UtteranceConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 话语理解：分句、寒暄剥离、多意图扫描与优先级排序。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UtteranceUnderstandingService {

    private static final int MAX_INTENTS_THIS_TURN = 2;

    private final ChitchatInterceptService chitchatInterceptService;
    private final FaqAliasMatchService faqAliasMatchService;
    private final SessionTemplateService sessionTemplateService;
    private final UtteranceConnectorService utteranceConnectorService;

    public UtterancePlan analyze(String userText, DialogueState dialogueState) {
        UtterancePlan plan = new UtterancePlan();
        if (userText == null || userText.isBlank()) {
            plan.setPureChitchat(true);
            return plan;
        }

        List<String> clauses = utteranceConnectorService.splitClauses(userText);
        boolean anyChitchat = false;
        boolean anyBusiness = false;
        StringBuilder businessText = new StringBuilder();
        LinkedHashSet<String> intentSet = new LinkedHashSet<>();

        for (String clause : clauses) {
            if (chitchatInterceptService.isChitchatPhrase(clause)) {
                plan.addSegment(new UtteranceSegment(UtteranceSegment.SegmentType.CHITCHAT, clause, null));
                anyChitchat = true;
            } else {
                List<String> clauseIntents = faqAliasMatchService.matchAllIntents(clause);
                String intent = clauseIntents.isEmpty() ? null : clauseIntents.get(0);
                plan.addSegment(new UtteranceSegment(UtteranceSegment.SegmentType.BUSINESS, clause, intent));
                anyBusiness = true;
                if (!businessText.isEmpty()) {
                    businessText.append('，');
                }
                businessText.append(clause);
                intentSet.addAll(clauseIntents);
            }
        }

        intentSet.addAll(faqAliasMatchService.matchAllIntents(userText));
        mergePendingIntents(intentSet, dialogueState);

        List<String> sorted = IntentPriority.sortByPriority(new ArrayList<>(intentSet));
        if (sorted.isEmpty() && anyBusiness) {
            anyBusiness = false;
        }

        if (!sorted.isEmpty()) {
            List<String> thisTurn = sorted.size() <= MAX_INTENTS_THIS_TURN
                    ? sorted
                    : sorted.subList(0, MAX_INTENTS_THIS_TURN);
            List<String> deferred = sorted.size() <= MAX_INTENTS_THIS_TURN
                    ? List.of()
                    : sorted.subList(MAX_INTENTS_THIS_TURN, sorted.size());
            plan.setBusinessIntents(thisTurn);
            plan.setDeferredIntents(deferred);
            plan.setPrimaryIntent(IntentPriority.primaryOf(thisTurn));
            plan.setBusinessTextForLlm(!businessText.isEmpty()
                    ? businessText.toString()
                    : userText);
        }

        if (!anyBusiness && anyChitchat) {
            plan.setPureChitchat(true);
        } else if (anyChitchat && anyBusiness) {
            plan.setMixedChitchat(true);
            plan.setPrefixChitchatAck(sessionTemplateService.getTemplate(
                    "GREETING_ACK", "好的，收到啦～"));
        }

        log.info("utterance plan pureChitchat={} mixed={} intents={} deferred={} businessText=\"{}\"",
                plan.pureChitchat(), plan.mixedChitchat(), plan.businessIntents(),
                plan.deferredIntents(), abbreviate(plan.businessTextForLlm()));
        return plan;
    }

    private static void mergePendingIntents(LinkedHashSet<String> intentSet, DialogueState dialogueState) {
        if (dialogueState == null || dialogueState.getPendingIntentQueue() == null) {
            return;
        }
        intentSet.addAll(dialogueState.getPendingIntentQueue());
        dialogueState.getPendingIntentQueue().clear();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }
}
