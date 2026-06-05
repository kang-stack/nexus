package cn.cloudcat.user.agent;

import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatAgentTest {

    @Test
    void stopsOnHandledSkill() {
        ChatSkill handled = new ChatSkill() {
            @Override public int order() { return 1; }
            @Override public String name() { return "handled"; }
            @Override public boolean supports(SkillContext ctx) { return true; }
            @Override public void execute(SkillContext ctx) { ctx.markHandled(); }
        };
        ChatSkill never = new ChatSkill() {
            @Override public int order() { return 2; }
            @Override public String name() { return "never"; }
            @Override public boolean supports(SkillContext ctx) { return true; }
            @Override public void execute(SkillContext ctx) { ctx.markNeedRag(); }
        };
        ChatAgent agent = new ChatAgent(List.of(never, handled)); // sorted by order
        SkillContext ctx = new SkillContext("c1", "u1", "hi", "hi");
        agent.handle(ctx);
        assertTrue(ctx.handled());
        assertFalse(ctx.needRag());
    }
}
