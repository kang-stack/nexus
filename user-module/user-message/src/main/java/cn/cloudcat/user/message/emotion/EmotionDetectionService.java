package cn.cloudcat.user.message.emotion;

import cn.cloudcat.user.common.constant.EmotionTag;
import cn.cloudcat.user.common.constant.RiskLevel;
import cn.cloudcat.user.common.entity.KbEmotionPattern;
import cn.cloudcat.user.knowledge.service.EmotionPatternService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 用户情绪检测服务 — 基于 acc_user_kb_emotion_pattern 词库 + 标点模式，无需 LLM。
 * <p>
 * <b>检测优先级（先命中先胜出）：</b>
 * <ol>
 *   <li>ABUSE（辱骂）→ HIGH / 自动安抚 + 转人工建议</li>
 *   <li>THREATEN（威胁）→ HIGH / 转人工建议</li>
 *   <li>ANGRY（愤怒）→ MEDIUM / 转人工建议</li>
 *   <li>URGENT（急迫）→ MEDIUM / 转人工建议</li>
 *   <li>SAD（沮丧）→ LOW / 安抚不转人工</li>
 *   <li>标点情绪（!!!! / ????）→ MEDIUM/LOW</li>
 *   <li>未命中 → NEUTRAL / LOW</li>
 * </ol>
 * <p>
 * 词库从 acc_user_kb_emotion_pattern 表加载（通过 {@link EmotionPatternService} 缓存），
 * 运营可通过管理后台实时增删改，无需重启服务。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class EmotionDetectionService {

    private static final Pattern EXCLAMATION_BURST = Pattern.compile("[!！?？]{2,}");

    /** 默认 ABUSE 安抚话术（词条未配置 soothe_hint 时使用） */
    private static final String DEFAULT_ABUSE_SOOTHE =
            "非常理解您的心情，我们会认真对待。如需人工协助，可点击下方转接客服~";

    /** 默认 URGENT 安抚话术 */
    private static final String DEFAULT_URGENT_SOOTHE =
            "非常理解您的急切心情，我正在尽最大努力帮您处理~";

    /** REGEX 模式编译缓存，避免每次匹配都重新编译 */
    private final ConcurrentHashMap<String, Pattern> regexCache = new ConcurrentHashMap<>();

    private final EmotionPatternService patternService;

    public EmotionDetectionService(EmotionPatternService patternService) {
        this.patternService = patternService;
    }

    /**
     * 情绪检测结果（不可变记录）。
     *
     * @param tag           情绪标签枚举
     * @param riskLevel     风险等级枚举
     * @param suggestHuman  是否建议转人工客服
     * @param sootheHint    安抚话术（NEUTRAL 时为 null）
     */
    public record EmotionResult(EmotionTag tag, RiskLevel riskLevel, boolean suggestHuman, String sootheHint) {
        public static EmotionResult neutral() {
            return new EmotionResult(EmotionTag.NEUTRAL, RiskLevel.LOW, false, null);
        }
    }

    /**
     * 基于 acc_user_kb_emotion_pattern 词库 + 标点模式检测用户情绪。
     * <p>
     * <b>匹配流程：</b>
     * <ol>
     *   <li>加载启用词条（EmotionPatternService 提供缓存）</li>
     *   <li>按 priority 升序遍历：CONTAINS 子串匹配 / REGEX 正则匹配</li>
     *   <li>命中后取词条的 risk_level + suggest_human + soothe_hint</li>
     *   <li>若 soothe_hint 为空，按 emotion_tag 使用默认安抚话术</li>
     *   <li>全部未命中 → 检查标点情绪（!! / ??）→ 仍无则 NEUTRAL</li>
     * </ol>
     *
     * @param text 用户输入原文
     * @return EmotionResult 情绪检测结果
     */
    public EmotionResult detect(String text) {
        // Step 1: 输入校验
        if (text == null || text.isBlank()) {
            return EmotionResult.neutral();
        }
        String normalized = text.trim();

        // Step 2: 加载启用词条（EmotionPatternService 缓存，按 priority 升序）
        List<KbEmotionPattern> patterns = patternService.loadEnabled();

        // Step 3: 遍历词条匹配（高 priority 先命中先胜出）
        for (KbEmotionPattern pattern : patterns) {
            if (!matches(normalized, pattern)) {
                continue;
            }
            // 命中 → 构建 EmotionResult
            EmotionTag tag = EmotionTag.fromTag(pattern.getEmotionTag());
            RiskLevel risk = RiskLevel.fromLevel(pattern.getRiskLevel());
            boolean suggestHuman = pattern.getSuggestHuman() != null && pattern.getSuggestHuman() == 1;
            String sootheHint = resolveSootheHint(pattern);
            return new EmotionResult(tag, risk, suggestHuman, sootheHint);
        }

        // Step 4: 词库未命中 → 标点情绪兜底（!! / ??）
        if (EXCLAMATION_BURST.matcher(normalized).find()) {
            return new EmotionResult(EmotionTag.ANGRY, RiskLevel.MEDIUM, false, null);
        }

        // Step 5: 完全未命中 → 中性
        return EmotionResult.neutral();
    }

    /**
     * 按匹配类型判断用户文本是否命中情绪词条。
     *
     * @param normalized 用户输入（已 trim）
     * @param pattern    情绪词条（含匹配类型和关键词/正则）
     * @return 是否命中
     */
    private boolean matches(String normalized, KbEmotionPattern pattern) {
        String p = pattern.getPattern();
        if (p == null || p.isBlank()) {
            return false;
        }
        String type = pattern.getMatchType() != null ? pattern.getMatchType() : "CONTAINS";
        return switch (type) {
            case "REGEX" -> {
                try {
                    Pattern compiled = regexCache.computeIfAbsent(p,
                            k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                    yield compiled.matcher(normalized).find();
                } catch (PatternSyntaxException e) {
                    yield false;
                }
            }
            default -> normalized.toLowerCase().contains(p.toLowerCase()); // CONTAINS：忽略大小写
        };
    }

    /**
     * 解析安抚话术：词条自身配置优先，其次按 emotion_tag 取默认模板。
     */
    private String resolveSootheHint(KbEmotionPattern pattern) {
        if (pattern.getSootheHint() != null && !pattern.getSootheHint().isBlank()) {
            return pattern.getSootheHint();
        }
        EmotionTag tag = EmotionTag.fromTag(pattern.getEmotionTag());
        if (tag == null) {
            return null; // 无标签无安抚话术，仅标记
        }
        return switch (tag) {
            case ABUSE, THREATEN -> DEFAULT_ABUSE_SOOTHE;
            case URGENT -> DEFAULT_URGENT_SOOTHE;
            default -> null; // ANGRY / SAD 无默认安抚话术，仅标记
        };
    }
}
