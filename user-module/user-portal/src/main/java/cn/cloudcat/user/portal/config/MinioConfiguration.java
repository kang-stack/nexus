package cn.cloudcat.user.portal.config;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * MinIO 客户端配置，惰性初始化以避免启动时连接失败阻塞应用启动。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Configuration
public class MinioConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MinioConfiguration.class);

    /** MinIO 服务端点地址 */
    @Value("${minio.endpoint}")
    private String endpoint;

    /** MinIO 访问密钥 */
    @Value("${minio.access-key}")
    private String accessKey;

    /** MinIO 密钥 */
    @Value("${minio.secret-key}")
    private String secretKey;

    /**
     * 创建 MinIO 客户端 Bean，连接失败时返回 null 以保证应用可启动。
     */
    @Bean
    @Lazy
    public MinioClient minioClient() {
        try {
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        } catch (Exception e) {
            logger.warn("Failed to create MinIO client: {}. MinIO functionality will be unavailable.", e.getMessage());
            return null;
        }
    }
}
