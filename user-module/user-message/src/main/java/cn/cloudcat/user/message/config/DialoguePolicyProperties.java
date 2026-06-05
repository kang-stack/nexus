package cn.cloudcat.user.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话 Turn Policy / 话语理解开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "icc.cs.dialogue")
public class DialoguePolicyProperties {

    /**
     * 规则未识别业务意图时，是否在 DialoguePrepare 阶段调用 LLM 做 Turn Policy 调度。
     * 开发环境 LLM 不可达时可设为 false，由 IntentRecognition(13) 兜底（行为与 Phase 2 一致）。
     */
    private boolean llmTurnPolicyEnabled = true;
}
