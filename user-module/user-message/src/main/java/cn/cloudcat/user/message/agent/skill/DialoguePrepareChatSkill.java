package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.session.DialogueStateCodec;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.message.policy.LlmTurnPolicyService;
import cn.cloudcat.user.message.policy.TurnPolicyService;
import cn.cloudcat.user.message.utterance.UtteranceUnderstandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对话准备 Skill（order=8）：加载 DialogueState → 话语理解 → Turn Policy 预填上下文。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DialoguePrepareChatSkill implements ChatSkill {

    private final UtteranceUnderstandingService utteranceUnderstandingService;
    private final LlmTurnPolicyService llmTurnPolicyService;
    private final TurnPolicyService turnPolicyService;

    @Override
    public int order() {
        return 8;
    }

    @Override
    public String name() {
        return "DialoguePrepare";
    }

    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled();
    }

    @Override
    public void execute(SkillContext ctx) {
        DialogueState state = ctx.conversation() != null
                ? DialogueStateCodec.read(ctx.conversation())
                : DialogueState.empty();
        ctx.setDialogueState(state);
        var plan = utteranceUnderstandingService.analyze(ctx.userText(), state);
        llmTurnPolicyService.enrichIfNeeded(plan, state, ctx.conversationId(),
                ctx.userText(), ctx.intentHint());
        turnPolicyService.apply(ctx, plan, state);
    }
}
