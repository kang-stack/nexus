package cn.cloudcat.user.message.policy;

import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.DialogueTasks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话任务生命周期：启动、续办、结束。
 */
@Service
@Slf4j
public class DialogueTaskLifecycleService {

    private static final int MAX_PROBE_STEPS = 3;

    public void onContinueTask(DialogueState state) {
        state.setStep(state.getStep() + 1);
        log.info("dialogue task continue task={} step={}", state.getActiveTask(), state.getStep());
    }

    public void onTopicSwitch(DialogueState state) {
        log.info("dialogue task cleared (topic switch) was={}", state.getActiveTask());
        state.setActiveTask(DialogueState.TASK_NONE);
        state.setStep(0);
        state.getSlots().clear();
    }

    /** 本轮回复完成后，根据诊断/意图决定是否进入追问任务 */
    public void onTurnCompleted(DialogueState state, SkillContext ctx) {
        if (state == null || ctx == null) {
            return;
        }
        if (ctx.continueActiveTask()) {
            if (state.getStep() >= MAX_PROBE_STEPS) {
                clearTask(state);
            }
            return;
        }
        if (!ctx.handled() || ctx.chitchat()) {
            return;
        }
        String intent = ctx.resolvedIntent();
        String rootCause = ctx.primaryRootCause();
        if (intent == null) {
            return;
        }
        if ("网络问题".equals(intent) && "UNKNOWN".equals(rootCause)) {
            state.setActiveTask(DialogueTasks.NETWORK_UNKNOWN_PROBE);
            state.setStep(0);
            log.info("dialogue task started NETWORK_UNKNOWN_PROBE");
        }
    }

    public void clearTask(DialogueState state) {
        state.setActiveTask(DialogueState.TASK_NONE);
        state.setStep(0);
        state.getSlots().clear();
    }
}
