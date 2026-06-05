package cn.cloudcat.user.message.ai.service;

import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 绉诲姩绔鍚堟剰鍥捐瘑鍒紙澶氭爣绛?+ 瀛愬彞 span锛夈€? *
 * @author Kang
 * @since 2026-06-02
 */
public interface MultiIntentRecognitionService {

    @SystemMessage(fromResource = "prompts/intent-recognition-multi-prompt.txt")
    IntentRecognitionResult chat(@MemoryId String conversationId, @UserMessage String userMessage);
}
