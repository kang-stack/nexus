package cn.cloudcat.ai.spi;

public record RagRequest(
        String conversationId,
        String query,
        String diagnosisSummary,
        boolean allowActions
) {
}
