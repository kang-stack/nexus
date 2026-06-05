package cn.cloudcat.user.diagnosis;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.dmn.engine.DmnDecision;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.DmnEngineConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DMN 引擎持有者：管理 Camunda DMN 引擎实例和决策表缓存。
 * <p>
 * 启动时初始化引擎，按需从 classpath 加载 .dmn 文件并缓存解析结果，
 * 避免每次诊断请求都重新解析 DMN 文件。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@Slf4j
public class DmnEngineHolder {

    private DmnEngine engine;
    /** 决策表缓存：key = "resourcePath#decisionKey" */
    private final Map<String, DmnDecision> decisionCache = new ConcurrentHashMap<>();

    /** 启动时初始化 Camunda DMN 引擎 */
    @PostConstruct
    void init() {
        engine = DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine();
    }

    /** 加载（或从缓存获取）指定 classpath 资源下的决策表 */
    public DmnDecision loadDecision(String classpathResource, String decisionKey) {
        String cacheKey = classpathResource + "#" + decisionKey;
        return decisionCache.computeIfAbsent(cacheKey, k -> parseDecision(classpathResource, decisionKey));
    }

    /** 获取底层 DMN 引擎实例（用于 execute 调用） */
    public DmnEngine engine() {
        return engine;
    }

    /** 从 classpath 读取 .dmn 文件并解析为 DmnDecision */
    private DmnDecision parseDecision(String classpathResource, String decisionKey) {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        try (InputStream in = resource.getInputStream()) {
            return engine.parseDecision(decisionKey, in);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 DMN: " + classpathResource, e);
        }
    }
}
