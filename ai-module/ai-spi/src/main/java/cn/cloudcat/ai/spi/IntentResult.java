package cn.cloudcat.ai.spi;

import java.util.List;

public record IntentResult(
        boolean related,
        List<String> intentCodes
) {
    public static IntentResult unrelated() {
        return new IntentResult(false, List.of());
    }
}
