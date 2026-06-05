package cn.cloudcat.user.knowledge.engine;

import cn.cloudcat.user.common.entity.KbFaqAlias;
import cn.cloudcat.user.common.mapper.KbFaqAliasMapper;
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
import static org.mockito.Mockito.when;

/**
 * @author Kang
 * @since 2026-06-02
 */
@ExtendWith(MockitoExtension.class)
class FaqAliasMatchServiceTest {

    @Mock
    private KbFaqAliasMapper aliasMapper;

    private FaqAliasMatchService service;

    @BeforeEach
    void setUp() {
        service = new FaqAliasMatchService(aliasMapper);
    }

    @Test
    void nullInputReturnsEmpty() {
        assertTrue(service.matchIntent(null).isEmpty());
    }

    @Test
    void blankInputReturnsEmpty() {
        assertTrue(service.matchIntent("   ").isEmpty());
    }

    @Test
    void exactMatchReturnsIntentCode() {
        KbFaqAlias alias = createAlias("连不上网", "NET_PROBLEM", "EXACT");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        Optional<String> result = service.matchIntent("连不上网");
        assertTrue(result.isPresent());
        assertEquals("NET_PROBLEM", result.get());
    }

    @Test
    void exactMatchNormalizedComparison() {
        KbFaqAlias alias = createAlias("你好", "GREETING", "EXACT");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        Optional<String> result = service.matchIntent("你好");
        assertTrue(result.isPresent());
        assertEquals("GREETING", result.get());
    }

    @Test
    void containsMatchReturnsIntentCode() {
        KbFaqAlias alias = createAlias("上网", "NET_PROBLEM", "CONTAINS");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        Optional<String> result = service.matchIntent("我的上网速度很慢");
        assertTrue(result.isPresent());
        assertEquals("NET_PROBLEM", result.get());
    }

    @Test
    void regexMatchReturnsIntentCode() {
        KbFaqAlias alias = new KbFaqAlias();
        alias.setAliasText("\\d+元.*套餐");
        alias.setIntentCode("RECHARGE");
        alias.setMatchType("REGEX");
        alias.setEnabled(1);
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        Optional<String> result = service.matchIntent("50元套餐怎么选");
        assertTrue(result.isPresent());
        assertEquals("RECHARGE", result.get());
    }

    @Test
    void invalidRegexReturnsEmpty() {
        KbFaqAlias alias = new KbFaqAlias();
        alias.setAliasText("[invalid regex");
        alias.setIntentCode("TEST");
        alias.setMatchType("REGEX");
        alias.setEnabled(1);
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        assertTrue(service.matchIntent("anything").isEmpty());
    }

    @Test
    void noMatchReturnsEmpty() {
        KbFaqAlias alias = createAlias("上网", "NET_PROBLEM", "EXACT");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        assertTrue(service.matchIntent("我的套餐还剩多少").isEmpty());
    }

    @Test
    void aliasWithoutIntentCodeSkipped() {
        KbFaqAlias alias = new KbFaqAlias();
        alias.setAliasText("测试");
        alias.setIntentCode(null);
        alias.setMatchType("EXACT");
        alias.setEnabled(1);
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        assertTrue(service.matchIntent("测试").isEmpty());
    }

    @Test
    void defaultMatchTypeIsExact() {
        KbFaqAlias alias = new KbFaqAlias();
        alias.setAliasText("hello");
        alias.setIntentCode("GREETING");
        alias.setMatchType(null);
        alias.setEnabled(1);
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias));

        Optional<String> result = service.matchIntent("hello");
        assertTrue(result.isPresent());
        assertEquals("GREETING", result.get());
    }

    @Test
    void firstMatchWinsInOrder() {
        KbFaqAlias alias1 = new KbFaqAlias();
        alias1.setId(1L);
        alias1.setAliasText("上网");
        alias1.setIntentCode("NET_PROBLEM");
        alias1.setMatchType("CONTAINS");
        alias1.setEnabled(1);

        KbFaqAlias alias2 = new KbFaqAlias();
        alias2.setId(2L);
        alias2.setAliasText("上网慢");
        alias2.setIntentCode("NET_SLOW");
        alias2.setMatchType("EXACT");
        alias2.setEnabled(1);

        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias1, alias2));

        Optional<String> result = service.matchIntent("上网慢");
        assertEquals("NET_PROBLEM", result.get());
    }

    @Test
    void matchAllIntentsReturnsMultipleOrderedByTextPosition() {
        KbFaqAlias recharge = createAlias("充值", "充值问题", "CONTAINS");
        KbFaqAlias realname = createAlias("实名", "实名认证问题", "CONTAINS");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(recharge, realname));

        List<String> result = service.matchAllIntents("怎么实名 怎么充值");
        assertEquals(List.of("实名认证问题", "充值问题"), result);
    }

    @Test
    void matchAllIntentsRechargeAndNetworkViaAliases() {
        KbFaqAlias recharge = createAlias("充值", "充值问题", "CONTAINS");
        KbFaqAlias network = createAlias("网络好卡", "网络问题", "CONTAINS");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(recharge, network));

        List<String> result = service.matchAllIntents("怎么充值 网络好卡");
        assertEquals(List.of("充值问题", "网络问题"), result);
    }

    @Test
    void reloadRefreshesCache() {
        KbFaqAlias alias1 = createAlias("old", "OLD_INTENT", "EXACT");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias1));

        service.matchIntent("old");

        KbFaqAlias alias2 = createAlias("new", "NEW_INTENT", "EXACT");
        when(aliasMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alias2));

        service.reload();
        Optional<String> result = service.matchIntent("new");
        assertTrue(result.isPresent());
        assertEquals("NEW_INTENT", result.get());
    }

    private KbFaqAlias createAlias(String aliasText, String intentCode, String matchType) {
        KbFaqAlias alias = new KbFaqAlias();
        alias.setAliasText(aliasText);
        alias.setIntentCode(intentCode);
        alias.setMatchType(matchType);
        alias.setEnabled(1);
        return alias;
    }
}
