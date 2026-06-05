package cn.cloudcat.user.message.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface WifiChatAiService {

    public Flux<String> streamChat(@MemoryId String conversationId, @UserMessage String message);

    public String chat(@MemoryId String conversationId, @UserMessage String message);

}
