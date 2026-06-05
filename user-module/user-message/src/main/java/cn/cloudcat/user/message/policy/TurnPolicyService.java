package cn.cloudcat.user.message.policy;

import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.DialogueTasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 回合策略：话语理解、任务续办、意图预填、状态写回。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TurnPolicyService {

    private final TopicSwitchDetector topicSwitchDetector;
    private final DialogueTaskLifecycleService taskLifecycle;

    public void apply(SkillContext ctx, UtterancePlan plan, DialogueState state) {
        if (plan == null || state == null) {
            return;
        }
        ctx.setUtterancePlan(plan);

        if (topicSwitchDetector.isTopicSwitch(state, plan)) {
            taskLifecycle.onTopicSwitch(state);
        }

        boolean continuingTask = DialogueTasks.isActive(state.getActiveTask())
                && !topicSwitchDetector.isTopicSwitch(state, plan);

        if (continuingTask && (plan.pureChitchat() || plan.businessIntents().isEmpty())) {
            applyContinueTask(ctx, state);
            return;
        }

        if (plan.pureChitchat()) {
            log.debug("turn policy CHITCHAT_ONLY conv={}", ctx.conversationId());
            return;
        }

        if (continuingTask) {
            applyContinueTask(ctx, state);
            return;
        }

        applyBusinessIntents(ctx, plan, state);
    }

    private void applyContinueTask(SkillContext ctx, DialogueState state) {
        String taskIntent = DialogueTasks.intentForTask(state.getActiveTask());
        if (taskIntent == null) {
            return;
        }
        taskLifecycle.onContinueTask(state);
        ctx.setContinueActiveTask(true);
        ctx.setSkipRuleDiagnosis(true);
        ctx.setResolvedIntent(taskIntent);
        if (state.getLastRootCause() != null && !state.getLastRootCause().isBlank()) {
            ctx.applyForcedRootCause(state.getLastRootCause());
        }
        log.info("turn policy CONTINUE_TASK conv={} task={} step={} intent={} rootCause={}",
                ctx.conversationId(), state.getActiveTask(), state.getStep(),
                taskIntent, ctx.primaryRootCause());
    }

    private void applyBusinessIntents(SkillContext ctx, UtterancePlan plan, DialogueState state) {
        if (plan.businessIntents().isEmpty()) {
            return;
        }
        ctx.setResolvedIntent(plan.primaryIntent());
        if (plan.compoundBusiness()) {
            ctx.setCompoundIntents(plan.businessIntents());
        }
        state.setLastPrimaryIntent(plan.primaryIntent());

        if (!plan.deferredIntents().isEmpty()) {
            ctx.setPendingIntentNotice(buildPendingNotice(plan.deferredIntents()));
        }

        log.info("turn policy BUSINESS conv={} primary={} compound={} deferred={}",
                ctx.conversationId(), plan.primaryIntent(), plan.businessIntents(),
                plan.deferredIntents());
    }

    private static String buildPendingNotice(java.util.List<String> deferred) {
        if (deferred.isEmpty()) {
            return null;
        }
        String names = String.join("、", deferred);
        return "您提到的「" + names + "」我会在本轮主问题处理完后继续为您解答。";
    }

    public void afterTurn(DialogueState state, SkillContext ctx) {
        if (state == null) {
            return;
        }
        UtterancePlan plan = ctx.utterancePlan();
        if (plan != null && !plan.deferredIntents().isEmpty()) {
            state.getPendingIntentQueue().clear();
            state.getPendingIntentQueue().addAll(plan.deferredIntents());
        }
        if (ctx.primaryRootCause() != null) {
            state.setLastRootCause(ctx.primaryRootCause());
        }
        if (ctx.resolvedIntent() != null) {
            state.setLastPrimaryIntent(ctx.resolvedIntent());
        }
        taskLifecycle.onTurnCompleted(state, ctx);
    }
}
