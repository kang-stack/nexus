package cn.cloudcat.common.integration.elasticsearch;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch 配置属性
 * <p>
 * 映射 application.yml 中 elasticsearch.* 配置项：
 * 连接地址、认证信息、Embedding 模型和 SSL 校验开关。
 *
 * @author Kang
 * @since 2026-06-02
 */
@ConfigurationProperties(prefix = ElasticSearchProperties.PREFIX)
public class ElasticSearchProperties {
    public static final String PREFIX = "elasticsearch";

    /**
     * 是否连接 Elasticsearch。为 false 时不创建 RestClient / EmbeddingStore，应用可正常启动。
     */
    private boolean enabled = true;

    private String host;

    private String username;

    private String password;

    private String baseUrl;

    private String modelName;

    private String apiKey;

    private int dimensions;

    /**
     * 是否启用 SSL 证书校验，开发环境默认关闭（ES 使用自签名证书）
     */
    private boolean sslVerificationEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public boolean isSslVerificationEnabled() {
        return sslVerificationEnabled;
    }

    public void setSslVerificationEnabled(boolean sslVerificationEnabled) {
        this.sslVerificationEnabled = sslVerificationEnabled;
    }
}
