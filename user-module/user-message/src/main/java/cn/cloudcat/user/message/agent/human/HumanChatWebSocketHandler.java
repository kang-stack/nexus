package cn.cloudcat.user.message.agent.human;

import cn.cloudcat.user.common.sse.AgentResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工客服 WebSocket 处理器。
 * 客服通过 "/ws/human/agent?token=xxx" 连接。
 */
@Slf4j
@Component
public class HumanChatWebSocketHandler extends TextWebSocketHandler {

    private final HumanChatSessionManager sessionManager;

    /**
     * agentId → WebSocketSession
     */
    private final Map<String, WebSocketSession> onlineAgents = new ConcurrentHashMap<>();
    /**
     * agentId → 当前服务的 conversationId
     */
    private final Map<String, String> agentServing = new ConcurrentHashMap<>();

    public HumanChatWebSocketHandler(HumanChatSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String agentId = (String) session.getAttributes().get("agentId");
        if (agentId == null) {
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) {}
            return;
        }
        onlineAgents.put(agentId, session);
        log.info("human agent connected: agentId={}", agentId);

        sendToAgent(agentId, queueUpdateMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String agentId = (String) session.getAttributes().get("agentId");
        if (agentId == null) return;

        String payload = message.getPayload();
        JSONObject msg;
        try {
            msg = JSON.parseObject(payload);
        } catch (Exception e) {
            log.warn("invalid ws message: {}", payload);
            return;
        }

        String action = msg.getString("action");
        switch (action) {
            case "queue_status" -> sendToAgent(agentId, queueUpdateMessage());
            case "accept_next" -> handleAcceptNext(agentId);
            case "send_message" -> handleSendMessage(agentId, msg);
            case "typing" -> handleTyping(agentId, msg);
            case "close_session" -> handleCloseSession(agentId);
            default -> log.warn("unknown action: {}", action);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String agentId = (String) session.getAttributes().get("agentId");
        if (agentId != null) {
            onlineAgents.remove(agentId);
            String serving = agentServing.remove(agentId);
            if (serving != null) {
                sessionManager.closeSession(serving);
                log.info("agent disconnected, closed session: agentId={}, conversationId={}", agentId, serving);
            }
            log.info("human agent disconnected: agentId={}", agentId);
        }
    }

    // ---- actions ----

    private void handleAcceptNext(String agentId) {
        String conversationId = sessionManager.dequeueNext();
        if (conversationId == null) {
            sendToAgent(agentId, "{\"type\":\"no_customer\"}");
            return;
        }
        agentServing.put(agentId, conversationId);
        sessionManager.createSession(conversationId, agentId, agentId);

        sendToAgent(agentId, String.format(
                "{\"type\":\"session_assigned\",\"conversationId\":\"%s\"}", conversationId));

        // 閫氱煡鎵€鏈夊鏈嶉槦鍒楀彉鍖?        broadcastQueueUpdate();
    }

    private void handleSendMessage(String agentId, JSONObject msg) {
        String conversationId = msg.getString("conversationId");
        String content = msg.getString("content");
        if (conversationId == null || content == null) return;

        // 娑堟伅閫氳繃 HumanChatController 鐨?SSE sink 鍙戦€佺粰瀹㈡埛
        // 杩欓噷灏嗘秷鎭啓鍏?Redis 鎴栫洿鎺ラ€氳繃 SSE 鍙戦€?        // 瀹為檯瀹炵幇锛氫娇鐢?Sinks 鏄犲皠锛岀敱 HumanChatController 绠＄悊
        sendCustomerMessage(conversationId, AgentResponse.humanText(content));
    }

    private void handleTyping(String agentId, JSONObject msg) {
        String conversationId = msg.getString("conversationId");
        Boolean typing = msg.getBoolean("typing");
        if (conversationId == null) return;
        sendCustomerMessage(conversationId,
                AgentResponse.humanTyping(typing != null && typing));
    }

    private void handleCloseSession(String agentId) {
        String conversationId = agentServing.remove(agentId);
        if (conversationId != null) {
            sendCustomerMessage(conversationId, AgentResponse.humanClosed());
            sessionManager.closeSession(conversationId);
        }
        sendToAgent(agentId, queueUpdateMessage());
    }

    // ---- helpers ----

    private void sendToAgent(String agentId, String message) {
        WebSocketSession session = onlineAgents.get(agentId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.warn("send to agent fail: agentId={}", agentId, e);
            }
        }
    }

    private void broadcastQueueUpdate() {
        String msg = queueUpdateMessage();
        for (String agentId : onlineAgents.keySet()) {
            sendToAgent(agentId, msg);
        }
    }

    private String queueUpdateMessage() {
        long len = sessionManager.getQueueLength();
        return String.format("{\"type\":\"queue_update\",\"length\":%d}", len);
    }

    /**
     * 鍚戝鎴峰彂閫佹秷鎭€傞€氳繃 HumanChatController 涓鐞嗙殑 Sinks 鏄犲皠瀹炵幇銆?     */
    private void sendCustomerMessage(String conversationId, String message) {
        HumanChatController.deliverToCustomer(conversationId, message);
    }

    public boolean isAgentOnline(String agentId) {
        return onlineAgents.containsKey(agentId);
    }
}
