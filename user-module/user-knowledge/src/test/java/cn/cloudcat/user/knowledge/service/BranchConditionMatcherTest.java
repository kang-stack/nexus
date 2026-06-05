package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbBranch;
import cn.cloudcat.user.knowledge.service.branch.BranchConditionMatcher;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Kang
 * @since 2026-06-02
 */
class BranchConditionMatcherTest {

    private final BranchConditionMatcher matcher = new BranchConditionMatcher();

    // ===== existing tests (refactored to use imports) =====

    @Test
    void matchesPrimaryRootCause() {
        // 验证eq操作符：primaryRootCause匹配成功/失败
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"primaryRootCause","op":"eq","val":"OFFLINE"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("primaryRootCause", "OFFLINE")));
        assertFalse(matcher.matches(branch, Map.of("primaryRootCause", "SIGNAL_WEAK")));
    }

    @Test
    void emptyConditionMatchesAll() {
        // 验证空条件（null/blank）始终匹配
        KbBranch branch = new KbBranch();
        assertTrue(matcher.matches(branch, Map.of()));
        branch.setConditionJson("");
        assertTrue(matcher.matches(branch, Map.of()));
    }

    // ===== additional tests =====

    @Test
    void nullConditionJsonMatchesAll() {
        // 验证condition_json为null时匹配所有上下文
        KbBranch branch = new KbBranch();
        branch.setConditionJson(null);
        assertTrue(matcher.matches(branch, Map.of("anyKey", "anyValue")));
    }

    @Test
    void emptyItemsArrayMatchesAll() {
        // 验证items为空数组时匹配所有
        KbBranch branch = new KbBranch();
        branch.setConditionJson("{\"match\":\"AND\",\"items\":[]}");
        assertTrue(matcher.matches(branch, Map.of("primaryRootCause", "OFFLINE")));
    }

    @Test
    void orMatchFirstItemTrueReturnsTrue() {
        // 验证OR模式下第一项匹配即时返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"OR","items":[{"field":"primaryRootCause","op":"eq","val":"OFFLINE"},{"field":"primaryRootCause","op":"eq","val":"SIGNAL_WEAK"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("primaryRootCause", "OFFLINE")));
    }

    @Test
    void orMatchSecondItemTrueReturnsTrue() {
        // 验证OR模式下第二项匹配返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"OR","items":[{"field":"primaryRootCause","op":"eq","val":"OFFLINE"},{"field":"primaryRootCause","op":"eq","val":"SIGNAL_WEAK"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("primaryRootCause", "SIGNAL_WEAK")));
    }

    @Test
    void orMatchNoneReturnsFalse() {
        // 验证OR模式下无匹配项返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"OR","items":[{"field":"primaryRootCause","op":"eq","val":"OFFLINE"},{"field":"primaryRootCause","op":"eq","val":"SIGNAL_WEAK"}]}
                """);
        assertFalse(matcher.matches(branch, Map.of("primaryRootCause", "NO_SIGNAL")));
    }

    @Test
    void existsOperatorReturnsTrueWhenFieldPresent() {
        // 验证exists操作符：上下文字段存在且非空时返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"deviceSn","op":"exists"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("deviceSn", "SN123456")));
    }

    @Test
    void existsOperatorReturnsFalseWhenFieldAbsent() {
        // 验证exists操作符：上下文字段不存在时返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"deviceSn","op":"exists"}]}
                """);
        assertFalse(matcher.matches(branch, Map.of()));
    }

    @Test
    void existsOperatorReturnsFalseWhenFieldBlank() {
        // 验证exists操作符：上下文字段为空字符串时返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"deviceSn","op":"exists"}]}
                """);
        assertFalse(matcher.matches(branch, Map.of("deviceSn", "")));
    }

    @Test
    void inOperatorMatchesCommaSeparatedValues() {
        // 验证in操作符：逗号分隔的值列表中匹配
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"signalLevel","op":"in","val":"GOOD,FAIR,POOR"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("signalLevel", "GOOD")));
        assertTrue(matcher.matches(branch, Map.of("signalLevel", "FAIR")));
        assertFalse(matcher.matches(branch, Map.of("signalLevel", "EXCELLENT")));
    }

    @Test
    void inOperatorTrimsWhitespaceInValues() {
        // 验证in操作符忽略值列表中的空白
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"level","op":"in","val":"A, B , C"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("level", "B")));
    }

    @Test
    void unknownOperatorReturnsFalse() {
        // 验证未知操作符返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"x","op":"UNKNOWN_OP","val":"y"}]}
                """);
        assertFalse(matcher.matches(branch, Map.of("x", "y")));
    }

    @Test
    void malformedJsonReturnsFalse() {
        // 验证非法JSON返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("not valid json");
        assertFalse(matcher.matches(branch, Map.of("x", "y")));
    }

    @Test
    void andAllItemsMustMatch() {
        // 验证AND模式下所有条件必须同时满足
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"a","op":"eq","val":"1"},{"field":"b","op":"eq","val":"2"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("a", "1", "b", "2")));
        assertFalse(matcher.matches(branch, Map.of("a", "1", "b", "3")));
    }

    @Test
    void missingMatchFieldDefaultsToAnd() {
        // 验证match字段缺失时默认使用AND逻辑
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"items":[{"field":"a","op":"eq","val":"1"},{"field":"b","op":"eq","val":"2"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("a", "1", "b", "2")));
        assertFalse(matcher.matches(branch, Map.of("a", "1", "b", "3")));
    }

    @Test
    void eqTrimsActualValueBeforeComparison() {
        // 验证eq操作符比较前trim实际值
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"key","op":"eq","val":"hello"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("key", "  hello  ")));
    }

    @Test
    void eqNullActualReturnsFalse() {
        // 验证eq操作符当actual为null时返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"key","op":"eq","val":"hello"}]}
                """);
        assertFalse(matcher.matches(branch, Map.of()));
    }

    @Test
    void inOperatorWithEmptyCommaSeparatorHandled() {
        // 验证in操作符处理空值分隔符的边界情况
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"level","op":"in","val":"A,,B"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("level", "A")));
        assertTrue(matcher.matches(branch, Map.of("level", "B")));
    }

    // ===== NE (not equal) =====

    @Test
    void neOperatorReturnsTrueWhenValuesDiffer() {
        // 验证ne操作符：值不相等时返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"status","op":"ne","val":"OFFLINE"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("status", "ONLINE")));
        assertFalse(matcher.matches(branch, Map.of("status", "OFFLINE")));
    }

    // ===== GT (greater than) =====

    @Test
    void gtOperatorNumericComparison() {
        // 验证gt操作符：数值大于比较
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"count","op":"gt","val":"5"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("count", "10")));
        assertFalse(matcher.matches(branch, Map.of("count", "5")));
        assertFalse(matcher.matches(branch, Map.of("count", "3")));
    }

    // ===== GTE (greater than or equal) =====

    @Test
    void gteOperatorEqualsOrGreaterReturnsTrue() {
        // 验证gte操作符：大于或等于
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"count","op":"gte","val":"5"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("count", "5")));
        assertTrue(matcher.matches(branch, Map.of("count", "10")));
        assertFalse(matcher.matches(branch, Map.of("count", "3")));
    }

    // ===== LT (less than) =====

    @Test
    void ltOperatorNumericComparison() {
        // 验证lt操作符：数值小于比较
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"signalRssi","op":"lt","val":"-80"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("signalRssi", "-90")));
        assertFalse(matcher.matches(branch, Map.of("signalRssi", "-80")));
        assertFalse(matcher.matches(branch, Map.of("signalRssi", "-70")));
    }

    // ===== LTE (less than or equal) =====

    @Test
    void lteOperatorEqualsOrLessReturnsTrue() {
        // 验证lte操作符：小于或等于
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"signalRssi","op":"lte","val":"-80"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("signalRssi", "-80")));
        assertTrue(matcher.matches(branch, Map.of("signalRssi", "-90")));
        assertFalse(matcher.matches(branch, Map.of("signalRssi", "-70")));
    }

    // ===== BETWEEN =====

    @Test
    void betweenOperatorValueInRangeReturnsTrue() {
        // 验证between操作符：值在区间内返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"rssi","op":"between","val":"-90,-60"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("rssi", "-80")));
        assertTrue(matcher.matches(branch, Map.of("rssi", "-90")));
        assertTrue(matcher.matches(branch, Map.of("rssi", "-60")));
    }

    @Test
    void betweenOperatorValueOutOfRangeReturnsFalse() {
        // 验证between操作符：值在区间外返回false
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"rssi","op":"between","val":"-90,-60"}]}
                """);
        assertFalse(matcher.matches(branch, Map.of("rssi", "-100")));
        assertFalse(matcher.matches(branch, Map.of("rssi", "-50")));
    }

    // ===== NOT_EXISTS =====

    @Test
    void notExistsOperatorReturnsTrueWhenFieldAbsent() {
        // 验证not_exists操作符：字段不存在时返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"ticketId","op":"not_exists"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of()));
        assertTrue(matcher.matches(branch, Map.of("ticketId", "")));
        assertFalse(matcher.matches(branch, Map.of("ticketId", "TK001")));
    }

    // ===== BigDecimal precision =====

    @Test
    void decimalComparisonUsesBigDecimal() {
        // 验证小数比较使用BigDecimal保证精度
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"dataUsed","op":"gt","val":"1.5"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("dataUsed", "2.0")));
        assertFalse(matcher.matches(branch, Map.of("dataUsed", "1.5")));
        assertFalse(matcher.matches(branch, Map.of("dataUsed", "1.0")));
    }

    // ===== String fallback comparison =====

    @Test
    void nonNumericComparisonFallsBackToLexicographic() {
        // 验证非数值比较降级为字典序比较
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"version","op":"gt","val":"2.0"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("version", "2.1")));
        assertFalse(matcher.matches(branch, Map.of("version", "1.9")));
    }

    // ===== NE with null handling =====

    @Test
    void neReturnsTrueWhenActualNullAndExpectedNonNull() {
        // 验证ne操作符：actual为null且expected非空时返回true
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"key","op":"ne","val":"someValue"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of()));
    }

    // ===== Composite: AND with mixed operators =====

    @Test
    void andWithMixedOperatorsAllPass() {
        // 验证AND模式下混合操作符（gt + eq + exists）同时满足
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"AND","items":[{"field":"rssi","op":"gt","val":"-90"},{"field":"status","op":"eq","val":"ONLINE"},{"field":"deviceSn","op":"exists"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("rssi", "-70", "status", "ONLINE", "deviceSn", "SN001")));
        assertFalse(matcher.matches(branch, Map.of("rssi", "-95", "status", "ONLINE", "deviceSn", "SN001")));
    }

    // ===== OR with mixed operators =====

    @Test
    void orWithMixedOperatorsFirstFailSecondPass() {
        // 验证OR模式下混合操作符（ne + eq）第一个失败第二个通过
        KbBranch branch = new KbBranch();
        branch.setConditionJson("""
                {"match":"OR","items":[{"field":"status","op":"ne","val":"ONLINE"},{"field":"mode","op":"eq","val":"GUEST"}]}
                """);
        assertTrue(matcher.matches(branch, Map.of("status", "ONLINE", "mode", "GUEST")));
    }
}
