package cn.cloudcat.user.agent.trace;

import org.slf4j.MDC;

/**
 * 对话轮次 MDC 绑定：日志 pattern 中通过 {@code %X{turnId}} {@code %X{convId}} 输出。
 *
 * @author Kang
 * @since 2026-06-02
 */
public final class ChatTurnMdc {

    public static final String TURN_ID = "turnId";
    public static final String CONV_ID = "convId";

    private ChatTurnMdc() {
    }

    /** 绑定当前线程 MDC，供 Skill 链全链路日志 grep */
    public static void bind(String turnId, String conversationId) {
        if (turnId != null && !turnId.isBlank()) {
            MDC.put(TURN_ID, turnId);
        }
        if (conversationId != null && !conversationId.isBlank()) {
            MDC.put(CONV_ID, conversationId);
        }
    }

    /** 清理 MDC，避免线程池复用污染 */
    public static void clear() {
        MDC.remove(TURN_ID);
        MDC.remove(CONV_ID);
    }
}
