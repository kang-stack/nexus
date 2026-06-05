package cn.cloudcat.user.message.chat;

import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SatisfactionStatus;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.session.service.UserMessageService;
import cn.cloudcat.user.session.service.UserMessageServiceImpl;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.user.knowledge.chitchat.ChitchatInterceptService;
import cn.cloudcat.user.common.sse.AgentResponse;
import cn.cloudcat.user.message.agent.service.AgentTaskManager;
import cn.cloudcat.user.message.chitchat.ChitchatFaqChipService;
import cn.cloudcat.user.message.entry.QuickEntryService;
import cn.cloudcat.user.message.session.UserSessionTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CS 聊天应用服务
 * <p>
 * 用户侧聊天 API 的 Service 层，负责会话创建、消息列表、闲聊拦截、
 * 流式对话、满意度提交和会话关闭。流式对话委托给 UserChatTurnOrchestrator。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserChatApplicationService {

    private final UserConversationService conversationService;
    private final UserMessageService messageService;
    private final ChitchatInterceptService chitchatInterceptService;
    private final UserChatTurnOrchestrator turnOrchestrator;
    private final UserSessionTimeoutService sessionTimeoutService;
    private final AgentTaskManager taskManager;
    private final QuickEntryService quickEntryService;
    private final ChitchatFaqChipService faqChipService;

    /** 创建会话：无问题时返回欢迎语，有问题时保存用户消息并标记需要流式回复 */
    @Transactional
    public Map<String, Object> createSession(String userId, String deviceSn, String problem) {
        UserConversation conv = conversationService.create(userId, deviceSn, problem);
        sessionTimeoutService.refreshIdleTimer(conv.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conv.getId());

        if (problem == null || problem.isBlank()) {
            // 无问题 → 返回欢迎语
            UserMessage greeting = messageService.saveAssistantText(
                    conv.getId(), "您好~WiFi智能客服为您服务！请问有什么可以帮您？", "SYSTEM");
            result.put("greeting", greeting.getContent());
        } else {
            // 带问题进入 → 保存用户消息，标记需要流式回复
            messageService.saveUserMessage(conv.getId(), problem, null);
            conversationService.onUserMessage(conv, false);
            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("content", problem);
            result.put("userMessage", userMsg);
            result.put("needStream", true);
        }
        return result;
    }

    /** 获取会话视图（含状态、意图等） */
    public Map<String, Object> getSession(String conversationId, String userId) {
        return turnOrchestrator.sessionView(conversationId, userId);
    }

    /** 获取会话消息列表 */
    public List<UserMessage> listMessages(String conversationId, String userId) {
        conversationService.getForUser(conversationId, userId);
        return messageService.listByConversation(conversationId);
    }

    /** 尝试闲聊拦截：命中则返回预设回复 + FAQ 入口，未命中返回 empty */
    @Transactional
    public Optional<Map<String, Object>> tryChitchatReply(String conversationId, String userId, String text) {
        UserConversation conv = conversationService.getForUser(conversationId, userId);
        Optional<ChitchatInterceptService.ChitchatHit> hit = chitchatInterceptService.tryIntercept(text, conv);
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        messageService.saveUserMessage(conversationId, text, null);
        conversationService.onUserMessage(conv, true);

        ChitchatInterceptService.ChitchatHit chitchat = hit.get();
        List<Map<String, Object>> links = chitchat.appendFaqChips() ? faqChipService.buildChips() : List.of();
        String payloadJson = MessagePayloadUtil.toPayloadJson(links);
        UserMessage reply = payloadJson != null
                ? messageService.saveAssistantStructured(
                        conversationId, chitchat.replyText(), UserMessageServiceImpl.SOURCE_CHITCHAT, payloadJson)
                : messageService.saveAssistantText(
                        conversationId, chitchat.replyText(), UserMessageServiceImpl.SOURCE_CHITCHAT);
        conversationService.onChitchatReply(conv, chitchat.categoryCode());
        sessionTimeoutService.refreshIdleTimer(conversationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chitchat", true);
        payload.put("message", toMessageView(reply, links));
        return Optional.of(payload);
    }

    /** V2 主聊天流入口：委托给 turnOrchestrator，外层统一兜底异常为 SSE error 事件 */
    public Flux<String> stream(String conversationId, String userId, String message, String quickServiceCode) {
        return turnOrchestrator.streamTurn(conversationId, userId, message, quickServiceCode)
                .onErrorResume(err -> {
                    log.error("cs stream error: conversationId={}", conversationId, err);
                    return Flux.just(AgentResponse.error(err.getMessage()), AgentResponse.complete());
                });
    }

    /** 提交满意度评价，关闭会话 */
    @Transactional
    public Map<String, Object> submitSatisfaction(String conversationId, String userId, boolean useful) {
        UserConversation conv = conversationService.getForUser(conversationId, userId);
        conv.setSatisfactionStatus(SatisfactionStatus.DONE.name());
        conversationService.applyStatus(conv, ConversationStatus.CLOSED.name());
        UserMessage card = messageService.saveSatisfactionCard(conversationId);
        return Map.of("success", true, "messageId", card.getId(), "useful", useful);
    }

    /** 主动关闭会话 */
    @Transactional
    public Map<String, Object> closeSession(String conversationId, String userId) {
        UserConversation conv = conversationService.getForUser(conversationId, userId);
        conversationService.close(conv, "USER_CLOSE");
        return Map.of("success", true);
    }

    /** 获取入口数据包：一键服务快捷入口（原「猜你想问」已移除，后续由 LLM 推荐） */
    public Map<String, Object> listEntryBundle() {
        return Map.of("quickEntries", quickEntryService.listEntries());
    }

    /** 获取快捷入口列表 */
    public List<Map<String, String>> listQuickEntries() {
        return quickEntryService.listEntries();
    }

    /** 停止当前流式 AI 任务 */
    public Map<String, Object> stop(String conversationId) {
        boolean success = taskManager.stopTask(conversationId);
        return Map.of("success", success,
                "message", success ? "已停止执行" : "没有找到正在执行的任务");
    }

    private Map<String, Object> toMessageView(UserMessage msg) {
        return toMessageView(msg, null);
    }

    private Map<String, Object> toMessageView(UserMessage msg, List<Map<String, Object>> linksOverride) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", msg.getId());
        view.put("role", msg.getRole());
        view.put("contentType", msg.getContentType());
        view.put("content", msg.getContent());
        view.put("replySource", msg.getReplySource());
        view.put("createdAt", msg.getCreatedAt());
        if (linksOverride != null && !linksOverride.isEmpty()) {
            view.put("links", linksOverride);
        } else {
            Map<String, Object> parsed = MessagePayloadUtil.parsePayload(msg.getPayloadJson());
            if (parsed.containsKey("links")) {
                view.put("links", parsed.get("links"));
            }
            if (parsed.containsKey("button")) {
                view.put("button", parsed.get("button"));
            }
        }
        return view;
    }

    private static String intentHint(String message, String quickServiceCode) {
        if (quickServiceCode != null && !quickServiceCode.isBlank()) {
            return quickServiceCode;
        }
        return message;
    }
}