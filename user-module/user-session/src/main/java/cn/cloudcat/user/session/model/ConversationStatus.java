package cn.cloudcat.user.session.model;

/**
 * V2 会话六态状态机。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum ConversationStatus {
    ACTIVE,
    AI_REPLYING,
    AWAITING_USER,
    CLOSING,
    AWAITING_SATISFACTION,
    CLOSED
}
