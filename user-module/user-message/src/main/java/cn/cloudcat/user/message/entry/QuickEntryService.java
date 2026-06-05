package cn.cloudcat.user.message.entry;

import cn.cloudcat.user.knowledge.service.KbSceneFaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 快捷入口服务
 * <p>
 * 提供前端会话页面的快捷入口列表（如"网络问题""其他问题"等）。
 * 优先从知识库场景 FAQ 配置加载，无配置时使用内置兜底入口。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@RequiredArgsConstructor
public class QuickEntryService {

    private final KbSceneFaqService sceneFaqService;

    /** 获取快捷入口列表，数据库无配置时使用内置兜底 */
    public List<Map<String, String>> listEntries() {
        List<Map<String, String>> entries = sceneFaqService.listEnabledEntries();
        if (entries.isEmpty()) {
            return fallbackEntries();
        }
        return entries;
    }

    /** 内置兜底快捷入口 */
    private static List<Map<String, String>> fallbackEntries() {
        return List.of(
                entry("网络问题", "网慢/网卡/无网络", "🐢", "网络很慢，上网卡顿"),
                entry("其他问题", "其他服务", "💬", "还有其他问题想咨询")
        );
    }

    /** 构建单条快捷入口数据 */
    private static Map<String, String> entry(String code, String label, String icon, String message) {
        return Map.of(
                "intentCode", code,
                "label", label,
                "icon", icon,
                "entryMessage", message
        );
    }
}
