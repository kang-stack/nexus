package cn.cloudcat.user.knowledge.chitchat;

import cn.cloudcat.user.session.SessionPhaseResolver;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.common.entity.KbChitchatPattern;
import cn.cloudcat.user.common.entity.KbChitchatReply;
import cn.cloudcat.user.common.mapper.KbChitchatPatternMapper;
import cn.cloudcat.user.common.mapper.KbChitchatReplyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChitchatInterceptServiceTest {

    @Mock
    private KbChitchatPatternMapper patternMapper;
    @Mock
    private KbChitchatReplyMapper replyMapper;
    @Mock
    private ChitchatRateLimiter rateLimiter;

    private ChitchatInterceptService service;

    @BeforeEach
    void setUp() {
        lenient().when(rateLimiter.allow(any(), any())).thenReturn(true);
        service = new ChitchatInterceptService(patternMapper, replyMapper, new SessionPhaseResolver(), rateLimiter);
    }

    @Test
    void exactGreetingMatchesFirstPhaseTemplate() {
        // 验证精确匹配问候语返回FIRST阶段的模板回复
        KbChitchatPattern pattern = new KbChitchatPattern();
        pattern.setCategoryCode("GREETING");
        pattern.setPattern("你好");
        pattern.setMatchType("EXACT");
        pattern.setEnabled(1);

        KbChitchatReply reply = new KbChitchatReply();
        reply.setCategoryCode("GREETING");
        reply.setSessionPhase("FIRST");
        reply.setTextTemplate("您好~WiFi智能客服为您服务！");
        reply.setEnabled(1);

        when(patternMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pattern));
        when(replyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(reply);

        UserConversation conv = new UserConversation();
        conv.setTurnCount(0);
        conv.setChitchatOnlyCount(0);

        Optional<ChitchatInterceptService.ChitchatHit> hit = service.tryIntercept("你好", conv);
        assertTrue(hit.isPresent());
        assertEquals("GREETING", hit.get().categoryCode());
        assertTrue(hit.get().replyText().contains("您好"));
    }

    @Test
    void nonChitchatReturnsEmpty() {
        // 验证非寒暄文本（业务问题）不匹配返回empty
        when(patternMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        UserConversation conv = new UserConversation();
        assertTrue(service.tryIntercept("连不上网", conv).isEmpty());
    }
}
