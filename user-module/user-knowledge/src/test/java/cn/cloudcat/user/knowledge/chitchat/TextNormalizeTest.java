package cn.cloudcat.user.knowledge.chitchat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Kang
 * @since 2026-06-02
 */
class TextNormalizeTest {

    @Test
    void nullInputReturnsEmptyString() {
        // 验证null输入返回空字符串
        assertEquals("", TextNormalize.normalize(null));
    }

    @Test
    void emptyInputReturnsEmptyString() {
        // 验证空字符串输入返回空字符串
        assertEquals("", TextNormalize.normalize(""));
    }

    @Test
    void blankInputReturnsBlank() {
        // 验证纯空白输入返回空字符串
        assertEquals("", TextNormalize.normalize("   "));
    }

    @Test
    void fullwidthLettersConvertedToHalfwidth() {
        // 验证全角字母转半角
        String result = TextNormalize.normalize("Ｈｅｌｌｏ");
        assertEquals("hello", result);
    }

    @Test
    void fullwidthDigitsConvertedToHalfwidth() {
        // 验证全角数字转半角
        String result = TextNormalize.normalize("１２３");
        assertEquals("123", result);
    }

    @Test
    void fullwidthPunctuationConvertedToHalfwidth() {
        // 验证全角标点转半角
        String result = TextNormalize.normalize("你好！");
        assertEquals("你好!", result);
    }

    @Test
    void fullwidthSpaceConvertedToHalfwidth() {
        // 验证全角空格(3000)转半角空格
        String result = TextNormalize.normalize("你好　世界");
        assertEquals("你好 世界", result);
    }

    @Test
    void uppercaseConvertedToLowercase() {
        // 验证大写字母转小写
        assertEquals("hello world", TextNormalize.normalize("HELLO WORLD"));
    }

    @Test
    void multipleSpacesCollapsedToSingle() {
        // 验证多空格合并为单空格
        assertEquals("a b c", TextNormalize.normalize("a   b    c"));
    }

    @Test
    void leadingTrailingWhitespaceTrimmed() {
        // 验证首尾空白去除
        assertEquals("hello", TextNormalize.normalize("  hello  "));
    }

    @Test
    void chineseGreetingNormalized() {
        // 验证中文问候语保留原样（除标点和空格外）
        String result = TextNormalize.normalize("你好，在吗");
        assertEquals("你好,在吗", result);
    }

    @Test
    void mixedFullwidthHalfwidthNormalized() {
        // 验证混合全角半角文本正确归一化
        String result = TextNormalize.normalize("ＡＢＣabc１２３");
        assertEquals("abcabc123", result);
    }
}
