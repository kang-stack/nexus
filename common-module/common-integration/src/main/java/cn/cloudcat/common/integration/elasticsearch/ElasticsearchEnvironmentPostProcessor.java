package cn.cloudcat.common.integration.elasticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * {@code elasticsearch.enabled=false} 时关闭 Spring Boot ES 自动配置与 Actuator 健康检查，
 * 避免无 ES 环境下 JMX/health 端点反复连接报错。
 */
public class ElasticsearchEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE = "elasticsearchIntegrationOverrides";

    private static final String[] AUTO_CONFIG_EXCLUDES = {
            "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration",
            "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration",
            "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRepositoriesAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.elasticsearch.ElasticsearchRestClientHealthContributorAutoConfiguration"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty("elasticsearch.enabled", Boolean.class, true)) {
            return;
        }

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("management.health.elasticsearch.enabled", false);

        String existing = environment.getProperty("spring.autoconfigure.exclude", "");
        StringJoiner joiner = new StringJoiner(",");
        if (existing != null && !existing.isBlank()) {
            joiner.add(existing.trim());
        }
        for (String exclude : AUTO_CONFIG_EXCLUDES) {
            joiner.add(exclude);
        }
        overrides.put("spring.autoconfigure.exclude", joiner.toString());

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
