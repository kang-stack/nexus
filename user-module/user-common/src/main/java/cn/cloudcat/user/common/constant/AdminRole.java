package cn.cloudcat.user.common.constant;

/**
 * 管理员角色层级
 * <p>
 * SUPER_ADMIN > ADMIN > OPERATOR
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum AdminRole {

    /** 超级管理员：可管理后台账号、系统配置 */
    SUPER_ADMIN,

    /** 普通管理员：可管理文档、设备，不可管理账号 */
    ADMIN,

    /** 运营人员：只读访问，不可修改 */
    OPERATOR;

    /**
     * role 是否不低于指定的最低角色
     */
    public boolean isAtLeast(AdminRole min) {
        return this.ordinal() <= min.ordinal();
    }
}
