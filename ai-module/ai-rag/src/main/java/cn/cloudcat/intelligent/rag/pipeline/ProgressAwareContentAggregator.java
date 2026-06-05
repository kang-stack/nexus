package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.constant.RetrievalSource;
import cn.cloudcat.user.common.sse.AgentResponse;
import cn.cloudcat.intelligent.rag.model.RagReference;
import cn.cloudcat.intelligent.rag.util.ReferenceUtil;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 带进度通知的内容聚合器，输出 AgentResponse JSON 格式。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
public class ProgressAwareContentAggregator implements ContentAggregator {

    private final ContentAggregator delegate;
    private final Consumer<String> progressCallback;
    private final String messageId;
    private final BiConsumer<String, List<RagReference>> ragReferenceCallback;

    public ProgressAwareContentAggregator(ContentAggregator delegate, Consumer<String> progressCallback,
                                          String messageId, BiConsumer<String, List<RagReference>> ragReferenceCallback) {
        this.delegate = delegate;
        this.progressCallback = progressCallback;
        this.messageId = messageId;
        this.ragReferenceCallback = ragReferenceCallback;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        if (progressCallback != null) {
            progressCallback.accept(AgentResponse.progress("正在排序筛选结果..."));
        }

        List<Content> results = delegate.aggregate(queryToContents);

        try {
            List<RagReference> ragReferences = results.stream()
                    .collect(Collectors.toMap(
                            content -> content.textSegment().metadata().getInteger(
                                    cn.cloudcat.user.common.constant.MetadataKeyConstant.DOC_ID),
                            content -> content,
                            (existing, replacement) -> existing
                    )).values().stream()
                    .map(content -> ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID))
                    .collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(ragReferences) && ragReferenceCallback != null && messageId != null) {
                ragReferenceCallback.accept(messageId, ragReferences);
            }

            ragReferences = ragReferences.stream()
                    .filter(reference -> reference.getChunkId() != null)
                    .collect(Collectors.toList());

            if (progressCallback != null && !CollectionUtils.isEmpty(ragReferences)) {
                progressCallback.accept(AgentResponse.reference(JSON.toJSONString(ragReferences)));
            }
        } catch (Exception e) {
            log.warn("RAG引用信息回写失败: messageId={}", messageId, e);
        }

        if (progressCallback != null) {
            progressCallback.accept(AgentResponse.progress("正在生成回答..."));
        }

        return results;
    }
}
