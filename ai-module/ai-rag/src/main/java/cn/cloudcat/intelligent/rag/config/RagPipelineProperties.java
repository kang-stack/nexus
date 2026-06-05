package cn.cloudcat.intelligent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 管道开关（问题改写、检索等）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "icc.rag")
public class RagPipelineProperties {

    /**
     * 是否在检索前调用快速模型做问题改写。
     * 开发环境若无法访问 LLM API，可设为 false，直接用原文检索。
     */
    private boolean queryRewriteEnabled = true;
}
