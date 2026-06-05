package cn.cloudcat.user.message.chat;

import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.user.common.sse.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * V2 用户端客服 API：/api/cs/chat/*
 *
 * @author Kang
 * @since 2026-06-02
 */
@RestController
@RequestMapping("/api/cs/v1/chat")
@Slf4j
@RequiredArgsConstructor
public class UserChatController {

    private final UserChatApplicationService chatService;
    private final UserChatActionService actionService;

    /** GET /api/cs/chat/quick-entries — 获取一键服务快捷入口 */
    @GetMapping("/quick-entries")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> listQuickEntries() {
        return chatService.listEntryBundle();
    }

    /** POST /api/cs/chat/sessions — 创建新会话，可选附带问题 */
    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> createSession(@RequestBody(required = false) Map<String, String> body) {
        String problem = body != null ? body.get("problem") : null;
        String deviceSn = body != null ? body.get("deviceSn") : null;
        if (deviceSn == null || deviceSn.isBlank()) {
            throw new IllegalArgumentException("deviceSn 不能为空");
        }
        return chatService.createSession(currentUserId(), deviceSn, problem);
    }

    /** GET /api/cs/chat/sessions/{id} — 获取会话状态视图（含剩余空闲秒数） */
    @GetMapping("/sessions/{conversationId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> getSession(@PathVariable String conversationId) {
        return chatService.getSession(conversationId, currentUserId());
    }

    /** GET /api/cs/chat/sessions/{id}/messages — 获取会话历史消息 */
    @GetMapping("/sessions/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public List<UserMessage> listMessages(@PathVariable String conversationId) {
        return chatService.listMessages(conversationId, currentUserId());
    }

    /** POST /api/cs/chat/messages — 发送消息，先尝试闲聊拦截，未命中返回 streamPath */
    @PostMapping("/messages")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> sendMessage(@RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        String text = body.get("text");
        return chatService.tryChitchatReply(conversationId, currentUserId(), text)
                .orElseGet(() -> Map.of(
                        "chitchat", false,
                        "conversationId", conversationId,
                        "streamPath", "/api/cs/chat/sessions/" + conversationId + "/stream?message=" + text
                ));
    }

    /** POST /api/cs/chat/sessions/{id}/stream — 流式对话（POST，JSON body） */
    @PostMapping(value = "/sessions/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Flux<String> streamPost(@PathVariable String conversationId,
                                   @RequestBody Map<String, String> body) {
        //todo body需要创建类来接收参数，不要用Map
        String message = body != null ? body.get("text") : null;
        String quickServiceCode = body != null ? body.get("quickServiceCode") : null;
        return chatService.stream(conversationId, currentUserId(), message, quickServiceCode)
                .onErrorResume(err -> {
                    log.error("stream error: conversationId={}", conversationId, err);
                    return Flux.just(
                            AgentResponse.error(err.getMessage()),
                            AgentResponse.complete()
                    );
                });
    }

    /** GET /api/cs/chat/sessions/{id}/stream — 流式对话（GET，query param，legacy） */
    @GetMapping(value = "/sessions/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Flux<String> stream(@PathVariable String conversationId,
                                                      @RequestParam String message,
                                                      @RequestParam(required = false) String quickServiceCode) {
        return chatService.stream(conversationId, currentUserId(), message, quickServiceCode)
                .onErrorResume(err -> {
                    log.error("stream error: conversationId={}", conversationId, err);
                    return Flux.just(
                            AgentResponse.error(err.getMessage()),
                            AgentResponse.complete()
                    );
                });
    }

    /** POST /api/cs/chat/sessions/{id}/stop — 取消/停止正在执行的 AI 流式任务 */
    @PostMapping("/sessions/{conversationId}/stop")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> stop(@PathVariable String conversationId) {
        return chatService.stop(conversationId);
    }

    /** POST /api/cs/chat/sessions/{id}/satisfaction — 提交满意度评价 */
    @PostMapping("/sessions/{conversationId}/satisfaction")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> submitSatisfaction(@PathVariable String conversationId,
                                                   @RequestBody Map<String, Object> body) {
        boolean useful = Boolean.TRUE.equals(body.get("useful"));
        return chatService.submitSatisfaction(conversationId, currentUserId(), useful);
    }

    /** POST /api/cs/chat/sessions/{id}/close — 主动关闭会话 */
    @PostMapping("/sessions/{conversationId}/close")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> close(@PathVariable String conversationId) {
        return chatService.closeSession(conversationId, currentUserId());
    }

    /** POST /api/cs/chat/sessions/{id}/actions — 执行设备操作（网络刷新/流量查询/重置/转人工） */
    @PostMapping("/sessions/{conversationId}/actions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> executeAction(@PathVariable String conversationId,
                                             @RequestBody Map<String, String> body) {
        String action = body != null ? body.get("action") : null;
        return actionService.executeAction(conversationId, currentUserId(), action);
    }

    /** POST /api/cs/chat/sessions/{id}/human — 显式转人工客服 */
    @PostMapping("/sessions/{conversationId}/human")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> transferHuman(@PathVariable String conversationId) {
        return actionService.transferHuman(conversationId, currentUserId());
    }

    /** 从 SecurityContext 获取当前用户 ID */
    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
