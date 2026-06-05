package cn.cloudcat.ai.spi;

import java.util.List;

public record IntentRequest(
        String conversationId,
        String text,
        List<String> candidateIntents
) {
}
