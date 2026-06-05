package cn.cloudcat.user.session;

import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.entity.UserConversation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Kang
 * @since 2026-06-02
 */
class SessionStateMachineTest {

    private final SessionStateMachine machine = new SessionStateMachine();

    // ===== existing tests =====

    @Test
    void awaitingUserUserMessageReturnsActive() {
        // 验证AWAITING_USER状态下收到用户消息转回ACTIVE
        UserConversation conv = conv(ConversationStatus.AWAITING_USER);
        assertEquals(ConversationStatus.ACTIVE, machine.apply(conv, SessionEvent.USER_MESSAGE));
    }

    @Test
    void closedUserMessageReopensToActive() {
        // 验证CLOSED状态下收到用户消息重新打开为ACTIVE
        UserConversation conv = conv(ConversationStatus.CLOSED);
        assertEquals(ConversationStatus.ACTIVE, machine.apply(conv, SessionEvent.USER_MESSAGE));
    }

    @Test
    void aiReplyStartFromActive() {
        // 验证ACTIVE状态下AI开始回复进入AI_REPLYING
        UserConversation conv = conv(ConversationStatus.ACTIVE);
        assertEquals(ConversationStatus.AI_REPLYING, machine.apply(conv, SessionEvent.AI_REPLY_START));
    }

    // ===== additional tests =====

    @Test
    void aiReplyDoneFromAiReplyingReturnsAwaitingUser() {
        // 验证AI_REPLYING状态下AI回复完成进入AWAITING_USER
        UserConversation conv = conv(ConversationStatus.AI_REPLYING);
        assertEquals(ConversationStatus.AWAITING_USER, machine.apply(conv, SessionEvent.AI_REPLY_DONE));
    }

    @Test
    void closingScriptFromAwaitingUserReturnsClosing() {
        // 验证AWAITING_USER状态下触发结束语进入CLOSING
        UserConversation conv = conv(ConversationStatus.AWAITING_USER);
        assertEquals(ConversationStatus.CLOSING, machine.apply(conv, SessionEvent.CLOSING_SCRIPT));
    }

    @Test
    void timeoutCloseFromAwaitingUserReturnsClosing() {
        // 验证AWAITING_USER状态下超时关闭进入CLOSING
        UserConversation conv = conv(ConversationStatus.AWAITING_USER);
        assertEquals(ConversationStatus.CLOSING, machine.apply(conv, SessionEvent.TIMEOUT_CLOSE));
    }

    @Test
    void satisfactionPromptFromClosingReturnsAwaitingSatisfaction() {
        // 验证CLOSING状态下触发满意度提示进入AWAITING_SATISFACTION
        UserConversation conv = conv(ConversationStatus.CLOSING);
        assertEquals(ConversationStatus.AWAITING_SATISFACTION, machine.apply(conv, SessionEvent.SATISFACTION_PROMPT));
    }

    @Test
    void satisfactionSubmittedReturnsClosed() {
        // 验证AWAITING_SATISFACTION状态下用户提交评价进入CLOSED
        UserConversation conv = conv(ConversationStatus.AWAITING_SATISFACTION);
        assertEquals(ConversationStatus.CLOSED, machine.apply(conv, SessionEvent.SATISFACTION_SUBMITTED));
    }

    @Test
    void timeoutCloseFromAwaitingSatisfactionReturnsClosed() {
        // 验证AWAITING_SATISFACTION状态下超时关闭进入CLOSED
        UserConversation conv = conv(ConversationStatus.AWAITING_SATISFACTION);
        assertEquals(ConversationStatus.CLOSED, machine.apply(conv, SessionEvent.TIMEOUT_CLOSE));
    }

    @Test
    void userCloseFromAwaitingSatisfactionReturnsClosed() {
        // 验证AWAITING_SATISFACTION状态下用户关闭页面进入CLOSED
        UserConversation conv = conv(ConversationStatus.AWAITING_SATISFACTION);
        assertEquals(ConversationStatus.CLOSED, machine.apply(conv, SessionEvent.USER_CLOSE));
    }

    @Test
    void userMessageFromAwaitingSatisfactionReopensToActive() {
        // 验证AWAITING_SATISFACTION状态下用户继续发消息重新进入ACTIVE
        UserConversation conv = conv(ConversationStatus.AWAITING_SATISFACTION);
        assertEquals(ConversationStatus.ACTIVE, machine.apply(conv, SessionEvent.USER_MESSAGE));
    }

    @Test
    void unknownTransitionStaysInCurrentState() {
        // 验证未定义的转换保持当前状态不变
        UserConversation conv = conv(ConversationStatus.AI_REPLYING);
        assertEquals(ConversationStatus.AI_REPLYING, machine.apply(conv, SessionEvent.USER_MESSAGE));
    }

    @Test
    void nullStatusDefaultsToActive() {
        // 验证null状态默认视为ACTIVE
        UserConversation conv = new UserConversation();
        conv.setStatus(null);
        assertEquals(ConversationStatus.ACTIVE, machine.current(conv));
    }

    @Test
    void blankStatusDefaultsToActive() {
        // 验证空字符串状态默认视为ACTIVE
        UserConversation conv = new UserConversation();
        conv.setStatus("");
        assertEquals(ConversationStatus.ACTIVE, machine.current(conv));
    }

    private UserConversation conv(ConversationStatus status) {
        UserConversation conv = new UserConversation();
        conv.setStatus(status.name());
        return conv;
    }
}
