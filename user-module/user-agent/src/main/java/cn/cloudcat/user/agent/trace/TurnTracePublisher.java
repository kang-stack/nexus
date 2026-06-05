package cn.cloudcat.user.agent.trace;

import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.session.service.UserChatTurnTraceService;

/**
 * 将 {@link SkillContext} 中的轨迹异步落库。
 *
 * @author Kang
 * @since 2026-06-02
 */
public final class TurnTracePublisher {

    private TurnTracePublisher() {
    }

    /** 汇总 Skill 链结果并异步写入 acc_user_chat_turn_trace */
    public static void publish(UserChatTurnTraceService traceService, SkillContext ctx,
                               String outcome, long totalCostMs) {
        if (traceService == null || ctx == null || ctx.turnId() == null || ctx.turnId().isBlank()) {
            return;
        }
        ctx.trace().finalizeSummary(ctx, outcome, totalCostMs);
        traceService.saveAsync(
                ctx.turnId(),
                ctx.conversationId(),
                ctx.userMessageId(),
                ctx.userId(),
                ctx.userText(),
                outcome,
                totalCostMs,
                ctx.resolvedIntent(),
                ctx.primaryRootCause(),
                ctx.replySegments().size(),
                ctx.trace().toTraceJson()
        );
    }
}
