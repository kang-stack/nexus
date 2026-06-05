package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbEmotionPattern;
import cn.cloudcat.user.common.mapper.KbEmotionPatternMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 情绪检测词库缓存服务。
 * <p>
 * 从 acc_user_kb_emotion_pattern 表加载启用词条，按 priority 升序，
 * 供 {@code EmotionDetectionService} 使用。
 * load/reload 模式与 ChitchatInterceptService 保持一致。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class EmotionPatternService {

    private final KbEmotionPatternMapper mapper;
    private final AtomicReference<List<KbEmotionPattern>> cache = new AtomicReference<>();

    public EmotionPatternService(KbEmotionPatternMapper mapper) {
        this.mapper = mapper;
    }

    /** 获取缓存的启用词条（按 priority 升序） */
    public List<KbEmotionPattern> loadEnabled() {
        List<KbEmotionPattern> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        List<KbEmotionPattern> fresh = mapper.selectList(
                new LambdaQueryWrapper<KbEmotionPattern>()
                        .eq(KbEmotionPattern::getEnabled, 1)
                        .orderByAsc(KbEmotionPattern::getPriority));
        cache.set(fresh);
        log.info("EmotionPatternService loaded {} patterns", fresh.size());
        return fresh;
    }

    /** 清除缓存，下次调用时重新加载 */
    public void reload() {
        cache.set(null);
    }

    /** 获取所有词条（供管理后台使用） */
    public List<KbEmotionPattern> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<KbEmotionPattern>()
                .orderByAsc(KbEmotionPattern::getEmotionTag)
                .orderByAsc(KbEmotionPattern::getPriority));
    }

    /** 新增词条并清除缓存 */
    public KbEmotionPattern create(KbEmotionPattern pattern) {
        mapper.insert(pattern);
        reload();
        return pattern;
    }

    /** 更新词条并清除缓存 */
    public KbEmotionPattern update(Long id, KbEmotionPattern update) {
        update.setId(id);
        mapper.updateById(update);
        reload();
        return mapper.selectById(id);
    }

    /** 删除词条并清除缓存 */
    public void delete(Long id) {
        mapper.deleteById(id);
        reload();
    }
}
