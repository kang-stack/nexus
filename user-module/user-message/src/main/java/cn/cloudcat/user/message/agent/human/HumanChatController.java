package cn.cloudcat.user.message.agent.human;

import cn.cloudcat.user.common.sse.AgentResponse;
import cn.cloudcat.user.message.agent.service.AgentTaskManager;
import cn.cloudcat.user.session.service.UserMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端人工客服接口。
 * 客户通过 SSE 接收人工消息，通过 POST 发送消息给人工。
 */
@RestController
@RequestMapping("/api/portal/human")
@Slf4j
public class HumanChatController {

    private final HumanChatSessionManager sessionManager;
    private final AgentTaskManager taskManager;
    private final UserMessageService messageService;

    /**
     * conversationId → Sinks.Many（客户端 SSE 输出）
     */
    private static final Map<String, Sinks.Many<String>> customerSinks = new ConcurrentHashMap<>();

    public HumanChatController(HumanChatSessionManager sessionManager,
                                AgentTaskManager taskManager,
                                UserMessageService messageService) {
        this.sessionManager = sessionManager;
        this.taskManager = taskManager;
        this.messageService = messageService;
    }

    /**
     * 瀹㈡埛鍙戣捣杞汉宸ヨ姹傘€?     */
    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> transfer(@RequestParam String conversationId) {
        String userId = getCurrentUserId();

        // 鍏堝仠姝?AI 姝ｅ湪鎵ц鐨勪换鍔?        taskManager.stopTask(conversationId);

        long position = sessionManager.enqueueCustomer(conversationId, userId);

        log.info("transfer to human: conversationId={}, userId={}, position={}",
                conversationId, userId, position);
        return Map.of("success", true, "position", position,
                "message", "宸插姞鍏ユ帓闃燂紝褰撳墠浣嶇疆: " + position);
    }

    /**
     * 瀹㈡埛 SSE 娴?鈥?鎺ユ敹浜哄伐瀹㈡湇娑堟伅銆?     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Flux<String> stream(@RequestParam String conversationId) {
        // 妫€鏌ユ槸鍚﹀凡寤虹珛 sink
        Sinks.Many<String> existing = customerSinks.get(conversationId);
        if (existing != null) {
            return existing.asFlux();
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        customerSinks.put(conversationId, sink);

        long position = sessionManager.getQueuePosition(conversationId);
        if (position > 0) {
            sink.tryEmitNext(AgentResponse.humanQueued((int) position));
        }

        var info = sessionManager.getSessionInfo(conversationId);
        if (info != null && "ACTIVE".equals(info.getString("status"))) {
            sink.tryEmitNext(AgentResponse.humanConnected(
                    info.getString("humanAgentName")));
        }

        return sink.asFlux()
                .doOnCancel(() -> cleanupSink(conversationId))
                .doFinally(s -> cleanupSink(conversationId));
    }

    /**
     * 瀹㈡埛鍙戦€佹秷鎭粰浜哄伐瀹㈡湇銆?     */
    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> sendMessage(@RequestParam String conversationId,
                                            @RequestParam String content) {
        messageService.saveUserMessage(conversationId, content, null);
        // 娑堟伅鎶曢€掔敱 WebSocket 渚х殑 HumanChatWebSocketHandler 杞彂
        // 姝ゆ帴鍙ｄ富瑕佺敤浜庢寔涔呭寲瀹㈡埛娑堟伅
        log.info("customer message saved: conversationId={}", conversationId);
        return Map.of("success", true);
    }

    /**
     * 瀹㈡埛鍙栨秷鎺掗槦銆?     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> cancel(@RequestParam String conversationId) {
        sessionManager.removeFromQueue(conversationId);
        cleanupSink(conversationId);
        return Map.of("success", true, "message", "已取消排队");
    }

    // ---- 鍐呴儴鏂规硶 ----

    private void cleanupSink(String conversationId) {
        Sinks.Many<String> sink = customerSinks.remove(conversationId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /**
     * 鐢?HumanChatWebSocketHandler 璋冪敤锛屽皢浜哄伐娑堟伅鎶曢€掑埌瀹㈡埛 SSE 娴併€?     */
    public static void deliverToCustomer(String conversationId, String message) {
        Sinks.Many<String> sink = customerSinks.get(conversationId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
