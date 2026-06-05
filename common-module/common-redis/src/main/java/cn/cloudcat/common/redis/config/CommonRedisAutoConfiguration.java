package cn.cloudcat.common.redis.config;

import cn.cloudcat.common.redis.lock.DistributeLockAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({RedisConfig.class, DistributeLockAspect.class})
public class CommonRedisAutoConfiguration {
}