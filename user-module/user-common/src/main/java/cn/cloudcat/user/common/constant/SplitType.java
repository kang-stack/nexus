package cn.cloudcat.user.common.constant;

/**
 * 文档切分方式枚举。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum SplitType {

    /**
     * 按长度切分
     */
    LENGTH,

    /**
     * 按标题切分
     */
    TITLE,

    /**
     * 按正则切分
     */
    REGEX,

    /**
     * 智能切分
     */
    SMART,

    /**
     * 按分隔符切分
     */
    SEPARATOR,

    /**
     * 按兄弟标题层级切分
     */
    BROTHER;
}
