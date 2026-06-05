package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbSceneFaq;
import cn.cloudcat.user.common.mapper.KbSceneFaqMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 场景 FAQ 服务：管理 acc_user_kb_scene_faq 表，提供快捷问题入口的查询与维护。
 * 数据从数据库加载并缓存，增删改操作自动刷新缓存。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@RequiredArgsConstructor
public class KbSceneFaqService {

    private final KbSceneFaqMapper sceneFaqMapper;
    /** 全量数据缓存 */
    private final AtomicReference<List<KbSceneFaq>> cache = new AtomicReference<>();

    /** 列出已启用的快捷问题入口（前端展示用） */
    public List<Map<String, String>> listEnabledEntries() {
        return loadCached().stream()
                .filter(row -> row.getEnabled() != null && row.getEnabled() == 1)
                .sorted(Comparator.comparingInt(r -> r.getSortOrder() != null ? r.getSortOrder() : 0))
                .map(this::toEntryMap)
                .toList();
    }

    /** 列出所有场景 FAQ（含禁用项） */
    public List<KbSceneFaq> listAll() {
        return sceneFaqMapper.selectList(new LambdaQueryWrapper<KbSceneFaq>()
                .orderByAsc(KbSceneFaq::getSortOrder)
                .orderByAsc(KbSceneFaq::getId));
    }

    /** 创建场景 FAQ，自动填充默认值 */
    public KbSceneFaq create(KbSceneFaq row) {
        if (row.getEnabled() == null) {
            row.setEnabled(1);
        }
        if (row.getSortOrder() == null) {
            row.setSortOrder(0);
        }
        sceneFaqMapper.insert(row);
        reload();
        return row;
    }

    /** 更新场景 FAQ 并刷新缓存 */
    public KbSceneFaq update(Long id, KbSceneFaq update) {
        update.setId(id);
        sceneFaqMapper.updateById(update);
        reload();
        return sceneFaqMapper.selectById(id);
    }

    /** 置空缓存，下次查询时重新加载 */
    public void reload() {
        cache.set(null);
    }

    /** 从缓存或数据库加载全量数据 */
    private List<KbSceneFaq> loadCached() {
        List<KbSceneFaq> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        List<KbSceneFaq> fresh = listAll();
        cache.set(fresh);
        return fresh;
    }

    /** 将实体转为前端所需 Map 格式（含 icon 默认值） */
    private Map<String, String> toEntryMap(KbSceneFaq row) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("intentCode", row.getIntentCode());
        map.put("label", row.getLabel());
        map.put("icon", row.getIcon() != null ? row.getIcon() : "💬");
        map.put("entryMessage", row.getEntryMessage());
        return map;
    }
}
