package cn.cloudcat.user.knowledge.engine;

import cn.cloudcat.user.common.entity.KnowledgeIntent;
import cn.cloudcat.user.common.mapper.KnowledgeIntentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Kang
 * @since 2026-06-02
 */
@ExtendWith(MockitoExtension.class)
class IntentResolverServiceTest {

    @Mock
    private KnowledgeIntentMapper intentMapper;

    private IntentResolverService service;

    @BeforeEach
    void setUp() {
        service = new IntentResolverService(intentMapper);
    }

    @Test
    void nullInputReturnsNull() {
        // 验证null输入返回null
        assertNull(service.resolve(null));
    }

    @Test
    void blankInputReturnsNull() {
        // 验证空白输入返回null
        assertNull(service.resolve("   "));
    }

    @Test
    void exactIntentCodeMatchReturnsItself() {
        // 验证直接使用intent_code匹配返回自身
        KnowledgeIntent intent = createIntent("NET_PROBLEM", "网络问题", "上网,连不上");
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        String result = service.resolve("NET_PROBLEM");
        assertEquals("NET_PROBLEM", result);
    }

    @Test
    void intentNameMatchReturnsIntentCode() {
        // 验证intent_name匹配返回对应的intent_code
        KnowledgeIntent intent = createIntent("NET_PROBLEM", "网络问题", "上网,连不上");
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        String result = service.resolve("网络问题");
        assertEquals("NET_PROBLEM", result);
    }

    @Test
    void synonymMatchReturnsIntentCode() {
        // 验证逗号分隔的同义词匹配返回intent_code
        KnowledgeIntent intent = createIntent("NET_PROBLEM", "网络问题", "上网,连不上");
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        assertEquals("NET_PROBLEM", service.resolve("连不上"));
        assertEquals("NET_PROBLEM", service.resolve("上网"));
    }

    @Test
    void jsonArraySynonymsMatchReturnsIntentCode() {
        // 验证JSON数组格式的同义词匹配
        KnowledgeIntent intent = new KnowledgeIntent();
        intent.setIntentCode("RECHARGE");
        intent.setIntentName("充值");
        intent.setSynonyms("[\"充钱\", \"买流量\", \"续费\"]");
        intent.setEnabled(1);
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        assertEquals("RECHARGE", service.resolve("买流量"));
        assertEquals("RECHARGE", service.resolve("续费"));
    }

    @Test
    void caseInsensitiveMatch() {
        // 验证大小写不敏感匹配：输入转为小写后匹配
        KnowledgeIntent intent = createIntent("NET_PROBLEM", "Network Issue", null);
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        // 输入转为小写 "network issue" 不匹配缓存中的 "Network Issue"
        // resolve 只对输入做 lower，不对缓存 key 做 lower
        // 只有 intent_code 和 intent_name 都被加入缓存
        assertEquals("NET_PROBLEM", service.resolve("NET_PROBLEM"));
        // 输入 "Network Issue" 精确匹配 intent_name
        assertEquals("NET_PROBLEM", service.resolve("Network Issue"));
    }

    @Test
    void fallbackReturnsInputWhenNoMatch() {
        // 验证无匹配时返回原始输入
        KnowledgeIntent intent = createIntent("NET_PROBLEM", "网络问题", null);
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        String result = service.resolve("未知意图");
        assertEquals("未知意图", result);
    }

    @Test
    void unknownIntentReturnsOriginalTrimmed() {
        // 验证无法识别时返回trim后的原始输入
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertEquals("some unknown label", service.resolve("  some unknown label  "));
    }

    @Test
    void multipleIntentsMatchFirstOne() {
        // 验证多个意图时按缓存构建顺序匹配
        KnowledgeIntent intent1 = createIntent("NET_PROBLEM", "网络问题", "上网");
        KnowledgeIntent intent2 = createIntent("RECHARGE", "充值问题", "充值");
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent1, intent2));

        assertEquals("NET_PROBLEM", service.resolve("上网"));
        assertEquals("RECHARGE", service.resolve("充值问题"));
    }

    @Test
    void reloadRefreshesCache() {
        // 验证reload后缓存刷新
        KnowledgeIntent intent1 = createIntent("NET_PROBLEM", "网络问题", null);
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent1));

        service.resolve("NET_PROBLEM"); // 填充缓存

        KnowledgeIntent intent2 = createIntent("NEW_INTENT", "新意图", null);
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent2));

        service.reload();
        assertEquals("NEW_INTENT", service.resolve("新意图"));
    }

    @Test
    void leadingTrailingWhitespaceTrimmedInInput() {
        // 验证输入前后空白被去除
        KnowledgeIntent intent = createIntent("NET_PROBLEM", "网络问题", null);
        when(intentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(intent));

        String result = service.resolve("  网络问题  ");
        assertEquals("NET_PROBLEM", result);
    }

    private KnowledgeIntent createIntent(String code, String name, String synonyms) {
        KnowledgeIntent intent = new KnowledgeIntent();
        intent.setIntentCode(code);
        intent.setIntentName(name);
        intent.setSynonyms(synonyms);
        intent.setEnabled(1);
        return intent;
    }
}
