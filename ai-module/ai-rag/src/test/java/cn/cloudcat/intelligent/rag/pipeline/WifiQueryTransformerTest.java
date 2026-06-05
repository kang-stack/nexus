package cn.cloudcat.intelligent.rag.pipeline;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Kang
 * @since 2026-05-28
 */
class WifiQueryTransformerTest {

    private TestableTransformer transformer;

    static class TestableTransformer extends WifiQueryTransformer {
        TestableTransformer(ChatModel chatModel, PromptTemplate promptTemplate) {
            super(chatModel, promptTemplate, null, null);
        }

        @Override
        public String format(List<ChatMessage> chatMemory) {
            return super.format(chatMemory);
        }

        @Override
        public String format(ChatMessage message) {
            return super.format(message);
        }

        @Override
        public dev.langchain4j.model.input.Prompt createPrompt(Query query, String chatMemory) {
            return super.createPrompt(query, chatMemory);
        }
    }

    @BeforeEach
    void setUp() {
        PromptTemplate template = PromptTemplate.from("Query: {{query}}\nHistory: {{chatMemory}}");
        transformer = new TestableTransformer(mock(ChatModel.class), template);
    }

    @Test
    void shouldFormatUserMessage() {
        String result = transformer.format(UserMessage.from("我的设备连不上网"));

        assertThat(result).isEqualTo("User: 我的设备连不上网");
    }

    @Test
    void shouldFormatAiMessage() {
        String result = transformer.format(AiMessage.from("请尝试重启设备"));

        assertThat(result).isEqualTo("AI: 请尝试重启设备");
    }

    @Test
    void shouldReturnNullForAiMessageWithToolExecutionRequests() {
        ToolExecutionRequest toolRequest = mock(ToolExecutionRequest.class);
        String result = transformer.format(AiMessage.from(List.of(toolRequest)));

        assertThat(result).isNull();
    }

    @Test
    void shouldFormatMultipleMessages() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("你好"),
                UserMessage.from("设备连不上网")
        );

        String result = transformer.format(messages);

        assertThat(result).contains("User: hello");
        assertThat(result).contains("AI: 你好");
        assertThat(result).contains("User: 设备连不上网");
    }

    @Test
    void shouldSkipToolExecutionMessagesInFormat() {
        ToolExecutionRequest toolRequest = mock(ToolExecutionRequest.class);
        List<ChatMessage> messages = List.of(
                UserMessage.from("查询套餐"),
                AiMessage.from(List.of(toolRequest)), // tool call — should be skipped
                AiMessage.from("您的套餐是...")
        );

        String result = transformer.format(messages);

        assertThat(result).doesNotContain("tool");
        assertThat(result).contains("User: 查询套餐");
        assertThat(result).contains("AI: 您的套餐是...");
    }

    @Test
    void shouldCreatePromptWithQueryAndChatMemory() {
        Query query = Query.from("设备无法上网");
        String chatMemory = "User: 之前问过\nAI: 之前回答过";

        dev.langchain4j.model.input.Prompt prompt = transformer.createPrompt(query, chatMemory);

        assertThat(prompt.text()).contains("设备无法上网");
        assertThat(prompt.text()).contains("之前问过");
        assertThat(prompt.text()).contains("之前回答过");
    }

    @Test
    void shouldCreatePromptWithEmptyChatMemory() {
        Query query = Query.from("新问题");

        dev.langchain4j.model.input.Prompt prompt = transformer.createPrompt(query, "");

        assertThat(prompt.text()).contains("新问题");
    }

    @Test
    void shouldHandleEmptyMessageList() {
        String result = transformer.format(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFallbackToOriginalQueryWhenLlmFails() {
        ChatModel failingModel = mock(ChatModel.class);
        when(failingModel.chat(any(String.class))).thenThrow(new RuntimeException("HTTP connect timed out"));
        WifiQueryTransformer failingTransformer = new WifiQueryTransformer(failingModel, null);

        Query query = Query.from("怎么实名认证；设备诊断：NOT_ACTIVATED");
        var result = failingTransformer.transform(query);

        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().text()).contains("怎么实名认证");
        assertThat(result.iterator().next().text()).contains("NOT_ACTIVATED");
    }
}
