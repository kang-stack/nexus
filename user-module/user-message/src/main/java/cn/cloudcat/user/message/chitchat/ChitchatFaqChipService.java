package cn.cloudcat.user.message.chitchat;

import cn.cloudcat.user.message.entry.QuickEntryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 寒暄回复 FAQ 快捷芯片（复用一键服务入口数据）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class ChitchatFaqChipService {

    private final QuickEntryService quickEntryService;

    public ChitchatFaqChipService(QuickEntryService quickEntryService) {
        this.quickEntryService = quickEntryService;
    }

    public List<Map<String, Object>> buildChips() {
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, String> entry : quickEntryService.listEntries()) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("text", entry.getOrDefault("label", entry.get("intentCode")));
            link.put("type", "intent");
            link.put("intentCode", entry.get("intentCode"));
            link.put("message", entry.getOrDefault("entryMessage", entry.get("label")));
            links.add(link);
        }
        return links;
    }
}
