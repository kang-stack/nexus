package cn.cloudcat.user.knowledge.chitchat;

import cn.cloudcat.user.session.ConversationExt;
import cn.cloudcat.user.session.SessionPhaseResolver;
import cn.cloudcat.user.session.model.SessionPhase;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.common.entity.KbChitchatPattern;
import cn.cloudcat.user.common.entity.KbChitchatReply;
import cn.cloudcat.user.common.mapper.KbChitchatPatternMapper;
import cn.cloudcat.user.common.mapper.KbChitchatReplyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 寒暄拦截 FastPath 服务（V2 §5 完整实现）。
 * <p>
 * <b>设计目标：</b>"你好/在吗/谢谢" 等口语寒暄完全不走 LLM，避免 Token 成本与首响延迟（P99 &lt; 100ms）。
 * <p>
 * <b>匹配路由链（按顺序）：</b>
 * <ol>
 *   <li>文本归一化：全角→半角、大写→小写、空白折叠（{@link TextNormalize#normalize(String)}）</li>
 *   <li>EXACT 精确匹配：归一化后完全相等（如 "你好" == "你好"）</li>
 *   <li>CONTAINS 包含匹配：归一化后 contains 模式（长度≥2，如 "在不在" contains "在"）</li>
 *   <li>REGEX 正则匹配：纯符号 / 长度≤1 / 无意义串（如 "^.{1,2}$" 兜底）</li>
 *   <li>未命中 → 返回 empty，后续走 IntentRecognition Skill 链</li>
 * </ol>
 * <p>
 * <b>会话阶段感知：</b>
 * <ul>
 *   <li>FIRST：session.turn_count==0 && chitchat_only_count==0（首次进入说[你好]）</li>
 *   <li>REPEAT：同 category 再次命中 && chitchat_only_count>0（60s 内重复"在吗"）</li>
 *   <li>MID：其他情况（对话中途探活 / 致谢）</li>
 * </ul>
 * <p>
 * <b>防刷升级策略（PRD §5.5）：</b>
 * <ul>
 *   <li>chitchat_only_count 1~2：正常寒暄回复</li>
 *   <li>chitchat_only_count ≥ 2：强追加 FAQ_CHIPS（仍不走 LLM）</li>
 *   <li>chitchat_only_count ≥ 4：升级提示[请描述您的设备问题] + FAQ_CHIPS</li>
 * </ul>
 * <p>
 * <b>限频：</b>Redis key {@code chitchat:rate:{convId}:{category}} TTL=60s，
 * 同会话同类寒暄 60s 内仅响应一次。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class ChitchatInterceptService {

    /** 连续寒暄 ≥ 2 次时强制追加 FAQ 快捷入口 */
    private static final int FAQ_CHIP_THRESHOLD = 2;
    /** 连续寒暄 ≥ 4 次时升级为引导话术 */
    private static final int ESCALATE_THRESHOLD = 4;
    /** 升级话术：引导用户描述具体业务问题 */
    private static final String ESCALATE_HINT =
            "亲，如需办理业务可以直接点击下方快捷问题，或描述您的问题哦~";

    /**
     * 无意义输入兜底正则（数据库 NOISE 模式之外的硬编码安全网）。
     * <p>
     * 匹配四类无意义输入：
     * <ol>
     *   <li>纯符号 ≥3 字符（不含汉字/拉丁字母/数字/空白）</li>
     *   <li>纯标点/符号</li>
     *   <li>同一字符重复 ≥5 次（如 "aaaaaa"）</li>
     *   <li>纯英文字母 ≥10 字符（键盘乱敲如 "asdfghjklqwerty"）</li>
     * </ol>
     */
    private static final Pattern MEANINGLESS_PATTERN = Pattern.compile(
            "^[^\\p{Script=Han}\\p{Script=Latin}\\p{Digit}\\s]{3,}$|" +
            "^[\\p{Punct}\\p{S}]+$|" +
            "^(.)\\1{4,}$|" +
            "^[a-zA-Z]{10,}$");

    /** 无意义输入默认引导话术（NOISE 类别无匹配回复模板时使用） */
    private static final String DEFAULT_NOISE_REPLY =
            "抱歉，我没有理解您的意思，您可以描述一下遇到的具体问题吗？";

    private final KbChitchatPatternMapper patternMapper;
    private final KbChitchatReplyMapper replyMapper;
    private final SessionPhaseResolver phaseResolver;
    private final ChitchatRateLimiter rateLimiter;

    /** 模式缓存（AtomicReference 保证 volatile 可见性 + CAS 无锁更新） */
    private final AtomicReference<List<KbChitchatPattern>> patternCache = new AtomicReference<>();

    /** REGEX 模式编译缓存，避免每次匹配都重新编译 */
    private final ConcurrentHashMap<String, Pattern> regexCache = new ConcurrentHashMap<>();

    public ChitchatInterceptService(KbChitchatPatternMapper patternMapper,
                                    KbChitchatReplyMapper replyMapper,
                                    SessionPhaseResolver phaseResolver,
                                    ChitchatRateLimiter rateLimiter) {
        this.patternMapper = patternMapper;
        this.replyMapper = replyMapper;
        this.phaseResolver = phaseResolver;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 寒暄拦截主入口：尝试匹配寒暄词表，命中返回 ChitchatHit，未命中返回 empty。
     * <p>
     * <b>完整处理流程（按顺序）：</b>
     * <ol>
     *   <li><b>前置校验：</b>userText 为空/blank 或 conversation 为 null → 直接返回 empty</li>
     *   <li><b>文本归一化：</b>调用 {@link TextNormalize#normalize(String)}
     *        — 全角转半角、大写转小写、合并多余空白。归一化后为空 → 返回 empty</li>
     *   <li><b>加载词表缓存：</b>从 {@link #patternCache} 获取，若未初始化则从
     *       {@code acc_user_kb_chitchat_pattern} 表加载（仅 enabled=1 的条目）</li>
     *   <li><b>读取会话上下文：</b>
     *       <ul>
     *         <li>{@code lastCategory}：从 acc_user_conversation.ext_json 中读取上次命中的寒暄类别，
     *              用于判断连续同类寒暄（REPEAT 阶段）</li>
     *         <li>{@code chitchatOnly}：会话的 chitchat_only_count，用于防刷计数</li>
     *       </ul>
     *   </li>
     *   <li><b>遍历词表匹配：</b>按 id 升序遍历所有已启用 pattern，调用
     *       {@link #matches(String, KbChitchatPattern)} 逐条比对
     *       （EXACT → CONTAINS → REGEX 三种模式）</li>
     *   <li><b>限频检查：</b>命中后调用 {@link ChitchatRateLimiter#allow(String, String)}，
     *       基于 Redis SETNX 实现 60s 同类别限频。超频 → 返回 empty</li>
     *   <li><b>阶段判定：</b>调用 {@link SessionPhaseResolver#resolve(UserConversation, String, String)}
     *       根据 turn_count + chitchat_only_count + lastCategory 判定 FIRST / MID / REPEAT</li>
     *   <li><b>查找回复模板：</b>调用 {@link #findReply(String, SessionPhase)}，
     *       先精确匹配 category+phase，未命中则 fallback 到 MID 阶段模板</li>
     *   <li><b>防刷升级：</b>
     *       <ul>
     *         <li>chitchatOnly >= 2 → 附加 FAQ_CHIPS</li>
     *         <li>chitchatOnly >= 4 → 替换回复文本为升级引导话术 + FAQ_CHIPS</li>
     *       </ul>
     *   </li>
     *   <li><b>返回结果：</b>封装 {@link ChitchatHit} 记录（含类别码、阶段、回复文本、是否附带FAQ入口）</li>
     *   <li><b>全部未命中：</b>返回 {@link Optional#empty()}，由上游走 IntentRecognition → LLM 链路</li>
     * </ol>
     *
     * @param userText     用户原始输入（未经归一化处理）
     * @param conversation 当前会话实体（需含 id, turn_count, chitchat_only_count, ext_json）
     * @return 命中时返回 ChitchatHit（含回复模板），未命中返回 Optional.empty()
     */
    public Optional<ChitchatHit> tryIntercept(String userText, UserConversation conversation) {
        // === Step 1: 前置校验 ===
        // userText 为空或 conversation 为 null 时直接返回，不进行任何匹配
        if (userText == null || userText.isBlank() || conversation == null) {
            return Optional.empty();
        }
        // === Step 2: 文本归一化 ===
        // 全角→半角、大写→小写、多空白→单空格、首尾去空白
        // 归一化后仍为空的输入（如纯标点 "？"）也直接返回
        String normalized = TextNormalize.normalize(userText);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        // === Step 3: 加载词表缓存 ===
        // 首次调用时从 acc_user_kb_chitchat_pattern 表加载（enabled=1），之后走 AtomicReference 缓存
        List<KbChitchatPattern> patterns = loadPatterns();

        // === Step 4: 读取会话上下文 ===
        // lastCategory: 上一次寒暄命中的 category（从 ext_json 中取），用于判断 REPEAT
        String lastCategory = ConversationExt.lastChitchatCategory(conversation);
        // chitchatOnly: 当前会话连续寒暄次数（不含业务消息），用于防刷升级
        int chitchatOnly = conversation.getChitchatOnlyCount() != null ? conversation.getChitchatOnlyCount() : 0;

        // === Step 5: 遍历词表匹配 ===
        // 按 id 升序遍历（与数据库 orderByAsc(id) 一致），先匹配先胜出
        for (KbChitchatPattern pattern : patterns) {
            // 依次尝试 EXACT → CONTAINS → REGEX 三种匹配模式
            if (!matches(normalized, pattern)) {
                continue;
            }

            // === Step 6: 限频检查 ===
            // 同会话同类寒暄 60s 内只响应一次（Redis SETNX + 60s TTL）
            String category = pattern.getCategoryCode();
            if (!rateLimiter.allow(conversation.getId(), category)) {
                log.debug("chitchat rate limited conv={} category={}", conversation.getId(), category);
                return Optional.empty(); // 超频 → 返回 empty，不返回任何回复
            }

            // === Step 7: 阶段判定 ===
            // 根据 turn_count==0 && chitchat_only_count==0 → FIRST
            // 同 category 再次命中 && chitchat_only_count>0 → REPEAT
            // 其他 → MID
            SessionPhase phase = phaseResolver.resolve(conversation, lastCategory, category);

            // === Step 8: 查找回复模板 ===
            // 先精确匹配 category + phase，未命中则 fallback 到 category + MID
            KbChitchatReply reply = findReply(category, phase);
            if (reply == null) {
                log.debug("chitchat matched category={} but no reply for phase={}", category, phase);
                return Optional.empty();
            }

            // === Step 9: 提取回复内容 ===
            String replyText = reply.getTextTemplate();
            // 模板自身标记是否需要 FAQ chips（如 GREETING/FIRST 默认显示猜你想问）
            boolean appendFaq = reply.getAppendFaqChips() != null && reply.getAppendFaqChips() == 1;

            // === Step 10: 防刷升级策略 ===
            // 连续寒暄 ≥ 2 次：强制追加 FAQ 快捷入口（chips）
            if (chitchatOnly >= FAQ_CHIP_THRESHOLD) {
                appendFaq = true;
            }
            // 连续寒暄 ≥ 4 次：升级为引导话术，提示用户描述具体业务问题
            if (chitchatOnly >= ESCALATE_THRESHOLD) {
                replyText = ESCALATE_HINT;
                appendFaq = true;
            }

            // === Step 11: 命中日志 ===
            log.info("chitchat hit category={} phase={} text={} onlyCount={}",
                    category, phase, normalized, chitchatOnly);

            // === Step 12: 返回命中结果 ===
            // ChitchatHit 为不可变 record：categoryCode, sessionPhase, replyText, appendFaqChips
            return Optional.of(new ChitchatHit(category, phase.name(), replyText, appendFaq));
        }

        // === Step 13: 数据库模式全部未命中 → 无意义输入兜底检测 ===
        // 硬编码正则安全网：检测数据库 NOISE 模式未覆盖的键盘乱敲、纯符号等无意义输入
        // 避免穿透到 LLM 浪费 Token
        if (MEANINGLESS_PATTERN.matcher(userText.trim()).find()) {
            KbChitchatReply noiseReply = findReply("NOISE", SessionPhase.MID);
            String replyText = noiseReply != null ? noiseReply.getTextTemplate() : DEFAULT_NOISE_REPLY;
            log.info("chitchat hit meaningless-input fallback: text={}", normalized);
            return Optional.of(new ChitchatHit("NOISE", SessionPhase.MID.name(), replyText, false));
        }

        // === Step 14: 全部未命中 ===
        // 遍历完所有 pattern 仍无匹配 → 返回 empty，由上游 UserChatApplicationService
        // 走正常业务链路：IntentRecognition Skill → Facts → DMN → KnowledgeBranch
        return Optional.empty();
    }

    /**
     * 判断短文本是否为寒暄用语（不做限频、不返回回复模板）。
     * 用于复合句分句：含业务诉求时不应整句走 ChitchatIntercept。
     */
    public boolean isChitchatPhrase(String userText) {
        if (userText == null || userText.isBlank()) {
            return false;
        }
        String normalized = TextNormalize.normalize(userText);
        if (normalized.isEmpty()) {
            return false;
        }
        for (KbChitchatPattern pattern : loadPatterns()) {
            if (matches(normalized, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清除模式缓存。
     * <p>
     * 运营在后台修改寒暄词表后调用此方法，下次 {@link #tryIntercept} 时自动从数据库重新加载。
     * 使用 AtomicReference.set(null) 保证线程安全。
     */
    public void reloadPatterns() {
        patternCache.set(null);
    }

    /**
     * 从缓存或数据库加载已启用的寒暄模式列表。
     * <p>
     * 双重检查：先读 AtomicReference，为 null 时查数据库。
     * 仅加载 enabled=1 的条目，按 id 升序（先插入先匹配）。
     */
    private List<KbChitchatPattern> loadPatterns() {
        List<KbChitchatPattern> cached = patternCache.get();
        if (cached != null) {
            return cached;
        }
        // 缓存未命中 → 查数据库
        List<KbChitchatPattern> fresh = patternMapper.selectList(
                new LambdaQueryWrapper<KbChitchatPattern>()
                        .eq(KbChitchatPattern::getEnabled, 1)     // 仅已启用
                        .orderByAsc(KbChitchatPattern::getId));    // 按 id 升序（先插入优先匹配）
        patternCache.set(fresh);
        return fresh;
    }

    /**
     * 按分类和阶段查找寒暄回复模板。
     * <p>
     * <b>查找策略（二级 fallback）：</b>
     * <ol>
     *   <li>精确匹配：category_code + session_phase（如 GREETING+FIRST）</li>
     *   <li>MID 兜底：精确匹配失败时，fallback 到同 category 的 MID 阶段模板</li>
     *   <li>都未命中 → 返回 null（调用方应处理此情况）</li>
     * </ol>
     * <p>
     * REPEAT/FIRST 阶段若无专门模板，统一使用 MID 模板（更通用的回复措辞）。
     *
     * @param categoryCode 寒暄类别码（GREETING / PRESENCE / ACK / NOISE）
     * @param phase        会话阶段（FIRST / MID / REPEAT）
     * @return 匹配的回复模板，或 null
     */
    private KbChitchatReply findReply(String categoryCode, SessionPhase phase) {
        // 第一优先级：精确匹配 category + phase
        KbChitchatReply exact = replyMapper.selectOne(new LambdaQueryWrapper<KbChitchatReply>()
                .eq(KbChitchatReply::getCategoryCode, categoryCode)
                .eq(KbChitchatReply::getSessionPhase, phase.name())
                .eq(KbChitchatReply::getEnabled, 1)
                .last("LIMIT 1"));
        if (exact != null) {
            return exact;
        }
        // 第二优先级：fallback 到 MID 阶段模板（通用措辞）
        return replyMapper.selectOne(new LambdaQueryWrapper<KbChitchatReply>()
                .eq(KbChitchatReply::getCategoryCode, categoryCode)
                .eq(KbChitchatReply::getSessionPhase, SessionPhase.MID.name())  // MID 作为默认阶段
                .eq(KbChitchatReply::getEnabled, 1)
                .last("LIMIT 1"));
    }

    /**
     * 按匹配类型判断归一化后的用户文本是否命中寒暄模式。
     * <p>
     * <b>三种匹配模式：</b>
     * <ul>
     *   <li><b>EXACT</b>（默认）：归一化后完全相等。
     *       例：normalized="你好" == patternNorm="你好" → true</li>
     *   <li><b>CONTAINS</b>：归一化后包含模式串（子串匹配）。
     *       例：normalized="你好在吗" contains patternNorm="在吗" → true</li>
     *   <li><b>REGEX</b>：Java 正则匹配（忽略大小写 + Unicode 大小写）。
     *       例：pattern="^\d+元.*套餐$" 匹配 normalized="50元流量套餐" → true
     *       非法正则表达式被静默捕获，记录 warn 日志后返回 false</li>
     * </ul>
     *
     * @param normalized 归一化后的用户文本
     * @param pattern    寒暄模式（含模式串和匹配类型）
     * @return 是否匹配
     */
    private boolean matches(String normalized, KbChitchatPattern pattern) {
        String p = pattern.getPattern();
        if (p == null || p.isBlank()) {
            return false;
        }
        // match_type 为空时默认 EXACT
        String type = pattern.getMatchType() != null ? pattern.getMatchType() : "EXACT";
        // 对模式串也做归一化处理（统一大小写、全半角等）
        String patternNorm = TextNormalize.normalize(p);
        return switch (type) {
            case "CONTAINS" -> normalized.contains(patternNorm);  // 子串包含
            case "REGEX" -> {
                try {
                    // 使用缓存避免每次匹配都重新编译正则
                    Pattern compiled = regexCache.computeIfAbsent(p,
                            k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                    yield compiled.matcher(normalized).find();
                } catch (PatternSyntaxException e) {
                    log.warn("invalid chitchat REGEX pattern id={} expr={}", pattern.getId(), p);
                    yield false;
                }
            }
            default -> normalized.equals(patternNorm);  // EXACT: 完全相等
        };
    }

    /**
     * 寒暄命中结果（不可变记录）。
     *
     * @param categoryCode  寒暄类别码（GREETING / PRESENCE / ACK / NOISE）
     * @param sessionPhase  会话阶段（FIRST / MID / REPEAT）
     * @param replyText     回复文本（已应用防刷升级后的最终文本）
     * @param appendFaqChips 是否需要在回复后追加 FAQ 快捷入口（猜你想问 chips）
     */
    public record ChitchatHit(String categoryCode, String sessionPhase, String replyText, boolean appendFaqChips) {
    }
}
