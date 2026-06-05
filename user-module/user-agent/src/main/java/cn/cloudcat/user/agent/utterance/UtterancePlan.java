package cn.cloudcat.user.agent.utterance;

import java.util.ArrayList;
import java.util.List;

/**
 * 单轮话语理解结果：寒暄/业务子句、本轮意图、待排队意图。
 */
public class UtterancePlan {

    private final List<UtteranceSegment> segments = new ArrayList<>();
    private final List<String> businessIntents = new ArrayList<>();
    private final List<String> deferredIntents = new ArrayList<>();
    private String primaryIntent;
    private String businessTextForLlm;
    private String prefixChitchatAck;
    private boolean pureChitchat;
    private boolean mixedChitchat;
    /** 本轮已在 Turn Policy 阶段尝试过 LLM 调度（避免 IntentRecognition 重复调用） */
    private boolean llmTurnPolicyAttempted;
    /** LLM 调度成功产出至少一个业务意图 */
    private boolean llmTurnPolicyHit;

    public List<UtteranceSegment> segments() {
        return segments;
    }

    public void addSegment(UtteranceSegment segment) {
        segments.add(segment);
    }

    public List<String> businessIntents() {
        return businessIntents;
    }

    public void setBusinessIntents(List<String> intents) {
        businessIntents.clear();
        if (intents != null) {
            businessIntents.addAll(intents);
        }
    }

    public List<String> deferredIntents() {
        return deferredIntents;
    }

    public void setDeferredIntents(List<String> deferred) {
        deferredIntents.clear();
        if (deferred != null) {
            deferredIntents.addAll(deferred);
        }
    }

    public String primaryIntent() {
        return primaryIntent;
    }

    public void setPrimaryIntent(String primaryIntent) {
        this.primaryIntent = primaryIntent;
    }

    public String businessTextForLlm() {
        return businessTextForLlm;
    }

    public void setBusinessTextForLlm(String businessTextForLlm) {
        this.businessTextForLlm = businessTextForLlm;
    }

    public String prefixChitchatAck() {
        return prefixChitchatAck;
    }

    public void setPrefixChitchatAck(String prefixChitchatAck) {
        this.prefixChitchatAck = prefixChitchatAck;
    }

    public boolean pureChitchat() {
        return pureChitchat;
    }

    public void setPureChitchat(boolean pureChitchat) {
        this.pureChitchat = pureChitchat;
    }

    public boolean mixedChitchat() {
        return mixedChitchat;
    }

    public void setMixedChitchat(boolean mixedChitchat) {
        this.mixedChitchat = mixedChitchat;
    }

    public boolean compoundBusiness() {
        return businessIntents.size() > 1;
    }

    public boolean llmTurnPolicyAttempted() {
        return llmTurnPolicyAttempted;
    }

    public void setLlmTurnPolicyAttempted(boolean llmTurnPolicyAttempted) {
        this.llmTurnPolicyAttempted = llmTurnPolicyAttempted;
    }

    public boolean llmTurnPolicyHit() {
        return llmTurnPolicyHit;
    }

    public void setLlmTurnPolicyHit(boolean llmTurnPolicyHit) {
        this.llmTurnPolicyHit = llmTurnPolicyHit;
    }
}
