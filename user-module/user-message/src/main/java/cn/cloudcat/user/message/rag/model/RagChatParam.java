package cn.cloudcat.user.message.rag.model;

import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;

public record RagChatParam(
        String userId,
        String conversationId,
        String messageId,
        String content,
        String assistantMessageId,
        IntentRecognitionResult intentRecognitionResult
) {
}
