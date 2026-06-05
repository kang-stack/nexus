package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.message.diagnosis.DiagnosisPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 设备事实收集 Skill — Phase 2 规则引擎第一步（order=20）。
 * <p>
 * 从数据库加载用户设备的运行状态事实（信号强度、流量、电量等），
 * 存储到 SkillContext.facts 供后续 RuleDiagnosis(21) DMN 诊断使用。
 * <p>
 * Skill 链阶段：[Phase1 预处理 10-19] → [Phase2 规则引擎 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class FactsCollectChatSkill implements ChatSkill {

    private final DiagnosisPipeline diagnosisPipeline;

    @Override
    public int order() { return 20; }

    @Override
    public String name() { return "FactsCollect"; }

    /** 仅在未处理、不需要 RAG 且事实未收集时触发 */
    @Override
    public boolean supports(SkillContext ctx) {
        return !ctx.handled() && !ctx.needRag() && ctx.facts() == null;
    }

    /** 从数据库加载设备运行事实并设置到上下文 */
    @Override
    public void execute(SkillContext ctx) {
        var loaded = diagnosisPipeline.loadFacts(ctx.userId());
        ctx.setFacts(new DeviceFacts(loaded.facts(), loaded.deviceSn(), Instant.now()));
    }
}
