package cn.cloudcat.user.knowledge.chitchat;

/**
 * 寒暄/别名匹配前的文本归一化（V2 §5）。
 * <p>
 * 归一化步骤：去除首尾空白 → 全角转半角（英文字符区间 → 转小写 → 压缩连续空白为单个空格。
 */
public final class TextNormalize {

    private TextNormalize() {}

    /** 对输入文本执行全角转半角 + 转小写 + 空白压缩的归一化处理 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // \u5168\u89d2\u82f1\u6587\u5b57\u7b26\u533a\u95f4 (0xFF01-0xFF5E) \u2192 \u534a\u89d2 (0x0021-0x007E)\uff0c\u504f\u79fb\u91cf 0xFEE0
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\u3000') {
                // \u5168\u89d2\u7a7a\u683c (0x3000) \u2192 \u534a\u89d2\u7a7a\u683c
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        // \u7edf\u4e00\u8f6c\u5c0f\u5199\u540e\u538b\u7f29\u8fde\u7eed\u7a7a\u767d\u4e3a\u5355\u4e2a\u7a7a\u683c
        s = sb.toString().toLowerCase();
        return s.replaceAll("\\s+", " ").trim();
    }
}
