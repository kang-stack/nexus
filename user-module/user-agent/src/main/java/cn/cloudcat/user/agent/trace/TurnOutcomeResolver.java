package cn.cloudcat.user.agent.trace;

import cn.cloudcat.user.agent.skill.SkillContext;

/**
 * 根据 SkillContext 推断本轮最终路径标签。
 *
 * @author Kang
 * @since 2026-06-02
 */
public final class TurnOutcomeResolver {

    private TurnOutcomeResolver() {
    }

    public static String resolve(SkillContext ctx) {
        if (ctx == null) {
            return "UNKNOWN";
        }
        if (ctx.needRag()) {
            return "RAG";
        }
        if (ctx.chitchat()) {
            return "CHITCHAT";
        }
        if (ctx.handled() && !ctx.replySegments().isEmpty()) {
            return "BRANCH";
        }
        if (ctx.handled()) {
            return "HANDLED";
        }
        return "UNKNOWN";
    }
}
