package cn.cloudcat.user.agent.skill;

import cn.cloudcat.user.agent.trace.SkillTraceCollector;
import cn.cloudcat.user.agent.utterance.UtterancePlan;
import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.diagnosis.model.DiagnosisResult;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.common.model.ReplySegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skill 链共享上下文（随 Skill 执行逐步填充）。
 *
 * @author Kang
 * @since 2026-06-02
 */
public class SkillContext {

    // ---- 构造参数（不可变） ----
    private final String conversationId;
    private final String userId;
    private final String userText;
    private final String intentHint;

    /** 本轮 traceId（全链路关联键） */
    private String turnId;
    /** 本轮用户消息 ID */
    private String userMessageId;
    /** Skill 链决策轨迹收集器 */
    private final SkillTraceCollector trace = new SkillTraceCollector();

    // ---- Skill 链逐级填充的中间结果 ----
    private UserConversation conversation;
    private DeviceFacts facts;
    private String resolvedIntent;
    private DiagnosisResult diagnosis;
    private String primaryRootCause;
    private final List<ReplySegment> replySegments = new ArrayList<>();

    // ---- 流程控制标记 ----
    private boolean handled;
    private boolean needRag;
    private boolean chitchat;
    private boolean closingTriggered;
    private boolean appendSatisfaction;
    private boolean suggestHumanTransfer;
    private boolean compoundIntent;

    // ---- 可选辅助属性 ----
    private String ragQuery;
    private String emotionTag;
    private String emotionRisk;
    private final List<String> compoundIntentCodes = new ArrayList<>();

    private UtterancePlan utterancePlan;
    private DialogueState dialogueState;
    private boolean continueActiveTask;
    private boolean skipRuleDiagnosis;
    private String pendingIntentNotice;

    public SkillContext(String conversationId, String userId, String userText, String intentHint) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.userText = userText;
        this.intentHint = intentHint;
    }

    public String conversationId() { return conversationId; }
    public String userId() { return userId; }
    public String userText() { return userText; }
    public String intentHint() { return intentHint; }

    public String turnId() { return turnId; }

    public String userMessageId() { return userMessageId; }

    public SkillTraceCollector trace() { return trace; }

    /** 绑定本轮 traceId 与用户消息 ID */
    public void bindTurn(String turnId, String userMessageId) {
        this.turnId = turnId;
        this.userMessageId = userMessageId;
    }

    public UserConversation conversation() { return conversation; }
    public void setConversation(UserConversation conversation) { this.conversation = conversation; }

    public DeviceFacts facts() { return facts; }
    public void setFacts(DeviceFacts facts) { this.facts = facts; }

    public String resolvedIntent() { return resolvedIntent; }
    public void setResolvedIntent(String resolvedIntent) { this.resolvedIntent = resolvedIntent; }

    public DiagnosisResult diagnosis() { return diagnosis; }
    /** 设置诊断结果并同步提取 primaryRootCause */
    public void setDiagnosis(DiagnosisResult diagnosis) {
        this.diagnosis = diagnosis;
        if (diagnosis != null) {
            this.primaryRootCause = diagnosis.primaryRootCause();
        }
    }

    public String primaryRootCause() { return primaryRootCause; }

    public List<ReplySegment> replySegments() { return replySegments; }
    /** 批量添加回复分段 */
    public void addSegments(List<ReplySegment> segments) {
        if (segments != null) {
            replySegments.addAll(segments);
        }
    }

    public boolean handled() { return handled; }
    /** 标记已处理（短路后续 Skill） */
    public void markHandled() { this.handled = true; }

    public boolean needRag() { return needRag; }
    /** 标记需要 RAG 兜底（短路后续 Skill） */
    public void markNeedRag() { this.needRag = true; }

    public boolean chitchat() { return chitchat; }
    /** 标记本轮为寒暄消息 */
    public void markChitchat() { this.chitchat = true; }

    public boolean closingTriggered() { return closingTriggered; }
    /** 标记已触发关闭脚本 */
    public void markClosingTriggered() { this.closingTriggered = true; }

    public boolean appendSatisfaction() { return appendSatisfaction; }
    /** 标记回复末尾需追加满意度卡片 */
    public void markAppendSatisfaction() { this.appendSatisfaction = true; }

    /** 返回设备事实数据的 Map 视图，若无事实则返回空 Map */
    public Map<String, Object> factMap() {
        return facts != null ? facts.values() : Map.of();
    }

    public String ragQuery() { return ragQuery; }
    public void setRagQuery(String ragQuery) { this.ragQuery = ragQuery; }

    public String emotionTag() { return emotionTag; }
    public void setEmotionTag(String emotionTag) { this.emotionTag = emotionTag; }

    public String emotionRisk() { return emotionRisk; }
    public void setEmotionRisk(String emotionRisk) { this.emotionRisk = emotionRisk; }

    public boolean suggestHumanTransfer() { return suggestHumanTransfer; }
    public void setSuggestHumanTransfer(boolean suggestHumanTransfer) { this.suggestHumanTransfer = suggestHumanTransfer; }

    public boolean compoundIntent() { return compoundIntent; }

    public List<String> compoundIntentCodes() { return compoundIntentCodes; }

    /** 设置复合意图编码列表，当意图数 >1 时自动标记 compoundIntent=true */
    public void setCompoundIntents(List<String> codes) {
        compoundIntentCodes.clear();
        if (codes != null) {
            compoundIntentCodes.addAll(codes);
        }
        compoundIntent = compoundIntentCodes.size() > 1;
    }

    public UtterancePlan utterancePlan() {
        return utterancePlan;
    }

    public void setUtterancePlan(UtterancePlan utterancePlan) {
        this.utterancePlan = utterancePlan;
    }

    public DialogueState dialogueState() {
        return dialogueState;
    }

    public void setDialogueState(DialogueState dialogueState) {
        this.dialogueState = dialogueState;
    }

    /** 意图识别 / 同义词匹配用的业务文本（已剥离寒暄子句） */
    public String effectiveUserTextForIntent() {
        if (utterancePlan != null && utterancePlan.businessTextForLlm() != null
                && !utterancePlan.businessTextForLlm().isBlank()) {
            return utterancePlan.businessTextForLlm();
        }
        return userText;
    }

    public boolean continueActiveTask() {
        return continueActiveTask;
    }

    public void setContinueActiveTask(boolean continueActiveTask) {
        this.continueActiveTask = continueActiveTask;
    }

    public boolean skipRuleDiagnosis() {
        return skipRuleDiagnosis;
    }

    public void setSkipRuleDiagnosis(boolean skipRuleDiagnosis) {
        this.skipRuleDiagnosis = skipRuleDiagnosis;
    }

    /** 续办任务时复用上轮 DMN 主因，避免重复诊断 */
    public void applyForcedRootCause(String rootCause) {
        this.primaryRootCause = rootCause;
    }

    public String pendingIntentNotice() {
        return pendingIntentNotice;
    }

    public void setPendingIntentNotice(String pendingIntentNotice) {
        this.pendingIntentNotice = pendingIntentNotice;
    }
}
