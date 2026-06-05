package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.sse.AgentResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.AbstractElasticsearchEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 带进度通知的检索器，输出 AgentResponse JSON 格式。
 * 当底层检索器抛出异常（如 ES 索引不存在）时，返回空列表而非传播异常。
 *
 * @author Kang
 * @since 2026-06-02
 */
public class ProgressAwareContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(ProgressAwareContentRetriever.class);

    private final ContentRetriever delegate;
    private final Consumer<String> progressCallback;
    private final AtomicBoolean progressSent = new AtomicBoolean(false);

    public ProgressAwareContentRetriever(ContentRetriever delegate, Consumer<String> progressCallback) {
        this.delegate = delegate;
        this.progressCallback = progressCallback;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (progressCallback != null) {
            if (delegate instanceof AbstractElasticsearchEmbeddingStore) {
                if (progressSent.compareAndSet(false, true)) {
                    progressCallback.accept(AgentResponse.progress("正在检索知识库内容..."));
                }
            } else {
                if (progressSent.compareAndSet(false, true)) {
                    progressCallback.accept(AgentResponse.progress("正在检索文档内容..."));
                }
            }
        }
        try {
            return delegate.retrieve(query);
        } catch (Exception e) {
            log.warn("Content retriever failed, returning empty results: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public ContentRetriever getDelegate() {
        return delegate;
    }
}
