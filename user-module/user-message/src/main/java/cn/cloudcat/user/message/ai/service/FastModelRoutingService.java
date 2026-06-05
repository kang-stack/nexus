package cn.cloudcat.user.message.ai.service;

import cn.cloudcat.user.common.constant.FastModel;
import cn.cloudcat.user.common.constant.FastModelTask;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 快速模型路由服务。
 * <p>
 * 管理轻量/快速模型（如 deepseek-v4-flash、qwen3.6-flash），
 * 用于意图识别、查询改写、标题生成等低延迟任务。
 * 复用主模型配置中的 provider baseUrl/apiKey，仅替换为更快的模型名。
 * <p>
 * 支持两种获取方式：
 * <ul>
 *   <li>按任务类型 — {@code getChatModel(FastModelTask.INTENT_RECOGNITION)}</li>
 *   <li>按模型名 — {@code getChatModel(FastModel.DEEPSEEK_V4_FLASH)}</li>
 * </ul>
 */
@Slf4j
@Service
public class FastModelRoutingService {

    private final FastModelProperties fastProperties;
    private final ModelRoutingService.ModelRoutingProperties mainProperties;

    private final Map<FastModel, FastModelEntry> modelMap = new ConcurrentHashMap<>();
    private final Map<FastModelTask, FastModel> taskMapping = new ConcurrentHashMap<>();
    private volatile FastModel defaultModel;

    public FastModelRoutingService(FastModelProperties fastProperties,
                                   ModelRoutingService.ModelRoutingProperties mainProperties) {
        this.fastProperties = fastProperties;
        this.mainProperties = mainProperties;
    }

    @PostConstruct
    public void init() {
        rebuildModels();
        loadTaskMapping();
    }

    // ===== Public API =====

    public ChatModel getChatModel() {
        FastModelEntry entry = modelMap.get(defaultModel);
        return entry != null ? entry.getChatModel() : null;
    }

    public ChatModel getChatModel(FastModel model) {
        FastModelEntry entry = modelMap.get(model);
        if (entry == null) {
            return getChatModel();
        }
        return entry.getChatModel();
    }

    public ChatModel getChatModel(FastModelTask task) {
        FastModel model = taskMapping.getOrDefault(task, defaultModel);
        return getChatModel(model);
    }

    public StreamingChatModel getStreamingChatModel() {
        FastModelEntry entry = modelMap.get(defaultModel);
        return entry != null ? entry.getStreamingChatModel() : null;
    }

    public StreamingChatModel getStreamingChatModel(FastModel model) {
        FastModelEntry entry = modelMap.get(model);
        if (entry == null) {
            return getStreamingChatModel();
        }
        return entry.getStreamingChatModel();
    }

    public StreamingChatModel getStreamingChatModel(FastModelTask task) {
        FastModel model = taskMapping.getOrDefault(task, defaultModel);
        return getStreamingChatModel(model);
    }

    public String getCurrentModelName(FastModelTask task) {
        FastModel model = taskMapping.getOrDefault(task, defaultModel);
        return model != null ? model.getModelName() : "unknown";
    }

    public String getFallbackChain() {
        return String.join(",", modelMap.keySet().stream().map(FastModel::getModelName).toList());
    }

    // ===== Init =====

    private void loadTaskMapping() {
        Map<String, String> configMapping = fastProperties.getTaskMapping();
        if (configMapping == null || configMapping.isEmpty()) {
            log.warn("未配置快速模型任务映射，所有任务将使用默认模型");
            return;
        }
        for (FastModelTask task : FastModelTask.values()) {
            String modelName = configMapping.get(task.getConfigKey());
            if (modelName != null) {
                try {
                    FastModel model = FastModel.fromModelName(modelName);
                    taskMapping.put(task, model);
                    log.info("任务→模型映射: {} → {}", task.getConfigKey(), modelName);
                } catch (IllegalArgumentException e) {
                    log.warn("任务 '{}' 映射的模型 '{}' 未在枚举中定义，跳过", task.getConfigKey(), modelName);
                }
            }
        }
    }

    private synchronized void rebuildModels() {
        modelMap.clear();

        String defaultModelName = fastProperties.getDefaultModel();
        List<FastModelConfig> sorted = fastProperties.getModels().stream()
                .sorted(Comparator.comparingInt(FastModelConfig::getPriority))
                .toList();

        for (FastModelConfig config : sorted) {
            FastModel fastModel;
            try {
                fastModel = FastModel.fromModelName(config.getModelName());
            } catch (IllegalArgumentException e) {
                log.warn("快速模型 '{}' 未在 FastModel 枚举中定义，跳过", config.getModelName());
                continue;
            }

            ModelRoutingService.ModelProviderConfig providerConfig =
                    findProvider(config.getProvider());
            if (providerConfig == null) {
                log.warn("快速模型 '{}' 引用的 provider '{}' 不存在，跳过", config.getModelName(), config.getProvider());
                continue;
            }

            try {
                ChatModel chatModel = buildChatModel(providerConfig, config.getModelName());
                StreamingChatModel streamingModel = buildStreamingChatModel(providerConfig, config.getModelName());
                FastModelEntry entry = new FastModelEntry(config.getModelName(), config.getPriority(), chatModel, streamingModel);
                modelMap.put(fastModel, entry);
                log.info("快速模型已注册: model={}, provider={}, priority={}", config.getModelName(), config.getProvider(), config.getPriority());
            } catch (Exception e) {
                log.warn("快速模型构建失败: model={}, error={}", config.getModelName(), e.getMessage());
            }
        }

        if (!modelMap.isEmpty()) {
            try {
                defaultModel = FastModel.fromModelName(defaultModelName);
            } catch (IllegalArgumentException e) {
                defaultModel = modelMap.keySet().iterator().next();
                log.warn("默认快速模型 '{}' 不可用，回退到: {}", defaultModelName, defaultModel.getModelName());
            }
            if (!modelMap.containsKey(defaultModel)) {
                defaultModel = modelMap.keySet().iterator().next();
                log.warn("默认快速模型未注册，回退到第一个可用模型: {}", defaultModel.getModelName());
            }
            log.info("快速模型默认: {}", defaultModel.getModelName());
        } else {
            log.error("没有可用的快速模型！");
        }
    }

    private ModelRoutingService.ModelProviderConfig findProvider(String providerName) {
        if (mainProperties.getProviders() == null) return null;
        return mainProperties.getProviders().stream()
                .filter(p -> providerName.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    private ChatModel buildChatModel(ModelRoutingService.ModelProviderConfig providerConfig, String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(providerConfig.getBaseUrl())
                .apiKey(providerConfig.getApiKey())
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .build();
    }

    private StreamingChatModel buildStreamingChatModel(ModelRoutingService.ModelProviderConfig providerConfig, String modelName) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(providerConfig.getBaseUrl())
                .apiKey(providerConfig.getApiKey())
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    // ===== Inner types =====

    @Data
    public static class FastModelEntry {
        private final String modelName;
        private final int priority;
        private final ChatModel chatModel;
        private final StreamingChatModel streamingChatModel;
    }

    @Data
    @ConfigurationProperties(prefix = "icc.model.fast")
    @Service
    public static class FastModelProperties {
        private String defaultModel = "deepseek-v4-flash";
        private Map<String, String> taskMapping = new LinkedHashMap<>();
        private List<FastModelConfig> models = new ArrayList<>();
    }

    @Data
    public static class FastModelConfig {
        private String modelName;
        private String provider;
        private int priority;
    }
}
