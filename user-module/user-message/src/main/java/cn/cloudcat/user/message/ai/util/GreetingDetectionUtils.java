package cn.cloudcat.user.message.ai.util;

import java.util.List;

/**
 * 纯问候语轻量检测，避免对「你好」等消息发起多余 LLM 调用。
 */
public final class GreetingDetectionUtils {

    private static final List<String> GREETINGS = List.of(
            "good morning", "good evening", "good afternoon", "good night",
            "早上好", "下午好", "中午好", "早上好",
            "你好呀", "你好啊", "您好呀", "您好啊",
            "你好", "您好", "哈喽", "嗨", "hello", "halo",
            "有人吗", "在不在", "在吗", "在么", "在嘛",
            "晚安", "早安", "好的",
            "嗯", "哦", "呃", "额", "哈", "呵", "嘿", "哟",
            "okay", "okk", "ok", "hi", "hey", "yo", "hola", "howdy"
    );

    private static final List<String> FILLERS = List.of(
            "请问", "我想问", "麻烦问", "想问", "帮这", "一下",
            "你", "您", "我", "他", "好", "的",
            "在", "的", "了", "是", "吗", "呢", "吧", "呀", "啊", "哦", "吗",
            "请", "问", "一", "下", "能", "可以", "有", "没"
    );

    private GreetingDetectionUtils() {
    }

    public static boolean isPureGreeting(String message) {
        if (message == null || message.isBlank()) {
            return true;
        }
        String cleaned = message.replaceAll("[\\p{P}\\p{Z}\\n\\r]", "").toLowerCase().strip();
        if (cleaned.isEmpty()) {
            return true;
        }
        for (String g : GREETINGS) {
            cleaned = cleaned.replace(g, "");
        }
        for (String f : FILLERS) {
            cleaned = cleaned.replace(f, "");
        }
        return cleaned.length() <= 2;
    }
}
