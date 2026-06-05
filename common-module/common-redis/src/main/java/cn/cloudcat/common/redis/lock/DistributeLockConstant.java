package cn.cloudcat.common.redis.lock;

/**
 * 分布式锁常量
 *
 * @author Kang
 * @since 2026-06-02
 */
public class DistributeLockConstant {

    public static final String NONE_KEY = "NONE";

    public static final String DEFAULT_OWNER = "DEFAULT";

    public static final int DEFAULT_EXPIRE_TIME = -1;

    public static final int DEFAULT_WAIT_TIME = Integer.MAX_VALUE;
}
