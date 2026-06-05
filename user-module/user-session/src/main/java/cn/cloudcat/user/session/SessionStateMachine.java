package cn.cloudcat.user.session;

import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.entity.UserConversation;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 六态会话状态机（V2 §6.1）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
public class SessionStateMachine {

    /** 状态迁移表：from → (event → to) */
    private static final Map<ConversationStatus, Map<SessionEvent, ConversationStatus>> TRANSITIONS =
            new EnumMap<>(ConversationStatus.class);

    static {
        // ACTIVE 态下开始 AI 回复 → AI_REPLYING
        put(ConversationStatus.ACTIVE, SessionEvent.AI_REPLY_START, ConversationStatus.AI_REPLYING);
        // AI 回复完成 → 等待用户
        put(ConversationStatus.AI_REPLYING, SessionEvent.AI_REPLY_DONE, ConversationStatus.AWAITING_USER);
        // 等待用户时收到用户消息 → 返回 ACTIVE
        put(ConversationStatus.AWAITING_USER, SessionEvent.USER_MESSAGE, ConversationStatus.ACTIVE);
        // 等待用户时下发关闭脚本 → CLOSING
        put(ConversationStatus.AWAITING_USER, SessionEvent.CLOSING_SCRIPT, ConversationStatus.CLOSING);
        // 等待用户时超时 → CLOSING
        put(ConversationStatus.AWAITING_USER, SessionEvent.TIMEOUT_CLOSE, ConversationStatus.CLOSING);
        // CLOSING 后弹出满意度 → AWAITING_SATISFACTION
        put(ConversationStatus.CLOSING, SessionEvent.SATISFACTION_PROMPT, ConversationStatus.AWAITING_SATISFACTION);
        // 满意度提交 → CLOSED
        put(ConversationStatus.AWAITING_SATISFACTION, SessionEvent.SATISFACTION_SUBMITTED, ConversationStatus.CLOSED);
        // 满意度阶段超时 → CLOSED
        put(ConversationStatus.AWAITING_SATISFACTION, SessionEvent.TIMEOUT_CLOSE, ConversationStatus.CLOSED);
        // 满意度阶段用户主动关闭 → CLOSED
        put(ConversationStatus.AWAITING_SATISFACTION, SessionEvent.USER_CLOSE, ConversationStatus.CLOSED);
        // 满意度阶段收到用户消息 → 重新激活
        put(ConversationStatus.AWAITING_SATISFACTION, SessionEvent.USER_MESSAGE, ConversationStatus.ACTIVE);
        // 已关闭会话收到用户消息 → 重新激活
        put(ConversationStatus.CLOSED, SessionEvent.USER_MESSAGE, ConversationStatus.ACTIVE);
    }

    /** 注册一条状态迁移规则 */
    private static void put(ConversationStatus from, SessionEvent event, ConversationStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(SessionEvent.class)).put(event, to);
    }

    /** 读取会话当前状态，空/null 视为 ACTIVE */
    public ConversationStatus current(UserConversation conv) {
        if (conv == null || conv.getStatus() == null) {
            return ConversationStatus.ACTIVE;
        }
        try {
            return ConversationStatus.valueOf(conv.getStatus());
        } catch (IllegalArgumentException e) {
            return ConversationStatus.ACTIVE;
        }
    }

    /** 根据事件计算目标状态；无效迁移保持当前状态不变 */
    public ConversationStatus apply(UserConversation conv, SessionEvent event) {
        ConversationStatus from = current(conv);
        Map<SessionEvent, ConversationStatus> row = TRANSITIONS.get(from);
        if (row == null || !row.containsKey(event)) {
            return from;
        }
        return row.get(event);
    }
}
