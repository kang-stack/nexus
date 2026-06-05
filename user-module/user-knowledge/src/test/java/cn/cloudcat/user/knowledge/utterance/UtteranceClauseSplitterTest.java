package cn.cloudcat.user.knowledge.utterance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UtteranceClauseSplitterTest {

    @Test
    void splitsMixedCompoundSentence() {
        List<String> clauses = UtteranceConnectorService.UtteranceClauseSplitter.split(
                "你好在吗，网络太卡了，顺便怎么充值",
                UtteranceConnectorService.DEFAULT_CONNECTORS);
        assertTrue(clauses.stream().anyMatch(c -> c.contains("网络")));
        assertTrue(clauses.stream().anyMatch(c -> c.contains("充值")));
    }

    @Test
    void splitsCustomConnector() {
        List<String> clauses = UtteranceConnectorService.UtteranceClauseSplitter.split(
                "网速慢 顺带问下套餐",
                List.of("顺带问下"));
        assertTrue(clauses.size() >= 2);
    }
}
