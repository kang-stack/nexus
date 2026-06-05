package cn.cloudcat.user.session.support;

/**
 * 与 {@link cn.cloudcat.user.session.service.UserConversationServiceImpl} 一致的 userId 数值化规则。
 */
public final class UserIdParser {

    private UserIdParser() {
    }

    /** 将 JWT 业务 userId 字符串解析为 acc_user_conversation / trace 表中的 BIGINT */
    public static Long toStoredLong(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return (long) userId.hashCode();
        }
    }
}
