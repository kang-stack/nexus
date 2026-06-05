package cn.cloudcat.user.message.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * acc_user_message.payload_json 工具类。
 * <p>
 * 将前端渲染所需的 links（按钮列表）和 button（单按钮）序列化为 JSON，
 * 以及反向解析用于消息视图展示。
 */
final class MessagePayloadUtil {

    private MessagePayloadUtil() {}

    // 仅构建 links（无 button 时的简便调用）
    static String toPayloadJson(List<Map<String, Object>> links) {
        return toPayloadJson(links, null);
    }

    // 将 links + button 合并为 JSON payload；两者均为空时返回 null（不写 payload）
    static String toPayloadJson(List<Map<String, Object>> links, Map<String, Object> button) {
        if ((links == null || links.isEmpty()) && (button == null || button.isEmpty())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (links != null && !links.isEmpty()) {
            payload.put("links", links);
        }
        if (button != null && !button.isEmpty()) {
            payload.put("button", button);
        }
        return JSON.toJSONString(payload);
    }

    // 解析 payload_json 为前端可直接使用的 Map（提取 links/button）
    static Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject obj = JSON.parseObject(payloadJson);
            Map<String, Object> view = new LinkedHashMap<>();
            if (obj.containsKey("links")) {
                view.put("links", obj.get("links"));
            }
            if (obj.containsKey("button")) {
                view.put("button", obj.get("button"));
            }
            return view;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
