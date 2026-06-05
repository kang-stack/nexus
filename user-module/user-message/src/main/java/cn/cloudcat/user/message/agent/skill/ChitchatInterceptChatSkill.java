package cn.cloudcat.user.message.agent.skill;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.knowledge.chitchat.ChitchatInterceptService;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.message.chitchat.ChitchatFaqChipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 寒暄拦截 Skill — Phase 1 预处理最高优先级（order=10）。
 * <p>
 * 对"你好/在吗/谢谢"等纯闲聊输入进行 FastPath 拦截，P99 &lt; 100ms，不调用 LLM。
 * 命中后直接返回预设模板回复 + 可选 FAQ chips，markHandled 短路后续全部 Skill。
 * <p>
 * Skill 链阶段：[Phase1 预处理 10-19] → [Phase2 规则 20-29] → [Phase3 兜底 30-39] → [Phase4 后处理 40-49]
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@RequiredArgsConstructor
public class ChitchatInterceptChatSkill implements ChatSkill {

    private final ChitchatInterceptService chitchatInterceptService;
    private final UserConversationService conversationService;
    private final ChitchatFaqChipService faqChipService;

    @Override
    public int order() { return 10; }

    @Override
    public String name() { return "ChitchatIntercept"; }

    /** 仅整句纯寒暄时触发；寒暄+业务混合由 UtterancePlan 走前缀_ack + 业务分支 */
    @Override
    public boolean supports(SkillContext ctx) {
        if (ctx.conversation() == null || ctx.handled()) {
            return false;
        }
        if (ctx.utterancePlan() != null) {
            return ctx.utterancePlan().pureChitchat();
        }
        return true;
    }

    /** 尝试匹配闲聊词库，命中则直接返回预设回复 + 可选 FAQ 入口 */
    @Override
    public void execute(SkillContext ctx) {
        var hit = chitchatInterceptService.tryIntercept(ctx.userText(), ctx.conversation());
        if (hit.isEmpty()) {
            return;
        }
        var chitchat = hit.get();
        ReplySegment seg = new ReplySegment("TEXT", chitchat.replyText(), 1, 1);
        // 部分闲聊回复类型需要附加 FAQ 快捷入口
        if (chitchat.appendFaqChips()) {
            seg.setLinks(faqChipService.buildChips());
        }
        ctx.addSegments(List.of(seg));
        ctx.markChitchat();
        ctx.markHandled();
        conversationService.onChitchatReply(ctx.conversation(), chitchat.categoryCode());
    }
}
