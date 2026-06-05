package cn.cloudcat.user.message.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface CommonChatService {

    @SystemMessage("你是智能客服，请回答用户的问题。")
    Flux<String> streamChat(@MemoryId String userId, @UserMessage String message);
}
