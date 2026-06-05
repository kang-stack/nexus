package cn.cloudcat.user.message.agent.config;

import cn.cloudcat.user.common.constant.FastModelTask;
import cn.cloudcat.user.message.ai.service.CommonChatFlashService;
import cn.cloudcat.user.message.ai.service.CommonChatService;
import cn.cloudcat.user.message.ai.service.FastModelRoutingService;
import cn.cloudcat.user.message.ai.service.IntentRecognitionService;
import cn.cloudcat.user.message.ai.service.ModelRoutingService;
import cn.cloudcat.user.message.ai.service.MultiIntentRecognitionService;
import cn.cloudcat.user.message.chat.memory.UserChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    public MultiIntentRecognitionService multiIntentRecognitionService(
            FastModelRoutingService fastModelRoutingService,
            UserChatMemoryStore memoryStore) {
        return AiServices.builder(MultiIntentRecognitionService.class)
                .chatModel(fastModelRoutingService.getChatModel(FastModelTask.INTENT_RECOGNITION))
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(10)
                        .chatMemoryStore(memoryStore).build())
                .build();
    }

    @Bean
    public IntentRecognitionService intentRecognitionService(
            FastModelRoutingService fastModelRoutingService,
            UserChatMemoryStore memoryStore) {
        return AiServices.builder(IntentRecognitionService.class)
                .chatModel(fastModelRoutingService.getChatModel(FastModelTask.INTENT_RECOGNITION))
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(10)
                        .chatMemoryStore(memoryStore).build())
                .build();
    }

    @Bean
    public CommonChatFlashService commonChatFlashService(
            FastModelRoutingService fastModelRoutingService,
            UserChatMemoryStore memoryStore) {
        return AiServices.builder(CommonChatFlashService.class)
                .streamingChatModel(fastModelRoutingService.getStreamingChatModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(10)
                        .chatMemoryStore(memoryStore).build())
                .build();
    }

    @Bean
    public CommonChatService commonChatService(
            ModelRoutingService modelRoutingService,
            UserChatMemoryStore memoryStore) {
        return AiServices.builder(CommonChatService.class)
                .streamingChatModel(modelRoutingService.getStreamingChatModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(10)
                        .chatMemoryStore(memoryStore).build())
                .build();
    }
}
