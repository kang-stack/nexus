package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbHumanRoute;
import cn.cloudcat.user.common.mapper.KbHumanRouteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 人工路由服务：根据意图匹配关键词确定转人工的目标渠道。
 * <p>
 * 从 acc_user_kb_human_route 表加载路由规则，按优先级匹配 intentMatch 中的逗号分隔关键词。
 * 未匹配时返回默认渠道。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@RequiredArgsConstructor
public class KbHumanRouteService {

    /** 默认人工客服渠道 */
    private static final String DEFAULT_CHANNEL = "/human/chat/default";

    private final KbHumanRouteMapper humanRouteMapper;
    /** 路由规则缓存 */
    private final AtomicReference<List<KbHumanRoute>> cache = new AtomicReference<>();

    /** 根据意图匹配人工路由渠道，未命中返回默认渠道 */
    public String resolveChannel(String intent) {
        if (intent == null || intent.isBlank()) {
            return DEFAULT_CHANNEL;
        }
        String upper = intent.toUpperCase();
        for (KbHumanRoute route : loadRoutes()) {
            if (route.getIntentMatch() == null) {
                continue;
            }
            // intentMatch 为逗号分隔的关键词列表，任意匹配即命中
            for (String token : route.getIntentMatch().split(",")) {
                String t = token.trim();
                if (!t.isEmpty() && upper.contains(t.toUpperCase())) {
                    // channelPath 可能因数据异常为 null，防御性回退到默认渠道
                    String path = route.getChannelPath();
                    return (path != null && !path.isBlank()) ? path : DEFAULT_CHANNEL;
                }
            }
        }
        return DEFAULT_CHANNEL;
    }

    /** 列出所有人工路由规则 */
    public List<KbHumanRoute> listAll() {
        return humanRouteMapper.selectList(new LambdaQueryWrapper<KbHumanRoute>()
                .orderByAsc(KbHumanRoute::getPriority)
                .orderByAsc(KbHumanRoute::getId));
    }

    /** 更新路由规则并刷新缓存 */
    public KbHumanRoute update(Long id, KbHumanRoute update) {
        update.setId(id);
        humanRouteMapper.updateById(update);
        reload();
        return humanRouteMapper.selectById(id);
    }

    /** 置空缓存，下次查询时重新加载 */
    public void reload() {
        cache.set(null);
    }

    /** 从数据库加载已启用的路由规则，按优先级排序 */
    private List<KbHumanRoute> loadRoutes() {
        List<KbHumanRoute> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        List<KbHumanRoute> fresh = humanRouteMapper.selectList(new LambdaQueryWrapper<KbHumanRoute>()
                .eq(KbHumanRoute::getEnabled, 1)
                .orderByAsc(KbHumanRoute::getPriority));
        cache.set(fresh);
        return fresh;
    }
}
