package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.sse.AgentResponse;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Kang
 * @since 2026-05-28
 */
class WifiQueryRouterTest {

    @Test
    void shouldReturnAllRegisteredRetrievers() {
        ContentRetriever r1 = mock(ContentRetriever.class);
        ContentRetriever r2 = mock(ContentRetriever.class);
        Collection<ContentRetriever> retrievers = List.of(r1, r2);

        WifiQueryRouter router = new WifiQueryRouter(retrievers, null);

        Collection<ContentRetriever> result = router.route(Query.from("如何使用设备"));

        assertThat(result).containsExactly(r1, r2);
    }

    @Test
    void shouldReturnSameRetrieversForAnyQuery() {
        ContentRetriever r1 = mock(ContentRetriever.class);
        Collection<ContentRetriever> retrievers = List.of(r1);

        WifiQueryRouter router = new WifiQueryRouter(retrievers, null);

        Query q1 = Query.from("设备无法连接网络");
        Query q2 = Query.from("如何修改套餐");
        Query q3 = Query.from("APN怎么配置");

        assertThat(router.route(q1)).isEqualTo(retrievers);
        assertThat(router.route(q2)).isEqualTo(retrievers);
        assertThat(router.route(q3)).isEqualTo(retrievers);
    }

    @Test
    void shouldSendProgressEventOnFirstRouteCallOnly() {
        ContentRetriever r1 = mock(ContentRetriever.class);
        List<String> events = new ArrayList<>();

        WifiQueryRouter router = new WifiQueryRouter(List.of(r1), events::add);

        router.route(Query.from("q1"));
        router.route(Query.from("q2"));
        router.route(Query.from("q3"));

        assertThat(events).hasSize(1);
        assertThat(isProgressEvent(events.get(0), "正在路由您的问题...")).isTrue();
    }

    @Test
    void shouldNotSendProgressWhenCallbackIsNull() {
        ContentRetriever r1 = mock(ContentRetriever.class);
        WifiQueryRouter router = new WifiQueryRouter(List.of(r1), null);

        // should not throw NPE
        router.route(Query.from("query"));
    }

    @Test
    void shouldHandleEmptyRetrieverList() {
        WifiQueryRouter router = new WifiQueryRouter(List.of(), null);

        Collection<ContentRetriever> result = router.route(Query.from("query"));

        assertThat(result).isEmpty();
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
