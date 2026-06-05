package cn.cloudcat.user.knowledge.engine;

import cn.cloudcat.user.common.entity.KbFaqAlias;
import cn.cloudcat.user.common.mapper.KbFaqAliasMapper;
import cn.cloudcat.user.knowledge.chitchat.TextNormalize;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * acc_user_kb_faq_alias 用户说法 → 意图，短路 LLM 意图识别（含原 intent_keyword_rule，已合并为别名行）。
 * <p>
 * 复合句 FastPath：扫描全部启用别名，按在文本中的出现位置排序去重 intent_code。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class FaqAliasMatchService {

    private final KbFaqAliasMapper aliasMapper;
    private final AtomicReference<List<KbFaqAlias>> cache = new AtomicReference<>();

    public FaqAliasMatchService(KbFaqAliasMapper aliasMapper) {
        this.aliasMapper = aliasMapper;
    }

    /** 尝试通过 FAQ 别名匹配意图，命中返回 intentCode，未命中返回 empty（等同 {@link #matchAllIntents} 的首项） */
    public Optional<String> matchIntent(String userText) {
        List<String> all = matchAllIntents(userText);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * 扫描用户输入中所有命中的 FAQ 别名，按别名在文本中的出现位置排序，去重 intent_code。
     * 用于「怎么实名 怎么充值」等复合问句 FastPath，最多由调用方截断为 2 个意图。
     */
    public List<String> matchAllIntents(String userText) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        String normalized = TextNormalize.normalize(userText);
        List<IntentHit> hits = new ArrayList<>();
        collectFaqAliasHits(normalized, hits);
        if (hits.isEmpty()) {
            return List.of();
        }
        hits.sort(Comparator.comparingInt(IntentHit::position).thenComparing(IntentHit::aliasText));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (IntentHit hit : hits) {
            ordered.add(hit.intentCode());
        }
        List<String> result = new ArrayList<>(ordered);
        if (result.size() == 1) {
            log.info("intent fast-path hit alias={} intent={}", hits.get(0).aliasText(), result.get(0));
        } else {
            log.info("intent fast-path compound intents={} text=\"{}\"", result, abbreviate(userText));
        }
        return result;
    }

    private void collectFaqAliasHits(String normalized, List<IntentHit> hits) {
        for (KbFaqAlias alias : loadAliases()) {
            if (alias.getIntentCode() == null || alias.getIntentCode().isBlank()) {
                continue;
            }
            int pos = matchPosition(normalized, alias);
            if (pos >= Integer.MAX_VALUE) {
                continue;
            }
            hits.add(new IntentHit(pos, alias.getIntentCode(), alias.getAliasText()));
        }
    }

    private static String abbreviate(String text) {
        String t = text.replace('\n', ' ').trim();
        return t.length() <= 40 ? t : t.substring(0, 37) + "...";
    }

    private record IntentHit(int position, String intentCode, String aliasText) {}

    /** 置空缓存，下次查询时重新从数据库加载 */
    public void reload() {
        cache.set(null);
    }

    /** 从缓存或数据库加载已启用的 FAQ 别名列表 */
    private List<KbFaqAlias> loadAliases() {
        List<KbFaqAlias> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        List<KbFaqAlias> fresh = aliasMapper.selectList(new LambdaQueryWrapper<KbFaqAlias>()
                .eq(KbFaqAlias::getEnabled, 1)
                .orderByAsc(KbFaqAlias::getId));
        cache.set(fresh);
        return fresh;
    }

    /** 别名在用户文本中的起始位置，未命中返回 {@link Integer#MAX_VALUE} */
    private int matchPosition(String normalized, KbFaqAlias alias) {
        String aliasText = alias.getAliasText();
        if (aliasText == null || aliasText.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String type = alias.getMatchType() != null ? alias.getMatchType() : "EXACT";
        String aliasNorm = TextNormalize.normalize(aliasText);
        return switch (type) {
            case "CONTAINS" -> {
                int idx = normalized.indexOf(aliasNorm);
                yield idx >= 0 ? idx : Integer.MAX_VALUE;
            }
            case "REGEX" -> {
                try {
                    Matcher matcher = Pattern.compile(aliasText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(normalized);
                    yield matcher.find() ? matcher.start() : Integer.MAX_VALUE;
                } catch (PatternSyntaxException e) {
                    log.warn("invalid faq alias REGEX id={} expr={}", alias.getId(), aliasText);
                    yield Integer.MAX_VALUE;
                }
            }
            default -> normalized.equals(aliasNorm) ? 0 : Integer.MAX_VALUE;
        };
    }
}
