package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.knowledge.entity.UserActionTemplate;
import cn.cloudcat.user.knowledge.mapper.UserActionTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统动作模板服务：管理 acc_user_action_template 表，提供动作成功消息的缓存查询。
 * <p>
 * 当系统自动执行某个 action（如开卡、充值）后，返回对应的成功提示文案。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@RequiredArgsConstructor
public class UserActionTemplateService {

    private final UserActionTemplateMapper templateMapper;
    /** 动作编码 → 成功消息缓存 */
    private volatile Map<String, String> messageCache = Map.of();

    /** 按动作编码查询成功消息，未命中则返回 fallback */
    public String resolveSuccessMessage(String actionCode, String fallback) {
        if (actionCode == null) {
            return fallback;
        }
        Map<String, String> cache = messageCache;
        String msg = cache.get(actionCode);
        if (msg != null) {
            return msg;
        }
        // 缓存未命中则重新加载
        reload();
        return messageCache.getOrDefault(actionCode, fallback);
    }

    /** 列出所有动作模板，按排序和 ID 升序 */
    public List<UserActionTemplate> listAll() {
        return templateMapper.selectList(new LambdaQueryWrapper<UserActionTemplate>()
                .orderByAsc(UserActionTemplate::getId));
    }

    /** 更新动作模板并刷新缓存 */
    public UserActionTemplate update(Long id, UserActionTemplate update) {
        update.setId(id);
        templateMapper.updateById(update);
        reload();
        return templateMapper.selectById(id);
    }

    /** 从数据库重新加载已启用的动作模板到缓存 */
    public void reload() {
        List<UserActionTemplate> rows = templateMapper.selectList(new LambdaQueryWrapper<UserActionTemplate>()
                .eq(UserActionTemplate::getEnabled, 1));
        Map<String, String> next = new ConcurrentHashMap<>();
        for (UserActionTemplate row : rows) {
            if (row.getActionCode() != null && row.getSuccessMessage() != null) {
                next.put(row.getActionCode(), row.getSuccessMessage());
            }
        }
        messageCache = Map.copyOf(next);
    }
}
