package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.ai.spi.KnowledgeSegmentLookup;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.elasticsearch.*;
import dev.langchain4j.store.embedding.filter.Filter;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static cn.cloudcat.user.common.constant.MetadataKeyConstant.BROTHER_CHUNK_ID;
import static cn.cloudcat.user.common.constant.MetadataKeyConstant.PARENT_CHUNK_ID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * KnowEngine Elasticsearch 内容检索器
 * <p>
 * 基于 Elasticsearch 的向量检索实现，支持以下特性：
 * <ul>
 *   <li><b>向量检索 (KNN)</b>：使用 Embedding 模型将查询文本向量化，进行相似度搜索</li>
 *   <li><b>全文检索</b>：支持 Elasticsearch 全文搜索</li>
 *   <li><b>混合检索</b>：结合向量检索和全文检索（需 Elasticsearch 相应许可证）</li>
 *   <li><b>关联内容扩展</b>：自动检索兄弟分段 (brother chunk) 和父分段 (parent chunk) 内容</li>
 * </ul>
 * <p>
 * <b>关联内容扩展机制：</b>
 * <ul>
 *   <li>兄弟分段：具有相同父分段的其他子分段，用于获取完整上下文</li>
 *   <li>父分段：从 Redis 中读取父分段的完整文本，替换子分段以获得更完整的语义</li>
 * </ul>
 * <p>
 *
 * @see ElasticsearchContentRetriever
 * @see ContentRetriever
 *
 * @author Kang
 * @since 2026-06-02
 */
public class WifiElasticsearchContentRetriever extends AbstractElasticsearchEmbeddingStore implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(WifiElasticsearchContentRetriever.class);
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;
    private final KnowledgeSegmentLookup knowledgeSegmentLookup;

    /**
     * Creates an instance of ElasticsearchContentRetriever using a RestClient.
     *
     * @param configuration  Elasticsearch retriever configuration to use (knn, script, full text, hybrid, hybrid with reranker)
     * @param restClient     Elasticsearch Rest Client (mandatory)
     * @param indexName      Elasticsearch index name (optional). Default value: "default".
     *                       Index will be created automatically if not exists.
     * @param embeddingModel Embedding model to be used by the retriever
     * @param maxResults     Maximum number of results to retrieve
     * @param minScore       Minimum score threshold for retrieved results
     * @param filter         Filter to apply during retrieval
     */
    public WifiElasticsearchContentRetriever(
            ElasticsearchConfiguration configuration,
            RestClient restClient,
            String indexName,
            EmbeddingModel embeddingModel,
            final int maxResults,
            final double minScore,
            final Filter filter,
            KnowledgeSegmentLookup knowledgeSegmentLookup) {
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
        this.knowledgeSegmentLookup = knowledgeSegmentLookup;
        this.initialize(configuration, restClient, indexName);
    }

    /**
     * 根据查询条件检索相关内容
     * <p>
     * 根据当前配置的检索模式执行内容检索，支持以下三种模式：
     * <ul>
     *   <li><b>全文检索</b>：当配置为 {@link ElasticsearchConfigurationFullText} 时，直接执行全文搜索</li>
     *   <li><b>混合检索</b>：当配置为 {@link ElasticsearchConfigurationHybrid} 时，结合向量检索和全文检索</li>
     *   <li><b>向量检索（默认）</b>：将查询文本向量化后进行 KNN 相似度搜索</li>
     * </ul>
     * <p>
     * 在向量检索模式下，检索结果还会进行关联内容扩展：
     * <ul>
     *   <li>兄弟分段扩展：根据 brotherChunkId 检索同一父分段下的其他兄弟分段，补全上下文</li>
     *   <li>父分段替换：根据 parentChunkId 从 Redis 中读取父分段的完整文本，替换子分段以获得更完整的语义</li>
     * </ul>
     *
     * @param query 查询对象，包含待检索的文本内容
     * @return 检索到的内容列表，包含原始检索结果及扩展的关联内容
     */
    @Override
    public List<Content> retrieve(final Query query) {
        // 全文检索模式：直接执行全文搜索并返回结果
        if (configuration instanceof ElasticsearchConfigurationFullText) {
            log.debug("Using a full text search query");
            return this.fullTextSearch(query.text()).stream()
                    .map(t -> Content.from(
                            t,
                            Map.of(
                                    ContentMetadata.SCORE, t.metadata().getDouble(ContentMetadata.SCORE.name()),
                                    ContentMetadata.EMBEDDING_ID,
                                    t.metadata().getString(ContentMetadata.EMBEDDING_ID.name()))))
                    .toList();
        }
        // 将查询文本转换为向量
        Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
        // 构建向量搜索请求，设置查询向量、最大返回数量、最低相似度分数和过滤条件
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();

        // 混合检索模式：结合向量检索和全文检索
        if (configuration instanceof ElasticsearchConfigurationHybrid) {
            return mapResultsToContentList(this.hybridSearch(request, query.text()));
        }

        // 向量检索模式（默认）：执行 KNN 向量相似度搜索
        List<Content> searchContents = mapResultsToContentList(this.search(request));
        // 去重并按文本内容排序
        searchContents = searchContents.stream().distinct().sorted(Comparator.comparing(content -> content.textSegment().text())).toList();
        List<Content> finalContents = new ArrayList<>(searchContents);

        // 兄弟分段缓存和父分段缓存，避免重复查询
        Map<String, List<Content>> brotherDocMap = new HashMap<>();
        Map<String, List<Content>> parentDocMap = new HashMap<>();
        List<Content> toRemove = new ArrayList<>();
        List<Content> toAdd = new ArrayList<>();

        for (Content content : searchContents) {
            // 兄弟分段扩展：检索具有相同 brotherChunkId 的其他兄弟分段
            String brotherChunkId = content.textSegment().metadata().getString(BROTHER_CHUNK_ID);
            if (brotherChunkId != null) {
                List<Content> cachedBrotherDocs = brotherDocMap.get(brotherChunkId);
                if (cachedBrotherDocs != null) {
                    toAdd.addAll(cachedBrotherDocs);
                } else {
                    Filter brotherFilter = metadataKey(BROTHER_CHUNK_ID).isEqualTo(brotherChunkId);
                    request = EmbeddingSearchRequest.builder()
                            .filter(brotherFilter)
                            .build();
                    List<Content> brotherDocs = mapResultsToContentList(this.search(request));
                    brotherDocMap.put(brotherChunkId, brotherDocs);
                    toAdd.addAll(brotherDocs);
                }
            }

            // 父分段替换：用父分段的完整文本替换子分段，获取更完整的语义
            String parentChunkId = content.textSegment().metadata().getString(PARENT_CHUNK_ID);
            if (parentChunkId != null) {
                List<Content> cachedParentDocs = parentDocMap.get(parentChunkId);
                if (cachedParentDocs != null) {
                    toRemove.add(content);
                } else if (knowledgeSegmentLookup != null) {
                    String segmentText = knowledgeSegmentLookup.getTextByChunkId(parentChunkId);
                    if (segmentText != null) {
                        TextSegment parentSegment = TextSegment.from(segmentText, content.textSegment().metadata());
                        Content parentContent = Content.from(parentSegment, content.metadata());
                        List<Content> parentDocs = List.of(parentContent);
                        parentDocMap.put(parentChunkId, parentDocs);
                        toRemove.add(content);
                        toAdd.addAll(parentDocs);
                    } else {
                        log.warn("parentChunk not found in Redis, chunkId: {}", parentChunkId);
                        toRemove.add(content);
                    }
                }
            }
        }
        finalContents.removeAll(toRemove);
        finalContents.addAll(toAdd);
        // 去重：兄弟分段扩展可能将已在原始搜索结果中的分段重复添加
        return finalContents.stream().distinct().toList();
    }

    private List<Content> mapResultsToContentList(EmbeddingSearchResult<TextSegment> searchResult) {
        List<Content> result = searchResult.matches().stream()
                .filter(f -> f.score() > minScore)
                .map(m -> Content.from(
                        m.embedded(),
                        Map.of(
                                ContentMetadata.SCORE, m.score(),
                                ContentMetadata.EMBEDDING_ID, m.embeddingId())))
                .toList();
        log.debug("Found [{}] relevant documents in Elasticsearch index [{}].", result.size(), indexName);
        return result;
    }

    public static WifiElasticsearchContentRetriever.Builder builder() {
        return new WifiElasticsearchContentRetriever.Builder();
    }

    public static class Builder {

        private RestClient restClient;
        private String indexName = "default";
        private ElasticsearchConfiguration configuration =
                ElasticsearchConfigurationKnn.builder().build();
        private EmbeddingModel embeddingModel;
        private int maxResults;
        private double minScore;
        private Filter filter;
        private KnowledgeSegmentLookup knowledgeSegmentLookup;

        /**
         * @param restClient Elasticsearch RestClient.
         * @return builder
         */
        public WifiElasticsearchContentRetriever.Builder restClient(RestClient restClient) {
            this.restClient = restClient;
            return this;
        }

        /**
         * @param indexName Elasticsearch index name (optional). Default value: "default".
         * @return builder
         */
        public WifiElasticsearchContentRetriever.Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * @param configuration the configuration to use
         * @return builder
         */
        public WifiElasticsearchContentRetriever.Builder configuration(ElasticsearchConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public WifiElasticsearchContentRetriever.Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public WifiElasticsearchContentRetriever.Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public WifiElasticsearchContentRetriever.Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public WifiElasticsearchContentRetriever.Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }


        public WifiElasticsearchContentRetriever.Builder knowledgeSegmentLookup(KnowledgeSegmentLookup knowledgeSegmentLookup) {
            this.knowledgeSegmentLookup = knowledgeSegmentLookup;
            return this;
        }

        public WifiElasticsearchContentRetriever build() {
            return new WifiElasticsearchContentRetriever(
                    configuration, restClient, indexName, embeddingModel, maxResults, minScore, filter, knowledgeSegmentLookup);
        }
    }
}