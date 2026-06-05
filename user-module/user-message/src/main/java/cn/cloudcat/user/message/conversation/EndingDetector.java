package cn.cloudcat.user.message.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 对话结束语检测器（PRD 5.4.3）。
 *
 * 当 AI 回复内容包含结束标识词时，触发满意度评价流程。
 * 同时检测用户是否表达了"满意/感谢/确认"等结束信号。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class EndingDetector {

    // AI 回复中的结束标识词（规则引擎可配置 is_ending=1 标记为结束语）
    private static final Set<String> AI_ENDING_PHRASES = Set.of(
            "请问还有其他问题吗", "还有什么可以帮您", "祝您生活愉快",
            "感谢您的咨询", "欢迎随时联系", "本次服务到此结束",
            "请问还有其他需要吗", "很高兴为您服务"
    );

    // 用户结束信号（用于判断是否应结束会话）
    private static final Set<String> USER_ENDING_PHRASES = Set.of(
            "好的", "谢谢", "感谢", "明白了", "知道了",
            "没有问题了", "没问题", "可以了", "ok", "OK",
            "拜拜", "再见", "晚安"
    );

    /**
     * 检查 AI 回复文本是否包含结束标识词。
     */
    public boolean isAiEnding(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            return false;
        }
        for (String phrase : AI_ENDING_PHRASES) {
            if (aiContent.contains(phrase)) {
                log.info("EndingDetector: AI ending phrase matched: '{}'", phrase);
                return true;
            }
        }
        return false;
    }

    /**
     * 检查用户输入是否为结束信号。
     */
    public boolean isUserEnding(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        String trimmed = userInput.trim();
        // 短消息 + 结束词匹配
        if (trimmed.length() > 10) {
            return false;
        }
        for (String phrase : USER_ENDING_PHRASES) {
            if (trimmed.equalsIgnoreCase(phrase) || trimmed.contains(phrase)) {
                log.info("EndingDetector: user ending signal '{}'", trimmed);
                return true;
            }
        }
        return false;
    }

    /**
     * 组合判断：用户发送结束信号 + 上一轮 AI 回复包含结束语 → 应结束会话。
     */
    public boolean shouldEndConversation(String lastAiContent, String currentUserInput) {
        return isAiEnding(lastAiContent) && isUserEnding(currentUserInput);
    }
}
