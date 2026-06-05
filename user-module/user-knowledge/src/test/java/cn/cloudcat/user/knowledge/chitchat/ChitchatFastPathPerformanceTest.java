package cn.cloudcat.user.knowledge.chitchat;

import cn.cloudcat.user.session.SessionPhaseResolver;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.common.entity.KbChitchatPattern;
import cn.cloudcat.user.common.entity.KbChitchatReply;
import cn.cloudcat.user.common.mapper.KbChitchatPatternMapper;
import cn.cloudcat.user.common.mapper.KbChitchatReplyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V2 寒暄 FastPath P99 目标 &lt; 100ms（纯词表路径，无 LLM）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("performance")
class ChitchatFastPathPerformanceTest {

    private static final int WARMUP = 50;
    private static final int ITERATIONS = 500;
    private static final long P99_LIMIT_MS = 100;

    @Mock
    private KbChitchatPatternMapper patternMapper;
    @Mock
    private KbChitchatReplyMapper replyMapper;
    @Mock
    private ChitchatRateLimiter rateLimiter;

    private ChitchatInterceptService service;
    private UserConversation conversation;

    @BeforeEach
    void setUp() {
        when(rateLimiter.allow(any(), any())).thenReturn(true);

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

        service = new ChitchatInterceptService(patternMapper, replyMapper, new SessionPhaseResolver(), rateLimiter);
        service.reloadPatterns();

        conversation = new UserConversation();
        conversation.setId("perf-conv-1");
        conversation.setTurnCount(0);
        conversation.setChitchatOnlyCount(0);
    }

    @Test
    void chitchatFastPathP99Under100ms() {
        for (int i = 0; i < WARMUP; i++) {
            service.tryIntercept("你好", conversation);
        }

        List<Long> samples = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            service.tryIntercept("你好", conversation).orElseThrow();
            samples.add((System.nanoTime() - start) / 1_000_000);
        }

        Collections.sort(samples);
        long p99 = samples.get((int) Math.ceil(ITERATIONS * 0.99) - 1);
        LongSummaryStatistics stats = samples.stream().mapToLong(Long::longValue).summaryStatistics();

        System.out.printf("ChitchatFastPath n=%d avg=%.2fms p99=%dms max=%dms%n",
                ITERATIONS, stats.getAverage(), p99, stats.getMax());

        assertTrue(p99 < P99_LIMIT_MS,
                "P99 should be < " + P99_LIMIT_MS + "ms but was " + p99 + "ms");
    }
}
