package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.sse.AgentResponse;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WiFi 查询路由器
 * <p>
 * 简化版路由器，所有查询统一路由到 ES 知识库检索器。
 * 移除原设计的 Neo4j/SQL 数据库路由，专注 ES 向量+全文混合检索。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
public class WifiQueryRouter implements QueryRouter {

    private final Collection<ContentRetriever> contentRetrievers;
    private final Consumer<String> progressCallback;
    private final AtomicBoolean routeProgressSent = new AtomicBoolean(false);

    public WifiQueryRouter(Collection<ContentRetriever> contentRetrievers, Consumer<String> progressCallback) {
        this.contentRetrievers = contentRetrievers;
        this.progressCallback = progressCallback;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        if (progressCallback != null && routeProgressSent.compareAndSet(false, true)) {
            progressCallback.accept(AgentResponse.progress("正在路由您的问题..."));
        }
        log.info("WifiQueryRouter routing query to {} retrievers", contentRetrievers.size());
        return contentRetrievers;
    }
}
