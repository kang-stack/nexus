package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.knowledge.service.SessionTemplateService;
import cn.cloudcat.user.message.conversation.EndingDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结束语 Skill — Phase 4 后处理（order=40）。
 * <p>
 * 仅在 solutionGiven=1（已给方案）且用户表达结束意图（"谢谢""好的"等）
 * 时才触发，返回 CLOSING 模板并触发满意度评价。
 * <p>
 * supports() 双重守卫：solutionGiven==1 + endingDetector.isUserEnding()
 * 确保不会在新会话/新问题中误触发。
 * <p>
 * Skill 链阶段：[Phase1 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class ClosingScriptChatSkill implements ChatSkill {

    private final EndingDetector endingDetector;
    private final SessionTemplateService sessionTemplateService;

    @Override
    public int order() { return 40; }

    @Override
    public String name() { return "ClosingScript"; }

    /** 仅在已给出解决方案且用户表达结束意图时触发 */
    @Override
    public boolean supports(SkillContext ctx) {
        if (ctx.conversation() == null) {
            return false;
        }
        Integer solutionGiven = ctx.conversation().getSolutionGiven();
        boolean gaveSolution = solutionGiven != null && solutionGiven == 1;
        return gaveSolution && endingDetector.isUserEnding(ctx.userText());
    }

    /** 返回结束语模板并标记满意度评价追加 */
    @Override
    public void execute(SkillContext ctx) {
        String closing = sessionTemplateService.getTemplate("CLOSING", "好的亲，后续有问题随时找我~");
        ReplySegment seg = new ReplySegment("TEXT", closing, 1, 2);
        seg.setEnding(true);
        ctx.addSegments(List.of(seg));
        ctx.markClosingTriggered();
        ctx.markAppendSatisfaction();
        ctx.markHandled();
    }
}
