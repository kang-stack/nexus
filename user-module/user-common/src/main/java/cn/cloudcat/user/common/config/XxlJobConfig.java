package cn.cloudcat.user.common.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 分布式任务调度配置，仅在 xxl.job.enabled=true 时生效。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
public class XxlJobConfig {

    /** XXL-Job 调度中心地址 */
    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    /** XXL-Job 访问令牌 */
    @Value("${xxl.job.accessToken:default_token}")
    private String accessToken;

    /** 执行器应用名称 */
    @Value("${xxl.job.executor.appname}")
    private String appname;

    /**
     * 创建 XXL-Job 执行器 Bean，注册到调度中心。
     */
    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        log.info("XXL-Job 执行器初始化: adminAddresses={}, appname={}", adminAddresses, appname);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        return executor;
    }
}
