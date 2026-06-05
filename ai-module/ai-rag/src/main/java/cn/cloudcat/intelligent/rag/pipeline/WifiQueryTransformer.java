package cn.cloudcat.intelligent.rag.pipeline;

import cn.cloudcat.user.common.sse.AgentResponse;
import com.google.common.base.Stopwatch;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

/**
 * KnowEngine 查询改写器
 * <p>
 * 基于 LLM 对用户查询进行智能改写优化，结合历史对话上下文，提升 RAG 检索效果。
 * 支持5种改写策略：
 * <ul>
 *   <li><b>简洁改写</b>：删除无意义的语气词、修饰词，将疑问句转为陈述句</li>
 *   <li><b>抽象概念改写</b>：将具体问题转化为更基础、更抽象的查询表述</li>
 *   <li><b>错别字改写</b>：纠正错别字和拼音错误</li>
 *   <li><b>车型信息提取</b>：标准化提取用户提及的品牌、型号、年款等车型信息</li>
 *   <li><b>上下文补全</b>：结合历史对话识别相关细节和术语，将问题重组为独立完整的查询</li>
 * </ul>
 * <p>
 * <b>处理流程：</b>
 * <ol>
 *   <li>通过 progressCallback 发送进度事件通知前端"正在优化您的问题"</li>
 *   <li>从 Query metadata 中提取历史对话记忆，格式化为 Prompt 上下文</li>
 *   <li>使用 LLM 根据 Prompt 模板改写用户查询</li>
 *   <li>构造增强查询（附加用户ID、当前时间等上下文信息）</li>
 *   <li>返回改写后的单个查询</li>
 * </ol>
 * <p>
 * <b>注意：</b>历史对话记忆由 {@link cn.cloudcat.user.message.chat.memory.UserChatMemoryStore} 提供。
 *
 * @see QueryTransformer
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
public class WifiQueryTransformer implements QueryTransformer {

    protected final ChatModel chatModel;

    protected final PromptTemplate promptTemplate;

    /**
     * assistant 消息的 messageId，用于日志追踪改写链路。
     */
    private final String chatMessageId;

    /**
     * 进度回调，用于流式返回前端进度信息
     */
    private final Consumer<String> progressCallback;

    private static final PromptTemplate LG_AGENT_PROMPT = PromptTemplate.from("""
            你是一个随身WiFi智能客服助手，你的职责范围是随身WiFi相关的咨询场景，包括设备使用指导、网络故障排查、套餐咨询、账户管理、售后服务等。你需要对用户的问题进行改写，使得改写后的问题在查询向量数据库时有更好的结果，并删除任何无关信息，确保查询简洁明了、具体明确。下面有一些改写的策略。

            1、简洁改写。问题可能比较长，包含了一些无意义的语气词、修饰词或者重复的词语等。尤其是问题在询问设备配置、套餐价格时，且包含一些无意义的日期、编号等修饰词。改写规则：删除无意义的词语使其更适合搜索引擎检索，疑问句要转成陈述句。
            2、抽象概念改写。前提：用户的问题一定在询问随身WiFi相关的问题，且是一些比较具体的细节问题，比如"我的设备总是连不上网，信号灯一直在闪"。需要改写成类似"随身WiFi无法连接网络故障排查"，将具体的问题转化为更基础、更简洁、更抽象的问题。
            3、错别字改写。用户的问题包含了错别字或者是一些常见的网络术语用户打成了对应的拼音。大小写不一样不属于错别字。错别字需要给出纠正结果。
            4、设备型号提取。如果用户提到了具体的设备型号信息，需要将其标准化提取。比如"华为随行WiFi 3"改写为"华为E5570"。
            5、结合历史对话和最新提问，识别出所有相关的细节、术语和上下文信息。最后，将这条提问重新组织成一个清晰、简洁且独立完整的格式，以便于进行信息检索。

            上面是5种改写策略，需要逐一使用最终给出一个统一的改写结果。直接输出改写后的结果，不需要输出思考过程及额外的多余内容。如果不需要改写，则直接输出原问题即可。

            ### 示例

            Input：我的随身WiFi突然上不了网了怎么办
            Output：随身WiFi无法连接网络故障排查

            Input：这个设备用的是什么套餐，流量够不够用
            Output：随身WiFi套餐流量查询

            Input：设备管理页面的密码怎么改
            Output：随身WiFi管理页面密码修改方法

            Input：MF782怎么设置APN
            Output：MF782设备APN设置教程

            Input：流量到期了怎么续费
            Output：随身WiFi流量套餐续费方式

            Input：设备信号不太好总是断线
            Output：随身WiFi信号弱频繁断线解决方法

            ### 历史对话内容
            {{chatMemory}}

            ### 用户问题：

            {{query}}

            ### 要求：
            非常重要的一点是：你只需要提供重新组织后的提问，不要包含任何其他内容！绝对不要在提问前添加任何多余的文字！
            """);

    public WifiQueryTransformer(ChatModel chatModel, String chatMessageId) {
        this(chatModel, LG_AGENT_PROMPT, chatMessageId, null);
    }

    public WifiQueryTransformer(ChatModel chatModel, String chatMessageId, Consumer<String> progressCallback) {
        this(chatModel, LG_AGENT_PROMPT, chatMessageId, progressCallback);
    }

    public WifiQueryTransformer(ChatModel chatModel, PromptTemplate promptTemplate, String chatMessageId, Consumer<String> progressCallback) {
        this.promptTemplate = ensureNotNull(promptTemplate, "promptTemplate");
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.chatMessageId = chatMessageId;
        this.progressCallback = progressCallback;
    }

    @Override
    public Collection<Query> transform(Query query) {
        // 发送进度：开始问题改写
        if (progressCallback != null) {
            progressCallback.accept(AgentResponse.progress("正在优化您的问题..."));
        }

        log.info("开始问题改写, 原始问题: {}", query.text());
        List<ChatMessage> chatMemory = (query.metadata() != null && query.metadata().chatMemory() != null)
                ? query.metadata().chatMemory() : List.of();

        String rewritten;
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            rewritten = chatModel.chat(createPrompt(query, format(chatMemory)).text());
            log.info("问题改写完成, 改写结果: {}, 耗时: {}ms",
                    rewritten, stopwatch.stop().elapsed(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            log.warn("问题改写失败（{}），使用原始问题继续 RAG 检索: {}",
                    e.getClass().getSimpleName(), query.text(), e);
            rewritten = query.text();
        }

        Query compressedQuery = toCompressedQuery(query, rewritten);
        log.info("检索查询就绪, source: {}, compressed: {}", query.text(), compressedQuery.text());

        if (chatMessageId != null) {
            log.debug("Query rewritten for messageId={}: {}", chatMessageId, rewritten);
        }

        return singletonList(compressedQuery);
    }

    /** 将改写结果包装为带时间上下文的检索查询 */
    private Query toCompressedQuery(Query query, String rewritten) {
        String newQuery = "我的问题是：" + rewritten + ", 现在是：" + LocalDateTime.now();
        return query.metadata() == null
                ? Query.from(newQuery)
                : Query.from(newQuery, query.metadata());
    }

    protected Prompt createPrompt(Query query, String chatMemory) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        variables.put("chatMemory", chatMemory);
        return promptTemplate.apply(variables);
    }

    protected String format(List<ChatMessage> chatMemory) {
        return chatMemory.stream()
                .map(this::format)
                .filter(Objects::nonNull)
                .collect(joining("\n"));
    }

    protected String format(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return "User: " + userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
            if (aiMessage.hasToolExecutionRequests()) {
                return null;
            }
            return "AI: " + aiMessage.text();
        } else {
            return null;
        }
    }

}
