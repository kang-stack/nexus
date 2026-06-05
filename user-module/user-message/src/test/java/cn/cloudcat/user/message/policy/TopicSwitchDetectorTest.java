package cn.cloudcat.user.message.policy;

import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.DialogueTasks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicSwitchDetectorTest {

    private final TopicSwitchDetector detector = new TopicSwitchDetector();

    @Test
    void detectsSwitchWhenNewIntentDiffersFromTask() {
        DialogueState state = DialogueState.empty();
        state.setActiveTask(DialogueTasks.NETWORK_UNKNOWN_PROBE);

        UtterancePlan plan = new UtterancePlan();
        plan.setBusinessIntents(List.of("充值问题"));

        assertTrue(detector.isTopicSwitch(state, plan));
    }

    @Test
    void noSwitchWhenSameIntentFamily() {
        DialogueState state = DialogueState.empty();
        state.setActiveTask(DialogueTasks.NETWORK_UNKNOWN_PROBE);

        UtterancePlan plan = new UtterancePlan();
        plan.setBusinessIntents(List.of("网络问题"));

        assertFalse(detector.isTopicSwitch(state, plan));
    }
}
