package cn.cloudcat.user.session.model;

/**
 * 会话状态机驱动事件枚举。
 * <p>
 * 触发状态迁移的原子事件：用户消息、AI 回复起止、关闭脚本、
 * 满意度提示/提交、超时关闭、用户主动关闭。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum SessionEvent {
    /** 用户发送消息 */
    USER_MESSAGE,
    /** AI 开始生成回复 */
    AI_REPLY_START,
    /** AI 回复生成完成 */
    AI_REPLY_DONE,
    /** 下发结束语/关闭脚本 */
    CLOSING_SCRIPT,
    /** 弹出满意度评价提示 */
    SATISFACTION_PROMPT,
    /** 用户提交满意度评价 */
    SATISFACTION_SUBMITTED,
    /** 超时自动关闭 */
    TIMEOUT_CLOSE,
    /** 用户主动关闭会话 */
    USER_CLOSE
}
