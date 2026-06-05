package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.ai.spi.KnowledgeSegmentLookup;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static cn.cloudcat.user.common.constant.MetadataKeyConstant.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 测试 WifiElasticsearchContentRetriever 的检索逻辑，包括分数过滤、去重和排序。
 * 使用 TestableRetriever 子类来注入受控的 ES 检索结果，避免依赖真实的 ES 环境。
 *
 * @author Kang
 * @since 2026-05-28
 */
class WifiElasticsearchContentRetrieverTest {

    private RestClient restClient;
    private EmbeddingModel embeddingModel;
    private KnowledgeSegmentLookup knowledgeSegmentService;

    /**
     * 可测试子类，重写 search() 返回受控结果。
     */
    static class TestableRetriever extends WifiElasticsearchContentRetriever {

        private final Queue<EmbeddingSearchResult<TextSegment>> searchResults = new LinkedList<>();
        private int searchCallCount;

        TestableRetriever(RestClient restClient, EmbeddingModel embeddingModel,
                          KnowledgeSegmentLookup knowledgeSegmentService,
                          double minScore, int maxResults) {
            super(ElasticsearchConfigurationKnn.builder().build(),
                    restClient, "test-index", embeddingModel,
                    maxResults, minScore, null, knowledgeSegmentService);
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            searchCallCount++;
            if (searchResults.isEmpty()) {
                return new EmbeddingSearchResult<>(List.of());
            }
            return searchResults.poll();
        }

        void enqueueSearchResult(EmbeddingSearchResult<TextSegment> result) {
            searchResults.add(result);
        }

        int getSearchCallCount() {
            return searchCallCount;
        }
    }

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        embeddingModel = mock(EmbeddingModel.class);
        knowledgeSegmentService = mock(KnowledgeSegmentLookup.class);
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(new Embedding(new float[128])));
    }

    // ---- 分数过滤测试 ----

    @Test
    void shouldIncludeResultsAboveMinScore() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment aboveThreshold = segment("设备故障排查", "chunk-1", 100, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.85, aboveThreshold)));

        List<Content> results = retriever.retrieve(Query.from("设备故障"));

        assertThat(results).hasSize(1);
        assertThat(getScore(results.get(0))).isEqualTo(0.85);
    }

    @Test
    void shouldExcludeResultsBelowMinScore() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment belowThreshold = segment("低相关度内容", "chunk-low", 1, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.3, belowThreshold)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldFilterMixedResultsByMinScore() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment good = segment("高相关度", "c-good", 100, null, null);
        TextSegment bad = segment("低相关度", "c-bad", 200, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.92, good), match(0.2, bad)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().text()).isEqualTo("高相关度");
    }

    // ---- 排序测试 ----

    @Test
    void shouldSortResultsByText() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment segB = segment("B-设备设置", "c-1", 100, null, null);
        TextSegment segA = segment("A-网络配置", "c-2", 200, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.9, segB), match(0.8, segA)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results).hasSize(2);
        // retrieve() 按 textSegment.text() 排序
        assertThat(results.get(0).textSegment().text()).isEqualTo("A-网络配置");
        assertThat(results.get(1).textSegment().text()).isEqualTo("B-设备设置");
    }

    @Test
    void shouldSortChineseResultsByLexicographicOrder() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment seg1 = segment("随身WiFi使用指南", "c-a", 100, null, null);
        TextSegment seg2 = segment("设备网络设置方法", "c-b", 200, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.92, seg1), match(0.78, seg2)));

        List<Content> results = retriever.retrieve(Query.from("如何使用随身WiFi"));

        assertThat(results).hasSize(2);
        // 按 Unicode 词法序排列中文字符
        assertThat(results.get(0).textSegment().text()).isEqualTo("设备网络设置方法");
        assertThat(results.get(1).textSegment().text()).isEqualTo("随身WiFi使用指南");
    }

    // ---- 去重测试 ----

    @Test
    void shouldDeduplicateIdenticalContent() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment seg = segment("重复内容", "chunk-dup", 100, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.9, seg), match(0.85, seg)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results).hasSize(1);
    }

    @Test
    void shouldKeepDistinctContentWithSameDocId() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment seg1 = segment("文档A分段1", "c-1a", 100, null, null);
        TextSegment seg2 = segment("文档A分段2", "c-1b", 100, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.9, seg1), match(0.85, seg2)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        // 不同文本内容 → 不去重（去重仅对完全相同的 Content 生效）
        assertThat(results).hasSize(2);
    }

    // ---- 无结果场景测试 ----

    @Test
    void shouldReturnEmptyListWhenSearchReturnsNoMatches() {
        TestableRetriever retriever = buildRetriever(0.5);
        retriever.enqueueSearchResult(emptySearchResult());

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAllResultsBelowMinScore() {
        TestableRetriever retriever = buildRetriever(0.8);

        TextSegment low1 = segment("低分1", "c-1", 100, null, null);
        TextSegment low2 = segment("低分2", "c-2", 200, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.3, low1), match(0.4, low2)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results).isEmpty();
    }

    // ---- 元数据传递测试 ----

    @Test
    void shouldPreserveScoreInContentMetadata() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment seg = segment("内容", "chunk-1", 42, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.76, seg)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(getScore(results.get(0))).isEqualTo(0.76);
        assertThat(results.get(0).metadata().get(ContentMetadata.EMBEDDING_ID)).isNotNull();
    }

    @Test
    void shouldPreserveSegmentMetadataInResults() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment seg = segment("文档内容", "chunk-metadata-test", 555, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.9, seg)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        assertThat(results.get(0).textSegment().metadata().getString(CHUNK_ID))
                .isEqualTo("chunk-metadata-test");
        assertThat(results.get(0).textSegment().metadata().getInteger(DOC_ID)).isEqualTo(555);
    }

    // ---- 排序后保留分数测试 ----

    @Test
    void shouldRetainCorrectScoresAfterSorting() {
        TestableRetriever retriever = buildRetriever(0.5);

        TextSegment seg1 = segment("B内容", "c-1", 100, null, null);
        TextSegment seg2 = segment("A内容", "c-2", 200, null, null);
        retriever.enqueueSearchResult(searchResult(match(0.55, seg1), match(0.99, seg2)));

        List<Content> results = retriever.retrieve(Query.from("查询"));

        // 排序后: A内容在前, B内容在后
        assertThat(results.get(0).textSegment().text()).isEqualTo("A内容");
        assertThat(getScore(results.get(0))).isEqualTo(0.99);
        assertThat(results.get(1).textSegment().text()).isEqualTo("B内容");
        assertThat(getScore(results.get(1))).isEqualTo(0.55);
    }

    // ---- helper methods ----

    private TestableRetriever buildRetriever(double minScore) {
        return new TestableRetriever(restClient, embeddingModel,
                knowledgeSegmentService, minScore, 5);
    }

    private TextSegment segment(String text, String chunkId, int docId,
                                 String parentChunkId, String brotherChunkId) {
        Metadata meta = new Metadata();
        meta.put(CHUNK_ID, chunkId);
        meta.put(DOC_ID, docId);
        if (parentChunkId != null) meta.put(PARENT_CHUNK_ID, parentChunkId);
        if (brotherChunkId != null) meta.put(BROTHER_CHUNK_ID, brotherChunkId);
        return TextSegment.from(text, meta);
    }

    @SafeVarargs
    private static EmbeddingSearchResult<TextSegment> searchResult(EmbeddingMatch<TextSegment>... matches) {
        return new EmbeddingSearchResult<>(new ArrayList<>(List.of(matches)));
    }

    private static EmbeddingSearchResult<TextSegment> emptySearchResult() {
        return new EmbeddingSearchResult<>(List.of());
    }

    private static EmbeddingMatch<TextSegment> match(double score, TextSegment segment) {
        return new EmbeddingMatch<>(score, UUID.randomUUID().toString(),
                new Embedding(new float[128]), segment);
    }

    private static double getScore(Content content) {
        return (Double) content.metadata().get(ContentMetadata.SCORE);
    }
}
