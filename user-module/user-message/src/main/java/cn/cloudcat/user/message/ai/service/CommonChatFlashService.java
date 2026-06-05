package cn.cloudcat.user.message.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface CommonChatFlashService {

    @SystemMessage("你是随身 WiFi 智能客服，请简短回应用户并引导其描述具体问题。")
    Flux<String> streamChat(@MemoryId String userId, @UserMessage String message);
}
