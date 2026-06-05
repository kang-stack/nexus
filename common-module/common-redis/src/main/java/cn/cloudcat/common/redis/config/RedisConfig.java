package cn.cloudcat.common.redis.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 *
 * 同时存在 Redisson（用于分布式锁/发布订阅）和 Lettuce（用于 StringRedisTemplate），
 * 因此显式创建 LettuceConnectionFactory 和 StringRedisTemplate 以避开 Redisson
 * 自带的 RedissonConnectionFactory 代理链路导致的 StackOverflowError。
 *
 * @author Kang
 * @since 2026-06-01
 */
@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties properties) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory();
        factory.setHostName(properties.getHost());
        factory.setPort(properties.getPort());
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            factory.setPassword(properties.getPassword());
        }
        if (properties.getDatabase() > 0) {
            factory.setDatabase(properties.getDatabase());
        }
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        return new StringRedisTemplate(lettuceConnectionFactory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
