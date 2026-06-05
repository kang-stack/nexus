package cn.cloudcat.user.knowledge.utterance;

import cn.cloudcat.user.common.entity.KbUtteranceConnector;
import cn.cloudcat.user.common.mapper.KbUtteranceConnectorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 从 acc_user_kb_utterance_connector 加载分句连接词，供话语理解切分子句。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UtteranceConnectorService {

    static final List<String> DEFAULT_CONNECTORS = List.of(
            "还有", "顺便", "另外", "以及", "同时", "然后", "再问一下", "想问");

    private static final Pattern PUNCT_SPLIT = Pattern.compile("[，,。；;!?！？\\n]+");

    private final KbUtteranceConnectorMapper connectorMapper;
    private final AtomicReference<List<String>> cachedConnectors = new AtomicReference<>();

    public List<String> splitClauses(String userText) {
        return UtteranceClauseSplitter.split(userText, loadConnectors());
    }

    public void reload() {
        cachedConnectors.set(null);
        log.info("utterance connectors cache cleared");
    }

    private List<String> loadConnectors() {
        List<String> hit = cachedConnectors.get();
        if (hit != null) {
            return hit;
        }
        synchronized (this) {
            hit = cachedConnectors.get();
            if (hit != null) {
                return hit;
            }
            List<KbUtteranceConnector> rows = connectorMapper.selectList(
                    new LambdaQueryWrapper<KbUtteranceConnector>()
                            .eq(KbUtteranceConnector::getEnabled, 1)
                            .orderByAsc(KbUtteranceConnector::getSortOrder)
                            .orderByAsc(KbUtteranceConnector::getId));
            List<String> texts = rows.stream()
                    .map(KbUtteranceConnector::getConnectorText)
                    .filter(t -> t != null && t.length() >= 2)
                    .distinct()
                    .toList();
            hit = texts.isEmpty() ? DEFAULT_CONNECTORS : texts;
            cachedConnectors.set(hit);
            log.info("utterance connectors loaded count={}", hit.size());
            return hit;
        }
    }

    /**
     * 分句：标点 + 运营配置连接词。
     */
    static final class UtteranceClauseSplitter {

        private UtteranceClauseSplitter() {
        }

        static List<String> split(String userText, List<String> connectors) {
            if (userText == null || userText.isBlank()) {
                return List.of();
            }
            Pattern splitter = buildSplitter(connectors);
            String[] parts = splitter.split(userText.trim());
            List<String> clauses = new java.util.ArrayList<>();
            for (String part : parts) {
                String t = part.trim();
                if (t.length() >= 2) {
                    clauses.add(t);
                }
            }
            if (clauses.isEmpty()) {
                clauses.add(userText.trim());
            }
            return clauses;
        }

        static Pattern buildSplitter(List<String> connectors) {
            StringBuilder sb = new StringBuilder(PUNCT_SPLIT.pattern());
            if (connectors != null && !connectors.isEmpty()) {
                sb.append("|(?:");
                for (int i = 0; i < connectors.size(); i++) {
                    if (i > 0) {
                        sb.append('|');
                    }
                    sb.append(Pattern.quote(connectors.get(i)));
                }
                sb.append(')');
            }
            return Pattern.compile(sb.toString());
        }
    }
}
