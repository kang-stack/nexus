package cn.cloudcat.user.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring MVC 异步请求线程池 — 供 SSE / {@code Flux} 流式接口使用。
 * <p>
 * 未配置时 Spring 使用 {@code SimpleAsyncTaskExecutor}（每请求新建线程，不适合生产）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    /** SSE 长连接默认超时：30 分钟（毫秒） */
    private static final long ASYNC_TIMEOUT_MS = 30L * 60 * 1000;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(ASYNC_TIMEOUT_MS);
        log.info("WebMvc async executor initialized (core=8, max=32, queue=200, timeout={}ms)",
                ASYNC_TIMEOUT_MS);
    }
}
