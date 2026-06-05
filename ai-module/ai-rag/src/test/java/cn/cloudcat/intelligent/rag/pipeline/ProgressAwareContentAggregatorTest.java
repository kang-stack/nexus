package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.sse.AgentResponse;
import cn.cloudcat.intelligent.rag.model.RagReference;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cn.cloudcat.user.common.constant.MetadataKeyConstant.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgressAwareContentAggregatorTest {

    private ContentAggregator delegate;
    private AtomicReference<List<RagReference>> persistedRefs;
    private List<String> progressEvents;
    private ProgressAwareContentAggregator aggregator;

    @BeforeEach
    void setUp() {
        delegate = mock(ContentAggregator.class);
        persistedRefs = new AtomicReference<>();
        progressEvents = new ArrayList<>();
        aggregator = new ProgressAwareContentAggregator(
                delegate, progressEvents::add, "msg-001",
                (messageId, refs) -> persistedRefs.set(refs));
    }

    @Test
    void shouldDelegateAggregationAndReturnResults() {
        Content c1 = contentWithDocId(100, "片段A");
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        List<Content> results = aggregator.aggregate(Map.of());

        assertThat(results).containsExactly(c1);
        verify(delegate).aggregate(any());
    }

    @Test
    void shouldSendProgressAtEachStage() {
        when(delegate.aggregate(any())).thenReturn(List.of(contentWithDocId(1, "text")));

        aggregator.aggregate(Map.of());

        assertThat(progressEvents).hasSize(3);
        assertThat(isProgressEvent(progressEvents.get(0), "正在排序筛选结果...")).isTrue();
        assertThat(progressEvents.get(1)).contains("\"type\":\"reference\"");
        assertThat(isProgressEvent(progressEvents.get(2), "正在生成回答...")).isTrue();
    }

    @Test
    void shouldPersistReferencesToDatabase() {
        Content c1 = contentWithDocId(42, "文档42内容");
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        aggregator.aggregate(Map.of());

        assertThat(persistedRefs.get()).hasSize(1);
        assertThat(persistedRefs.get().get(0).getDocumentId()).isEqualTo("42");
    }

    @Test
    void shouldDeduplicateByDocIdKeepingFirst() {
        Content first = contentWithDocId(100, "文档100的第一个片段");
        Content duplicate = contentWithDocId(100, "文档100的第二个片段（应被丢弃）");
        when(delegate.aggregate(any())).thenReturn(List.of(first, duplicate));

        aggregator.aggregate(Map.of());

        assertThat(persistedRefs.get()).hasSize(1);
        assertThat(persistedRefs.get().get(0).getDocumentId()).isEqualTo("100");
    }

    @Test
    void shouldPreserveMultipleDocumentsWithDifferentDocIds() {
        Content c1 = contentWithDocId(1, "文档1");
        Content c2 = contentWithDocId(2, "文档2");
        Content c3 = contentWithDocId(3, "文档3");
        when(delegate.aggregate(any())).thenReturn(List.of(c1, c2, c3));

        aggregator.aggregate(Map.of());

        assertThat(persistedRefs.get()).hasSize(3);
    }

    @Test
    void shouldNotPersistWhenResultsAreEmpty() {
        when(delegate.aggregate(any())).thenReturn(List.of());

        aggregator.aggregate(Map.of());

        assertThat(persistedRefs.get()).isNull();
    }

    @Test
    void shouldNotPersistWhenCallbackIsNull() {
        Content c1 = contentWithDocId(1, "doc");
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        ProgressAwareContentAggregator aggregatorWithoutDb =
                new ProgressAwareContentAggregator(delegate, progressEvents::add, "msg-001", null);

        List<Content> results = aggregatorWithoutDb.aggregate(Map.of());
        assertThat(results).containsExactly(c1);
    }

    @Test
    void shouldNotPersistWhenMessageIdIsNull() {
        Content c1 = contentWithDocId(1, "doc");
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        ProgressAwareContentAggregator aggregatorWithoutMsgId =
                new ProgressAwareContentAggregator(delegate, progressEvents::add, null,
                        (messageId, refs) -> persistedRefs.set(refs));

        List<Content> results = aggregatorWithoutMsgId.aggregate(Map.of());
        assertThat(results).containsExactly(c1);
        assertThat(persistedRefs.get()).isNull();
    }

    @Test
    void shouldSurvivePersistenceFailureAndStillReturnResults() {
        Content c1 = contentWithDocId(1, "doc");
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        ProgressAwareContentAggregator failingAggregator = new ProgressAwareContentAggregator(
                delegate, progressEvents::add, "msg-001",
                (messageId, refs) -> { throw new RuntimeException("DB connection lost"); });

        List<Content> results = failingAggregator.aggregate(Map.of());

        assertThat(results).containsExactly(c1);
    }

    @Test
    void shouldSendReferenceEventWithCorrectJsonFormat() {
        Content c1 = contentWithDocId(123, "测试内容");
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        aggregator.aggregate(Map.of());

        String refEvent = progressEvents.stream()
                .filter(e -> e.contains("\"type\":\"reference\""))
                .findFirst().orElse(null);
        assertThat(refEvent).isNotNull();

        var json = JSON.parseObject(refEvent);
        assertThat(json.getString("type")).isEqualTo("reference");
        assertThat(json.getInteger("count")).isEqualTo(1);

        var refs = JSON.parseArray(json.getString("content"));
        assertThat(refs).hasSize(1);
    }

    @Test
    void shouldFilterReferencesWithoutChunkIdBeforeSendingEvent() {
        Content c1 = Content.from(
                TextSegment.from("内容", new Metadata(Map.of(DOC_ID, 99))),
                Map.of());
        when(delegate.aggregate(any())).thenReturn(List.of(c1));

        aggregator.aggregate(Map.of());

        assertThat(persistedRefs.get()).hasSize(1);

        String refEvent = progressEvents.stream()
                .filter(e -> e.contains("\"type\":\"reference\""))
                .findFirst().orElse(null);
        assertThat(refEvent).isNull();
    }

    @Test
    void shouldNotSendProgressWhenCallbackIsNull() {
        ProgressAwareContentAggregator noCallback =
                new ProgressAwareContentAggregator(delegate, null, "msg-001",
                        (messageId, refs) -> persistedRefs.set(refs));
        when(delegate.aggregate(any())).thenReturn(List.of(contentWithDocId(1, "text")));

        noCallback.aggregate(Map.of());
    }

    private Content contentWithDocId(int docId, String text) {
        return Content.from(
                TextSegment.from(text, new Metadata(Map.of(
                        DOC_ID, docId,
                        FILE_NAME, "file-" + docId + ".pdf",
                        CHUNK_ID, "chunk-" + docId,
                        URL, "/url/" + docId))),
                Map.of(ContentMetadata.SCORE, 0.8, ContentMetadata.RERANKED_SCORE, 0.9));
    }

    private boolean isProgressEvent(String raw, String expectedContent) {
        if (!raw.startsWith("{")) return false;
        try {
            var json = JSON.parseObject(raw);
            return "progress".equals(json.getString("type"))
                    && expectedContent.equals(json.getString("content"));
        } catch (Exception e) {
            return false;
        }
    }
}
