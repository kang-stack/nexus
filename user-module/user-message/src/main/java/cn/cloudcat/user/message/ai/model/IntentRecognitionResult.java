package cn.cloudcat.user.message.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record IntentRecognitionResult(
        @JsonPropertyDescription("意图识别的推断理由")
        String reasoning,
        @JsonPropertyDescription("是否与随身 WiFi 领域相关")
        boolean related,
        @JsonPropertyDescription("识别出的意图")
        String intent,
        @JsonPropertyDescription("复合意图列表")
        List<IntentItem> intents,
        @JsonPropertyDescription("提取的实体信息")
        Entities entities) {

    public record IntentItem(String intent, String span) {
    }

    public record Entities(
            String device_model,
            String device_id,
            String order_id,
            String fault_description,
            String plan_type,
            String function_name,
            String plan_name,
            String phone_number) {
    }

    public IntentRecognitionResult normalized() {
        if (intents != null && !intents.isEmpty()) {
            return this;
        }
        if (intent == null || intent.isBlank()) {
            return unrelated();
        }
        return new IntentRecognitionResult(reasoning, related, intent, List.of(new IntentItem(intent, null)), entities);
    }

    public boolean isCompound() {
        return intents != null && intents.size() > 1;
    }

    public static IntentRecognitionResult unrelated() {
        return new IntentRecognitionResult("unrelated", false, null, List.of(), null);
    }

    public static IntentRecognitionResult quickService(String intent, String userText) {
        return new IntentRecognitionResult(
                "quick-entry:" + userText,
                true,
                intent,
                List.of(new IntentItem(intent, userText)),
                null);
    }
}
