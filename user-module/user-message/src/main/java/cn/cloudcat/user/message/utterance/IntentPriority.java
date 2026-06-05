package cn.cloudcat.user.message.utterance;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 业务意图优先级（与多意图 LLM prompt 一致）。
 */
public final class IntentPriority {

    private static final Map<String, Integer> ORDER = Map.ofEntries(
            Map.entry("人工客服", 0),
            Map.entry("网络问题", 1),
            Map.entry("设备问题", 2),
            Map.entry("退款问题", 3),
            Map.entry("充值问题", 4),
            Map.entry("实名认证问题", 5),
            Map.entry("实名注销问题", 6),
            Map.entry("开票问题", 7),
            Map.entry("其他问题", 8)
    );

    private IntentPriority() {
    }

    public static int rank(String intentCode) {
        if (intentCode == null) {
            return 99;
        }
        return ORDER.getOrDefault(intentCode, 50);
    }

    public static List<String> sortByPriority(List<String> intents) {
        return intents.stream()
                .distinct()
                .sorted(Comparator.comparingInt(IntentPriority::rank))
                .toList();
    }

    public static String primaryOf(List<String> intents) {
        List<String> sorted = sortByPriority(intents);
        return sorted.isEmpty() ? null : sorted.get(0);
    }
}
