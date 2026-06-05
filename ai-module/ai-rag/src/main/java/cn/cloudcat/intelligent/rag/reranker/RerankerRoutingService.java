package cn.cloudcat.intelligent.rag.reranker;

import cn.cloudcat.user.common.constant.RerankerModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重排序模型路由服务
 * <p>
 * 管理多个重排序模型实例（本地 ONNX + 远程 API），按 priority 排序，
 * 默认使用最高优先级的可用模型，支持按模型名指定。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Service
public class RerankerRoutingService {

    private final RerankerProperties properties;
    private final Map<RerankerModel, ScoringModel> modelMap = new ConcurrentHashMap<>();
    private volatile RerankerModel defaultModel;

    public RerankerRoutingService(RerankerProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        rebuildModels();
    }

    // ===== Public API =====

    public ScoringModel getScoringModel() {
        return modelMap.get(defaultModel);
    }

    public ScoringModel getScoringModel(RerankerModel model) {
        ScoringModel scoringModel = modelMap.get(model);
        if (scoringModel == null) {
            return getScoringModel();
        }
        return scoringModel;
    }

    public String getCurrentModelName() {
        return defaultModel != null ? defaultModel.getConfigName() : "unknown";
    }

    public String getFallbackChain() {
        return String.join(",", modelMap.keySet().stream().map(RerankerModel::getConfigName).toList());
    }

    // ===== Init =====

    private synchronized void rebuildModels() {
        modelMap.clear();

        String defaultName = properties.getDefaultReranker();
        List<RerankerConfig> sorted = properties.getModels().stream()
                .sorted(Comparator.comparingInt(RerankerConfig::getPriority))
                .toList();

        for (RerankerConfig config : sorted) {
            RerankerModel rerankerModel;
            try {
                rerankerModel = RerankerModel.fromConfigName(config.getName());
            } catch (IllegalArgumentException e) {
                log.warn("重排序模型 '{}' 未在 RerankerModel 枚举中定义，跳过", config.getName());
                continue;
            }

            try {
                ScoringModel scoringModel = buildScoringModel(rerankerModel, config);
                modelMap.put(rerankerModel, scoringModel);
                log.info("重排序模型已注册: name={}, type={}, priority={}", config.getName(), config.getType(), config.getPriority());
            } catch (Exception e) {
                log.warn("重排序模型初始化失败: name={}, error={}", config.getName(), e.getMessage());
            }
        }

        if (!modelMap.isEmpty()) {
            try {
                defaultModel = RerankerModel.fromConfigName(defaultName);
            } catch (IllegalArgumentException e) {
                defaultModel = modelMap.keySet().iterator().next();
                log.warn("默认重排序模型 '{}' 不可用，回退到: {}", defaultName, defaultModel.getConfigName());
            }
            if (!modelMap.containsKey(defaultModel)) {
                defaultModel = modelMap.keySet().iterator().next();
                log.warn("默认重排序模型未注册，回退到第一个可用模型: {}", defaultModel.getConfigName());
            }
            log.info("重排序默认模型: {}", defaultModel.getConfigName());
        } else {
            log.error("没有可用的重排序模型！");
        }
    }

    private ScoringModel buildScoringModel(RerankerModel model, RerankerConfig config) {
        return switch (model.getType()) {
            case "onnx" -> buildOnnxModel(config);
            case "dashscope" -> buildDashScopeModel(config);
            default -> throw new IllegalArgumentException("不支持的重排序模型类型: " + model.getType());
        };
    }

    private ScoringModel buildOnnxModel(RerankerConfig config) {
        String modelPath = resolveModelPath(config.getModelFile());
        String tokenizerPath = resolveModelPath(config.getTokenizerFile());
        log.info("初始化 ONNX 重排序模型: model={}, tokenizer={}", modelPath, tokenizerPath);
        return new OnnxScoringModel(modelPath, tokenizerPath, 8192);
    }

    private ScoringModel buildDashScopeModel(RerankerConfig config) {
        log.info("初始化 DashScope 重排序模型: model={}", config.getModelName());
        return new QwenRerankerScoringModel(config.getApiKey(), config.getModelName());
    }

    /**
     * 解析模型文件路径，支持两种方式：
     * <ul>
     *   <li>绝对路径（以 / 或盘符开头）— 直接返回</li>
     *   <li>classpath 相对路径 — 从 classpath 加载，兼容 IDE 和 JAR 包运行</li>
     * </ul>
     */
    private String resolveModelPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("模型文件路径不能为空");
        }

        // 绝对路径：Unix (/opt/...) 或 Windows (C:\...)
        if (path.startsWith("/") || (path.length() >= 2 && path.charAt(1) == ':')) {
            File file = new File(path);
            if (!file.exists()) {
                throw new IllegalArgumentException(
                        String.format("模型文件不存在: %s，请确认路径正确", path));
            }
            return file.getAbsolutePath();
        }

        // classpath 相对路径
        return resolveClasspathToFilePath(path);
    }

    private String resolveClasspathToFilePath(String classpathResource) {
        URL resource = getClass().getClassLoader().getResource(classpathResource);
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("classpath 下未找到资源: %s，请确认模型文件已放置到 resources 目录", classpathResource));
        }

        try {
            File file = new File(resource.toURI());
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        } catch (Exception e) {
            log.debug("资源在 JAR 包内，将复制到临时文件: {}", classpathResource);
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            Path tempFile = Files.createTempFile("reranker-", "." + classpathResource.substring(classpathResource.lastIndexOf('.') + 1));
            tempFile.toFile().deleteOnExit();
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("已将 classpath 资源复制到临时文件: {}", tempFile.toAbsolutePath());
            return tempFile.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("无法从 classpath 复制资源到临时文件: " + classpathResource, e);
        }
    }

    // ===== Inner types =====

    @Data
    @ConfigurationProperties(prefix = "icc.reranker")
    @Service
    public static class RerankerProperties {
        private String defaultReranker = "bge-onnx";
        private List<RerankerConfig> models = new ArrayList<>();
    }

    @Data
    public static class RerankerConfig {
        private String name;
        private String type;
        private String modelFile;
        private String tokenizerFile;
        private String apiKey;
        private String modelName;
        private int priority;
    }
}
