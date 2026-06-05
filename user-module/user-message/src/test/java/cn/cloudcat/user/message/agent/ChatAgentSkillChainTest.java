package cn.cloudcat.user.message.agent;

import cn.cloudcat.user.agent.ChatAgent;
import cn.cloudcat.user.agent.skill.ChatSkill;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.session.entity.UserConversation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChatAgentSkillChainTest {

    @Test
    void chitchatSkillStopsChainEarly() {
        ChatSkill chitchat = new ChatSkill() {
            @Override
            public int order() {
                return 10;
            }

            @Override
            public String name() {
                return "ChitchatStub";
            }

            @Override
            public boolean supports(SkillContext ctx) {
                return true;
            }

            @Override
            public void execute(SkillContext ctx) {
                ctx.addSegments(List.of(new ReplySegment("TEXT", "您好~", 1, 1)));
                ctx.markChitchat();
                ctx.markHandled();
            }
        };

        ChatSkill shouldNotRun = new ChatSkill() {
            @Override
            public int order() {
                return 20;
            }

            @Override
            public String name() {
                return "ShouldNotRun";
            }

            @Override
            public boolean supports(SkillContext ctx) {
                return !ctx.handled();
            }

            @Override
            public void execute(SkillContext ctx) {
                fail("Skill chain should stop after chitchat handled");
            }
        };

        ChatAgent agent = new ChatAgent(List.of(shouldNotRun, chitchat));
        SkillContext ctx = new SkillContext("c1", "u1", "你好", null);
        UserConversation conv = new UserConversation();
        conv.setId("c1");
        ctx.setConversation(conv);

        agent.handle(ctx);

        assertTrue(ctx.handled());
        assertTrue(ctx.chitchat());
        assertEquals(1, ctx.replySegments().size());
        assertFalse(ctx.needRag());
    }

    @Test
    void needRagStopsChainBeforeLowerPriority() {
        ChatSkill ragMarker = new ChatSkill() {
            @Override
            public int order() {
                return 30;
            }

            @Override
            public String name() {
                return "RagMarker";
            }

            @Override
            public boolean supports(SkillContext ctx) {
                return !ctx.handled();
            }

            @Override
            public void execute(SkillContext ctx) {
                ctx.markNeedRag();
            }
        };

        ChatSkill lateSkill = new ChatSkill() {
            @Override
            public int order() {
                return 35;
            }

            @Override
            public String name() {
                return "LateMain";
            }

            @Override
            public boolean supports(SkillContext ctx) {
                return true;
            }

            @Override
            public void execute(SkillContext ctx) {
                fail("Main chain should stop when needRag is set");
            }
        };

        ChatAgent agent = new ChatAgent(List.of(ragMarker, lateSkill));
        SkillContext ctx = new SkillContext("c2", "u1", "奇怪的问题", null);
        agent.handle(ctx);

        assertTrue(ctx.needRag());
        assertFalse(ctx.handled());
    }

    @Test
    void postPhaseSkillRunsAfterNeedRag() {
        ChatSkill ragMarker = new ChatSkill() {
            @Override
            public int order() {
                return 30;
            }

            @Override
            public String name() {
                return "RagMarker";
            }

            @Override
            public boolean supports(SkillContext ctx) {
                return !ctx.handled();
            }

            @Override
            public void execute(SkillContext ctx) {
                ctx.markNeedRag();
            }
        };

        final boolean[] postRan = {false};
        ChatSkill closingStub = new ChatSkill() {
            @Override
            public int order() {
                return 40;
            }

            @Override
            public String name() {
                return "ClosingPost";
            }

            @Override
            public boolean supports(SkillContext ctx) {
                return true;
            }

            @Override
            public void execute(SkillContext ctx) {
                postRan[0] = true;
            }
        };

        ChatAgent agent = new ChatAgent(List.of(ragMarker, closingStub));
        SkillContext ctx = new SkillContext("c3", "u1", "兜底问题", null);
        agent.handle(ctx);

        assertTrue(ctx.needRag());
        assertTrue(postRan[0], "Post-phase skills (order>=40) still run after needRag");
    }
}
