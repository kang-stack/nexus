package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbBranch;
import cn.cloudcat.user.common.entity.KbReplyScript;
import cn.cloudcat.user.common.entity.KnowledgeIntent;
import cn.cloudcat.user.common.mapper.KbBranchMapper;
import cn.cloudcat.user.common.mapper.KbReplyScriptMapper;
import cn.cloudcat.user.common.mapper.KnowledgeIntentMapper;
import cn.cloudcat.user.knowledge.engine.IntentResolverService;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.knowledge.service.branch.BranchConditionMatcher;
import cn.cloudcat.user.knowledge.service.branch.BranchReplyComposer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 结构化知识：intent + primaryRootCause → acc_user_kb_branch → ReplySegment。
 * Phase 2 接入 DMN 输出的 primaryRootCause；当前无诊断时仅匹配无条件分支。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class KnowledgeBranchService {

    private final KnowledgeIntentMapper intentMapper;
    private final KbBranchMapper branchMapper;
    private final KbReplyScriptMapper scriptMapper;
    private final IntentResolverService intentResolver;
    private final BranchConditionMatcher conditionMatcher;
    private final BranchReplyComposer replyComposer;

    public KnowledgeBranchService(KnowledgeIntentMapper intentMapper,
                                   KbBranchMapper branchMapper,
                                   KbReplyScriptMapper scriptMapper,
                                   IntentResolverService intentResolver,
                                   BranchConditionMatcher conditionMatcher,
                                   BranchReplyComposer replyComposer) {
        this.intentMapper = intentMapper;
        this.branchMapper = branchMapper;
        this.scriptMapper = scriptMapper;
        this.intentResolver = intentResolver;
        this.conditionMatcher = conditionMatcher;
        this.replyComposer = replyComposer;
    }

    /**
     * 根据用户输入和诊断结果匹配知识库分支，返回回复脚本片段。
     *
     * @param userInput        用户原始输入
     * @param rawIntent        原始意图标签（LLM 输出或快捷入口传入）
     * @param primaryRootCause DMN 诊断主根因（可为 null）
     * @param userId           当前用户 ID
     * @param facts            设备事实数据
     * @return 匹配到的回复片段列表，无匹配时返回空列表
     */
    public List<ReplySegment> resolve(String userInput, String rawIntent,
                                       String primaryRootCause, String userId,
                                       Map<String, Object> facts) {
        // 将原始意图解析为 canonical intent_code
        String intentCode = intentResolver.resolve(rawIntent);
        log.info("[KnowledgeBranch] resolve rawIntent={} -> intentCode={} rootCause={}",
                rawIntent, intentCode, primaryRootCause);
        if (intentCode == null || intentCode.isBlank()) {
            log.info("[KnowledgeBranch] MISS: empty intent after resolve");
            return Collections.emptyList();
        }
        KnowledgeIntent intent = findIntent(intentCode);
        if (intent == null) {
            log.info("[KnowledgeBranch] MISS: intent not in acc_user_kb_intent or disabled, code={}", intentCode);
            return Collections.emptyList();
        }

        // 查询该意图下所有已启用的分支，按优先级排序
        List<KbBranch> branches = branchMapper.selectList(
                new LambdaQueryWrapper<KbBranch>()
                        .eq(KbBranch::getIntentCode, intentCode)
                        .eq(KbBranch::getEnabled, 1)
                        .orderByAsc(KbBranch::getPriority));

        if (branches.isEmpty()) {
            log.info("[KnowledgeBranch] MISS: no acc_user_kb_branch rows for intent={}", intentCode);
            return Collections.emptyList();
        }

        // 构造条件匹配上下文（facts + intent + rootCause）
        Map<String, Object> ctx = buildMatchContext(facts, intentCode, primaryRootCause);

        // 按优先级遍历分支，首个条件匹配成功的分支即命中
        for (KbBranch branch : branches) {
            boolean matched = conditionMatcher.matches(branch, ctx);
            log.info("[KnowledgeBranch] try branch={} priority={} matched={}",
                    branch.getBranchCode(), branch.getPriority(), matched);
            if (matched) {
                List<ReplySegment> segments = loadScripts(branch, ctx);
                if (segments.isEmpty()) {
                    log.warn("[KnowledgeBranch] branch={} matched but acc_user_kb_reply_script empty", branch.getBranchCode());
                } else {
                    log.info("[KnowledgeBranch] HIT branch={} intent={} segments={}",
                            branch.getBranchCode(), intentCode, segments.size());
                }
                return segments;
            }
        }
        log.info("[KnowledgeBranch] MISS: {} branches tried, none matched intent={} rootCause={}",
                branches.size(), intentCode, primaryRootCause);
        return Collections.emptyList();
    }

    /** 组装分支条件评估上下文：设备 facts + intent_code + DMN 主根因 */
    private Map<String, Object> buildMatchContext(Map<String, Object> facts,
                                                   String intentCode,
                                                   String primaryRootCause) {
        Map<String, Object> ctx = facts != null && !facts.isEmpty()
                ? new LinkedHashMap<>(facts)
                : new LinkedHashMap<>();
        ctx.put("intent_code", intentCode);
        if (primaryRootCause != null && !primaryRootCause.isBlank()) {
            ctx.put("primaryRootCause", primaryRootCause);
        }
        return ctx;
    }

    /** 加载分支下的回复脚本并交由 BranchReplyComposer 组装为 ReplySegment 列表 */
    private List<ReplySegment> loadScripts(KbBranch branch, Map<String, Object> ctx) {
        List<KbReplyScript> scripts = scriptMapper.selectList(
                new LambdaQueryWrapper<KbReplyScript>()
                        .eq(KbReplyScript::getBranchId, branch.getId())
                        .orderByAsc(KbReplyScript::getSeqNo));
        if (scripts.isEmpty()) {
            log.warn("[KnowledgeBranch] branch={} has no reply scripts", branch.getBranchCode());
            return Collections.emptyList();
        }
        return replyComposer.compose(scripts, ctx);
    }

    /** 按 intentCode 查找已启用的意图实体 */
    private KnowledgeIntent findIntent(String intentCode) {
        List<KnowledgeIntent> list = intentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeIntent>()
                        .eq(KnowledgeIntent::getIntentCode, intentCode)
                        .eq(KnowledgeIntent::getEnabled, 1));
        return list.isEmpty() ? null : list.get(0);
    }
}
