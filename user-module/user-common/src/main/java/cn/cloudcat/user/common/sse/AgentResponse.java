package cn.cloudcat.user.common.sse;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * Agent SSE 流式输出统一响应模型。
 * <p>
 * 每个事件序列化为单行 JSON：{"type":"...","content":"...","data":{}}。
 * 前端根据 type 字段分发渲染（text → 追加文本, message → 整条消息, typing → 输入中动画等）。
 */
public class AgentResponse {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_REFERENCE = "reference";
    public static final String TYPE_RECOMMEND = "recommend";
    public static final String TYPE_PROGRESS = "progress";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_SESSION = "session";
    public static final String TYPE_COMPLETE = "complete";
    public static final String TYPE_STOPPED = "stopped";
    public static final String TYPE_HUMAN_TEXT = "human_text";
    public static final String TYPE_HUMAN_TYPING = "human_typing";
    public static final String TYPE_HUMAN_QUEUED = "human_queued";
    public static final String TYPE_HUMAN_CONNECTED = "human_connected";
    public static final String TYPE_HUMAN_CLOSED = "human_closed";
    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_TYPING = "typing";

    private final String type;
    private final String content;
    private final Integer count;
    private final Object data;

    public AgentResponse(String type, String content) {
        this(type, content, null, null);
    }

    public AgentResponse(String type, String content, Integer count) {
        this(type, content, count, null);
    }

    public AgentResponse(String type, String content, Integer count, Object data) {
        this.type = type;
        this.content = content;
        this.count = count;
        this.data = data;
    }

    public static String text(String content) {
        return new AgentResponse(TYPE_TEXT, content).toJson();
    }

    public static String thinking(String content) {
        return new AgentResponse(TYPE_THINKING, content).toJson();
    }

    public static String progress(String content) {
        return new AgentResponse(TYPE_PROGRESS, content).toJson();
    }

    public static String session(String conversationId) {
        return new AgentResponse(TYPE_SESSION, conversationId).toJson();
    }

    public static String error(String content) {
        return new AgentResponse(TYPE_ERROR, content).toJson();
    }

    public static String complete() {
        return new AgentResponse(TYPE_COMPLETE, null).toJson();
    }

    public static String complete(Map<String, Object> data) {
        return new AgentResponse(TYPE_COMPLETE, null, null, data).toJson();
    }

    public static String stopped(String partialContent) {
        return new AgentResponse(TYPE_STOPPED, partialContent).toJson();
    }

    public static String reference(String content) {
        int cnt = 0;
        try {
            var arr = JSON.parseArray(content);
            if (arr != null) cnt = arr.size();
        } catch (Exception ignored) {
        }
        return new AgentResponse(TYPE_REFERENCE, content, cnt).toJson();
    }

    public static String recommend(String content) {
        return new AgentResponse(TYPE_RECOMMEND, content).toJson();
    }

    public static String humanText(String content) {
        return new AgentResponse(TYPE_HUMAN_TEXT, content).toJson();
    }

    public static String humanTyping(boolean typing) {
        return new AgentResponse(TYPE_HUMAN_TYPING, null, null, typing).toJson();
    }

    public static String humanQueued(int position) {
        return new AgentResponse(TYPE_HUMAN_QUEUED, null, position).toJson();
    }

    public static String humanConnected(String agentName) {
        return new AgentResponse(TYPE_HUMAN_CONNECTED, agentName).toJson();
    }

    public static String humanClosed() {
        return new AgentResponse(TYPE_HUMAN_CLOSED, null).toJson();
    }

    public static String message(Object data) {
        return new AgentResponse(TYPE_MESSAGE, null, null, data).toJson();
    }

    public static String typing() {
        return new AgentResponse(TYPE_TYPING, null).toJson();
    }

    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        if (content != null) obj.put("content", content);
        if (count != null) obj.put("count", count);
        if (data != null) {
            if (data instanceof String s) {
                try {
                    obj.put("data", JSON.parse(s));
                } catch (Exception e) {
                    obj.put("data", s);
                }
            } else {
                obj.put("data", data);
            }
        }
        return obj.toJSONString();
    }
}
