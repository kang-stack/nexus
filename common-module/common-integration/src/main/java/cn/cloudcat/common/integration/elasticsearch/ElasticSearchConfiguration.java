package cn.cloudcat.common.integration.elasticsearch;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * Elasticsearch 与 Embedding 配置。
 * <p>
 * {@code elasticsearch.enabled=false} 时不创建 ES 客户端，应用仍可启动；
 * Embedding API（OpenAI 兼容）仍可用，供文档向量化等场景使用。
 */
@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchConfiguration {

    public static final String INDEX_NAME = "icc-vector";

    /** Embedding 模型（OpenAI 兼容 API），不依赖 ES 连接 */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(ElasticSearchProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .modelName(properties.getModelName())
                .dimensions(properties.getDimensions())
                .baseUrl(properties.getBaseUrl())
                .maxSegmentsPerBatch(9)
                .apiKey(properties.getApiKey())
                .build();
    }

    @Configuration
    @ConditionalOnProperty(prefix = ElasticSearchProperties.PREFIX, name = "enabled", havingValue = "true")
    static class ElasticsearchClientConfiguration {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        RestClient restClient(ElasticSearchProperties properties) {
            var builder = RestClient.builder(HttpHost.create(properties.getHost()));

            builder.setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder
                            .setConnectTimeout(5000)
                            .setSocketTimeout(60000)
                            .setConnectionRequestTimeout(5000));

            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                if (StringUtils.hasText(properties.getUsername())) {
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(
                            AuthScope.ANY,
                            new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }

                if (!properties.isSslVerificationEnabled()) {
                    try {
                        SSLContext sslContext = SSLContextBuilder.create()
                                .loadTrustMaterial(null, (chain, authType) -> true)
                                .build();
                        httpClientBuilder.setSSLContext(sslContext);
                        httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                        throw new RuntimeException("Failed to create SSL context for Elasticsearch RestClient", e);
                    }
                }

                return httpClientBuilder;
            });

            return builder.build();
        }

        @Primary
        @ConditionalOnMissingBean
        @Bean
        ElasticsearchEmbeddingStore elasticsearchEmbeddingStore(RestClient restClient) {
            return ElasticsearchEmbeddingStore.builder()
                    .restClient(restClient)
                    .indexName(INDEX_NAME)
                    .build();
        }
    }
}
