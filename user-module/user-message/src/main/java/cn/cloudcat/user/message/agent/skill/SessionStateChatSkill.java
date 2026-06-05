package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.SessionStateMachine;
import cn.cloudcat.user.session.service.UserConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话状态管理 Skill — Phase 5 终态（order=90）。
 * <p>
 * 始终执行的最终步骤，负责根据 Skill 执行情况驱动六态会话状态机：
 * <ul>
 *   <li>closingTriggered → CLOSING_SCRIPT → SATISFACTION_PROMPT</li>
 *   <li>其他情况 → AI_REPLY_START → AI_REPLY_DONE → AWAITING_USER（等待下一条用户消息）</li>
 * </ul>
 * <p>
 * Skill 链阶段：[Phase1 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49] → [Phase5 终态 90]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class SessionStateChatSkill implements ChatSkill {

    private final SessionStateMachine stateMachine;
    private final UserConversationService conversationService;

    @Override
    public int order() { return 90; }

    @Override
    public String name() { return "SessionState"; }

    /** 有会话时始终执行 */
    @Override
    public boolean supports(SkillContext ctx) {
        return ctx.conversation() != null;
    }

    /** 根据技能链执行结果驱动会话状态机转换 */
    @Override
    public void execute(SkillContext ctx) {
        var conv = ctx.conversation();
        // 结束语触发：关闭脚本 → 满意度邀请
        if (ctx.closingTriggered()) {
            var status = stateMachine.apply(conv, SessionEvent.CLOSING_SCRIPT);
            conversationService.applyStatus(conv, status.name());
            var satStatus = stateMachine.apply(conv, SessionEvent.SATISFACTION_PROMPT);
            conversationService.applyStatus(conv, satStatus.name());
            return;
        }
        // 非闲聊处理完成：AI 回复中 → 回复完成 → 等待用户
        if (ctx.handled() && !ctx.chitchat()) {
            conversationService.applyStatus(conv, ConversationStatus.AI_REPLYING.name());
            conversationService.applyStatus(conv,
                    stateMachine.apply(conv, SessionEvent.AI_REPLY_DONE).name());
            conversationService.applyStatus(conv, ConversationStatus.AWAITING_USER.name());
        }
    }
}
