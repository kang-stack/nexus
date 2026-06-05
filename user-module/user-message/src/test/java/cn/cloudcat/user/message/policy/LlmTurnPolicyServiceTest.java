package cn.cloudcat.user.message.policy;

import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.knowledge.engine.IntentResolverService;
import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import cn.cloudcat.user.message.ai.service.MultiIntentRecognitionService;
import cn.cloudcat.user.message.config.DialoguePolicyProperties;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.DialogueTasks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmTurnPolicyServiceTest {

    @Mock
    private MultiIntentRecognitionService intentRecognitionService;
    @Mock
    private IntentResolverService intentResolver;

    private final DialoguePolicyProperties properties = new DialoguePolicyProperties();
    private final TopicSwitchDetector topicSwitchDetector = new TopicSwitchDetector();
    private LlmTurnPolicyService service;

    @BeforeEach
    void setUp() {
        properties.setLlmTurnPolicyEnabled(true);
        service = new LlmTurnPolicyService(properties, topicSwitchDetector,
                intentRecognitionService, intentResolver);
    }

    @Test
    void enrichesPlanWhenRulesMissed() {
        UtterancePlan plan = new UtterancePlan();
        plan.setBusinessTextForLlm("上网好慢怎么办");

        IntentRecognitionResult llmResult = new IntentRecognitionResult(
                "网络慢",
                true,
                "网络问题",
                List.of(new IntentRecognitionResult.IntentItem("网络问题", "上网好慢")),
                null);
        when(intentRecognitionService.chat(eq("c1"), anyString())).thenReturn(llmResult);
        when(intentResolver.resolve("网络问题")).thenReturn("网络问题");

        service.enrichIfNeeded(plan, DialogueState.empty(), "c1", "上网好慢怎么办", null);

        assertTrue(plan.llmTurnPolicyAttempted());
        assertTrue(plan.llmTurnPolicyHit());
        assertEquals("网络问题", plan.primaryIntent());
        assertEquals(List.of("网络问题"), plan.businessIntents());
    }

    @Test
    void skipsWhenPureChitchat() {
        UtterancePlan plan = new UtterancePlan();
        plan.setPureChitchat(true);

        service.enrichIfNeeded(plan, DialogueState.empty(), "c1", "你好", null);

        assertFalse(plan.llmTurnPolicyAttempted());
        verify(intentRecognitionService, never()).chat(anyString(), anyString());
    }

    @Test
    void skipsWhenContinuingActiveTask() {
        UtterancePlan plan = new UtterancePlan();
        DialogueState state = DialogueState.empty();
        state.setActiveTask(DialogueTasks.NETWORK_UNKNOWN_PROBE);

        service.enrichIfNeeded(plan, state, "c1", "还是慢", null);

        assertFalse(plan.llmTurnPolicyAttempted());
        verify(intentRecognitionService, never()).chat(anyString(), anyString());
    }

    @Test
    void capsCompoundIntentsAtTwo() {
        UtterancePlan plan = new UtterancePlan();
        IntentRecognitionResult llmResult = new IntentRecognitionResult(
                "复合",
                true,
                "网络问题",
                List.of(
                        new IntentRecognitionResult.IntentItem("充值问题", "怎么充值"),
                        new IntentRecognitionResult.IntentItem("网络问题", "网速慢"),
                        new IntentRecognitionResult.IntentItem("退款问题", "退款")),
                null);
        when(intentRecognitionService.chat(eq("c1"), anyString())).thenReturn(llmResult);
        when(intentResolver.resolve(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service.enrichIfNeeded(plan, DialogueState.empty(), "c1", "网速慢，怎么充值，还要退款", null);

        assertEquals(2, plan.businessIntents().size());
        assertEquals(1, plan.deferredIntents().size());
        assertEquals("网络问题", plan.primaryIntent());
        assertEquals(List.of("网络问题", "退款问题"), plan.businessIntents());
        assertEquals(List.of("充值问题"), plan.deferredIntents());
    }
}
