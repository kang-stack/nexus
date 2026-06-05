package cn.cloudcat.common.integration.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ES 关闭时在启动日志中提示能力降级范围。
 */
@Component
@ConditionalOnProperty(prefix = ElasticSearchProperties.PREFIX, name = "enabled", havingValue = "false")
@Slf4j
public class ElasticsearchDisabledStartupLogger {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.warn("Elasticsearch 已关闭（elasticsearch.enabled=false）：RAG 向量检索、消息 ES 索引、文档向量入库不可用");
    }
}
