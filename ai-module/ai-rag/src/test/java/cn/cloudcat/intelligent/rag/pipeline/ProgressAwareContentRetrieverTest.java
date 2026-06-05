package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.sse.AgentResponse;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Kang
 * @since 2026-05-28
 */
class ProgressAwareContentRetrieverTest {

    @Test
    void shouldDelegateRetrievalToInnerRetriever() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        Query query = Query.from("测试查询");
        Content expected = Content.from(TextSegment.from("结果"));
        when(delegate.retrieve(query)).thenReturn(List.of(expected));

        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, null);

        List<Content> results = retriever.retrieve(query);
        assertThat(results).containsExactly(expected);
        verify(delegate).retrieve(query);
    }

    @Test
    void shouldSendProgressEventOnFirstCall() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        when(delegate.retrieve(any())).thenReturn(List.of());
        List<String> events = new ArrayList<>();

        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, events::add);

        retriever.retrieve(Query.from("q1"));
        retriever.retrieve(Query.from("q2"));

        assertThat(events).hasSize(1);
        assertThat(isProgressEvent(events.get(0))).isTrue();
    }

    @Test
    void shouldNotSendDuplicateProgressOnMultipleCalls() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        when(delegate.retrieve(any())).thenReturn(List.of());
        List<String> events = new ArrayList<>();

        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, events::add);

        retriever.retrieve(Query.from("q1"));
        retriever.retrieve(Query.from("q2"));
        retriever.retrieve(Query.from("q3"));

        assertThat(events).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenDelegateThrowsException() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        when(delegate.retrieve(any())).thenThrow(new RuntimeException("ES index not found"));

        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, null);

        List<Content> results = retriever.retrieve(Query.from("query"));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnEmptyListOnAnyExceptionType() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        when(delegate.retrieve(any())).thenThrow(new NullPointerException());

        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, null);

        List<Content> results = retriever.retrieve(Query.from("query"));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldNotSendProgressWhenCallbackIsNull() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        when(delegate.retrieve(any())).thenReturn(List.of());

        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, null);

        // should not throw NPE
        retriever.retrieve(Query.from("q"));
    }

    @Test
    void shouldExposeDelegate() {
        ContentRetriever delegate = mock(ContentRetriever.class);
        ProgressAwareContentRetriever retriever = new ProgressAwareContentRetriever(delegate, null);

        assertThat(retriever.getDelegate()).isSameAs(delegate);
    }

    private boolean isProgressEvent(String raw) {
        if (!raw.startsWith("{")) return false;
        try {
            var json = JSON.parseObject(raw);
            return "progress".equals(json.getString("type")) && json.getString("content") != null;
        } catch (Exception e) {
            return false;
        }
    }
}
