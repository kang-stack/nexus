package cn.cloudcat.user.message.ai.service;

import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 意图识别服务
 *
 * @author Kang
 */
public interface IntentRecognitionService {

    @SystemMessage(fromResource = "prompts/intent-recognition-new-prompt.txt")
    IntentRecognitionResult chat(@MemoryId String conversationId, @UserMessage String userMessage);
}
