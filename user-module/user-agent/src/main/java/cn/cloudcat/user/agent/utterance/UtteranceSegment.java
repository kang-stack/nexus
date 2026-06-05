package cn.cloudcat.user.agent.utterance;

/**
 * 用户输入中的一个子句片段。
 */
public record UtteranceSegment(
        SegmentType type,
        String text,
        String intentCode) {

    public enum SegmentType {
        CHITCHAT,
        BUSINESS
    }
}
