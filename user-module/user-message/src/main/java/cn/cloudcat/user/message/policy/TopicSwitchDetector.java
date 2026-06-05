package cn.cloudcat.user.message.policy;

import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.DialogueTasks;
import org.springframework.stereotype.Component;

/**
 * 判断用户是否从当前 activeTask 切换话题。
 */
@Component
public class TopicSwitchDetector {

    public boolean isTopicSwitch(DialogueState state, UtterancePlan plan) {
        if (state == null || !DialogueTasks.isActive(state.getActiveTask())) {
            return false;
        }
        if (plan == null || plan.pureChitchat()) {
            return false;
        }
        if (plan.businessIntents().isEmpty()) {
            return false;
        }
        String taskIntent = DialogueTasks.intentForTask(state.getActiveTask());
        if (taskIntent == null) {
            return false;
        }
        return !plan.businessIntents().contains(taskIntent);
    }
}
