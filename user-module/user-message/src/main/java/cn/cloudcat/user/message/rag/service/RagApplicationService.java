package cn.cloudcat.user.message.rag.service;

import cn.cloudcat.user.common.constant.FastModelTask;
import cn.cloudcat.ai.spi.KnowledgeSegmentLookup;
import cn.cloudcat.user.message.agent.service.AgentTaskManager;
import cn.cloudcat.user.message.ai.service.FastModelRoutingService;
import cn.cloudcat.user.message.ai.service.ModelRoutingService;
import cn.cloudcat.user.message.ai.service.PromptService;
import cn.cloudcat.user.message.ai.service.WifiChatAiService;
import cn.cloudcat.user.message.chat.memory.EvictableChatMemoryStore;
import cn.cloudcat.user.message.rag.model.RagChatParam;
import cn.cloudcat.common.integration.elasticsearch.ElasticSearchConfiguration;
import cn.cloudcat.intelligent.rag.pipeline.ProgressAwareContentAggregator;
import cn.cloudcat.intelligent.rag.pipeline.ProgressAwareContentRetriever;
import cn.cloudcat.intelligent.rag.pipeline.WifiElasticsearchContentRetriever;
import cn.cloudcat.intelligent.rag.pipeline.WifiQueryRouter;
import cn.cloudcat.intelligent.rag.pipeline.WifiQueryTransformer;
import cn.cloudcat.intelligent.rag.reranker.RerankerRoutingService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class RagApplicationService {

    private final ModelRoutingService modelRoutingService;
    private final FastModelRoutingService fastModelRoutingService;
    private final RerankerRoutingService rerankerRoutingService;
    private final KnowledgeSegmentLookup knowledgeSegmentLookup;
    private final RestClient restClient;
    private final EmbeddingModel embeddingModel;
    private final PromptService promptService;
    private final AgentTaskManager agentTaskManager;

    public RagApplicationService(
            ModelRoutingService modelRoutingService,
            FastModelRoutingService fastModelRoutingService,
            RerankerRoutingService rerankerRoutingService,
            KnowledgeSegmentLookup knowledgeSegmentLookup,
            @Autowired(required = false) RestClient restClient,
            EmbeddingModel embeddingModel,
            PromptService promptService,
            AgentTaskManager agentTaskManager) {
        this.modelRoutingService = modelRoutingService;
        this.fastModelRoutingService = fastModelRoutingService;
        this.rerankerRoutingService = rerankerRoutingService;
        this.knowledgeSegmentLookup = knowledgeSegmentLookup;
        this.restClient = restClient;
        this.embeddingModel = embeddingModel;
        this.promptService = promptService;
        this.agentTaskManager = agentTaskManager;
    }

    public Flux<String> doChatWithMemory(
            RagChatParam chatParam,
            EvictableChatMemoryStore memoryStore,
            Consumer<String> onComplete) {
        if (restClient == null) {
            return Flux.error(new IllegalStateException("Elasticsearch 未启用，RAG 检索不可用"));
        }
        return Flux.<String>create(sink -> {
            Consumer<String> progressCallback = sink::next;

            WifiQueryTransformer queryTransformer = new WifiQueryTransformer(
                    fastModelRoutingService.getChatModel(FastModelTask.QUERY_REWRITE),
                    chatParam.messageId(),
                    progressCallback);

            ProgressAwareContentRetriever embeddingRetriever = new ProgressAwareContentRetriever(
                    WifiElasticsearchContentRetriever.builder()
                            .configuration(ElasticsearchConfigurationKnn.builder().build())
                            .maxResults(5)
                            .minScore(0.5)
                            .embeddingModel(embeddingModel)
                            .restClient(restClient)
                            .indexName(ElasticSearchConfiguration.INDEX_NAME)
                            .knowledgeSegmentLookup(knowledgeSegmentLookup)
                            .build(),
                    progressCallback);

            ProgressAwareContentRetriever fullTextRetriever = new ProgressAwareContentRetriever(
                    ElasticsearchContentRetriever.builder()
                            .configuration(ElasticsearchConfigurationFullText.builder().build())
                            .restClient(restClient)
                            .indexName(ElasticSearchConfiguration.INDEX_NAME)
                            .maxResults(5)
                            .build(),
                    progressCallback);

            ScoringModel scoringModel = rerankerRoutingService.getScoringModel();
            ContentAggregator contentAggregator = new ProgressAwareContentAggregator(
                    ReRankingContentAggregator.builder()
                            .scoringModel(scoringModel)
                            .maxResults(5)
                            .querySelector(queryToContents -> queryToContents.keySet().iterator().next())
                            .build(),
                    progressCallback,
                    chatParam.assistantMessageId(),
                    (messageId, refs) -> { });

            String systemPrompt = promptService.getPrompt(chatParam.intentRecognitionResult());
            ContentInjector contentInjector = (contents, chatMessage) -> {
                if (chatMessage instanceof UserMessage userMsg) {
                    if (contents == null || contents.isEmpty()) {
                        return UserMessage.from(systemPrompt + "\n\n" + userMsg.singleText());
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(systemPrompt).append("\n\n");
                    sb.append("## 参考文档\n\n");
                    int index = 1;
                    for (Content c : contents) {
                        sb.append("### 文档片段 ").append(index++).append("\n");
                        sb.append(c.textSegment().text()).append("\n\n");
                    }
                    sb.append("## 用户问题\n");
                    sb.append(userMsg.singleText());
                    return UserMessage.from(sb.toString());
                }
                return chatMessage;
            };

            RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                    .queryRouter(new WifiQueryRouter(List.of(embeddingRetriever, fullTextRetriever), progressCallback))
                    .queryTransformer(queryTransformer)
                    .contentAggregator(contentAggregator)
                    .contentInjector(contentInjector)
                    .build();

            WifiChatAiService wifiChatAiService = AiServices.builder(WifiChatAiService.class)
                    .chatModel(modelRoutingService.getChatModel())
                    .streamingChatModel(modelRoutingService.getStreamingChatModel())
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                            .id(memoryId)
                            .maxMessages(10)
                            .chatMemoryStore(memoryStore)
                            .build())
                    .retrievalAugmentor(retrievalAugmentor)
                    .build();

            StringBuilder contentBuilder = new StringBuilder();
            Disposable disposable = wifiChatAiService.streamChat(chatParam.conversationId(), chatParam.content())
                    .doOnNext(contentBuilder::append)
                    .doOnComplete(() -> {
                        String body = contentBuilder.toString();
                        if (onComplete != null) {
                            onComplete.accept(body);
                        }
                    })
                    .subscribe(sink::next, sink::error, sink::complete);

            sink.onCancel(disposable::dispose);
            agentTaskManager.setDisposable(chatParam.conversationId(), disposable);
        }).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel());
    }
}
