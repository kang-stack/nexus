package cn.cloudcat.user.message.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 多模型路由服务。
 * <p>
 * 支持 DeepSeek → Qwen → OpenAI → Ollama 多级降级；
 * 启动时按 priority 构建模型链，异常/超时时自动切换下一个。
 * 支持运行时热切换主模型（通过 refreshModels 接口）。
 */
@Slf4j
@Service
public class ModelRoutingService {

    private final ModelRoutingProperties properties;
    private final List<ProviderEntry> providers = new CopyOnWriteArrayList<>();
    private volatile ProviderEntry currentProvider;
    private volatile ChatModel currentChatModel;
    private volatile StreamingChatModel currentStreamingChatModel;

    public ModelRoutingService(ModelRoutingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        rebuildProviders();
    }

    public ChatModel getChatModel() {
        return currentChatModel;
    }

    public StreamingChatModel getStreamingChatModel() {
        return currentStreamingChatModel;
    }

    public String getCurrentProviderName() {
        return currentProvider != null ? currentProvider.getName() : "unknown";
    }

    /**
     * 获取降级链路（所有可用 provider 名称，逗号分隔）。
     */
    public String getFallbackChain() {
        return String.join(",", providers.stream().map(ProviderEntry::getName).toList());
    }

    /**
     * 热切换主模型。
     */
    public synchronized void refreshModels() {
        rebuildProviders();
        log.info("模型配置已刷新，当前主模型: {}", getCurrentProviderName());
    }

    private synchronized void rebuildProviders() {
        providers.clear();
        List<ModelProviderConfig> sorted = properties.getProviders().stream()
                .sorted(Comparator.comparingInt(ModelProviderConfig::getPriority))
                .toList();

        for (ModelProviderConfig config : sorted) {
            try {
                ChatModel chatModel = buildChatModel(config);
                StreamingChatModel streamingModel = buildStreamingChatModel(config);
                ProviderEntry entry = new ProviderEntry(config.getName(), config.getPriority(), chatModel, streamingModel);
                providers.add(entry);
                log.info("模型提供商已注册: name={}, priority={}, model={}", config.getName(), config.getPriority(), config.getModelName());
            } catch (Exception e) {
                log.warn("模型提供商注册失败: name={}, error={}", config.getName(), e.getMessage());
            }
        }

        if (!providers.isEmpty()) {
            currentProvider = providers.getFirst();
            currentChatModel = currentProvider.getChatModel();
            currentStreamingChatModel = currentProvider.getStreamingChatModel();
        }
    }

    private ChatModel buildChatModel(ModelProviderConfig config) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1);

        if (config.getCustomParameters() != null && !config.getCustomParameters().isEmpty()) {
            builder.customParameters(config.getCustomParameters());
        }

        return builder.build();
    }

    private StreamingChatModel buildStreamingChatModel(ModelProviderConfig config) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(60));

        if (config.getCustomParameters() != null && !config.getCustomParameters().isEmpty()) {
            builder.customParameters(config.getCustomParameters());
        }

        return builder.build();
    }

    @Data
    public static class ProviderEntry {
        private final String name;
        private final int priority;
        private final ChatModel chatModel;
        private final StreamingChatModel streamingChatModel;
    }

    @Data
    @ConfigurationProperties(prefix = "icc.model")
    @Service
    public static class ModelRoutingProperties {
        private String primaryProvider = "deepseek";
        private boolean fallbackEnabled = true;
        private List<ModelProviderConfig> providers = new ArrayList<>();
    }

    @Data
    public static class ModelProviderConfig {
        private String name;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private int priority;
        private Map<String, Object> customParameters;
    }
}
