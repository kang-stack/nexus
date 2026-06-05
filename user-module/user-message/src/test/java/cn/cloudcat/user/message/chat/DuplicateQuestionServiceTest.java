package cn.cloudcat.user.message.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateQuestionServiceTest {

    @Test
    void similarityDetectsExactDuplicate() {
        double score = DuplicateQuestionService.similarity(
                "网络很慢怎么办",
                "网络很慢怎么办");
        assertEquals(1.0, score, 0.01);
    }

    @Test
    void similarityLowForDifferentQuestions() {
        double score = DuplicateQuestionService.similarity("怎么充值", "设备离线");
        assertTrue(score < 0.5);
    }
}
