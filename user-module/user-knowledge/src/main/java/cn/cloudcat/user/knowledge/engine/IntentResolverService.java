package cn.cloudcat.user.knowledge.engine;

import cn.cloudcat.user.common.entity.KnowledgeIntent;
import cn.cloudcat.user.common.mapper.KnowledgeIntentMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 LLM 标签 / 快捷入口 / 英文遗留码 解析为 canonical intent_code。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class IntentResolverService {

    private final KnowledgeIntentMapper intentMapper;
    private volatile Map<String, String> aliasToCode = Map.of();

    public IntentResolverService(KnowledgeIntentMapper intentMapper) {
        this.intentMapper = intentMapper;
    }

    /**
     * 将原始意图标签解析为 canonical intent_code。
     * 依次尝试精确匹配、小写匹配，均未匹配时返回原始值。
     */
    public String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ensureCache();
        String trimmed = raw.trim();
        // 先精确匹配
        String hit = aliasToCode.get(trimmed);
        if (hit != null) {
            return hit;
        }
        // 回退到小写匹配
        String lower = trimmed.toLowerCase();
        hit = aliasToCode.get(lower);
        return hit != null ? hit : trimmed;
    }

    /** DCL 懒加载别名映射表（alias → intentCode） */
    private void ensureCache() {
        if (!aliasToCode.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (!aliasToCode.isEmpty()) {
                return;
            }
            List<KnowledgeIntent> intents = intentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeIntent>().eq(KnowledgeIntent::getEnabled, 1));
            Map<String, String> map = new HashMap<>();
            for (KnowledgeIntent intent : intents) {
                String code = intent.getIntentCode();
                map.put(code, code);
                // 意图名称也作为别名
                if (intent.getIntentName() != null) {
                    map.put(intent.getIntentName(), code);
                }
                // 解析同义词 JSON 数组或逗号分隔字符串
                parseSynonyms(intent.getSynonyms()).forEach(a -> map.put(a, code));
            }
            aliasToCode = Map.copyOf(map);
            log.info("IntentResolver loaded {} entries for {} intents", map.size(), intents.size());
        }
    }

    /** 强制重建别名映射缓存 */
    public void reload() {
        aliasToCode = Map.of();
        ensureCache();
    }

    /** 解析同义词：支持 JSON 数组格式和逗号分隔格式 */
    private static List<String> parseSynonyms(String synonyms) {
        if (synonyms == null || synonyms.isBlank()) {
            return List.of();
        }
        if (synonyms.trim().startsWith("[")) {
            try {
                JSONArray arr = JSON.parseArray(synonyms);
                return arr.toJavaList(String.class);
            } catch (Exception e) {
                return List.of();
            }
        }
        return java.util.Arrays.stream(synonyms.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
