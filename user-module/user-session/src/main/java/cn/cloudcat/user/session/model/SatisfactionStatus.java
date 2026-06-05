package cn.cloudcat.user.session.model;

/**
 * 满意度评价生命周期状态。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum SatisfactionStatus {
    /** 未发起评价流程 */
    NONE,
    /** 已弹出评价提示，等待用户提交 */
    PENDING,
    /** 用户已提交评价 */
    DONE
}
