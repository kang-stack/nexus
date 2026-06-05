package cn.cloudcat.user.message.search;

import cn.cloudcat.user.session.service.UserChatTurnTraceService;
import cn.cloudcat.user.message.review.entity.UserMessageReview;
import cn.cloudcat.user.message.review.UserMessageReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息管理 Admin 控制器
 * <p>
 * 提供消息搜索和消息审核功能，映射到 /api/admin/messages 和 /api/cs/admin/messages。
 *
 * @author Kang
 * @since 2026-06-02
 */
@RestController
@RequestMapping("/api/cs/v1/admin/messages")
@RequiredArgsConstructor
public class UserMessageAdminController {

    private final UserMessageSearchService messageSearchService;
    private final UserMessageReviewService messageReviewService;
    private final UserChatTurnTraceService turnTraceService;

    /** GET /api/admin/messages/traces — 分页查询决策轨迹（默认全量，created_at 倒序；可选 conversationId 筛选） */
    @GetMapping("/traces")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> listTraces(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return turnTraceService.pageTraces(conversationId, page, size);
    }

    /** GET /api/admin/messages/traces/{turnId} — 单轮决策轨迹详情 */
    @GetMapping("/traces/{turnId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> getTrace(@PathVariable String turnId) {
        return turnTraceService.getByTurnId(turnId)
                .map(turnTraceService::toView)
                .orElse(Map.of("error", "trace not found", "turnId", turnId));
    }

    /** GET /api/admin/messages/search — 全文搜索聊天消息（ES + MySQL 降级） */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = messageSearchService.search(keyword, conversationId, role, page, size);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** GET /api/admin/messages/reviews — 分页查询待审核消息列表 */
    @GetMapping("/reviews")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> listReviews(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", messageReviewService.listReviews(status, page, size));
        result.put("total", messageReviewService.countReviews(status));
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** PUT /api/admin/messages/reviews/{id} — 管理员审核消息（批准/驳回） */
    @PutMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public UserMessageReview decideReview(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("reviewStatus");
        String note = body.get("reviewNote");
        Long reviewerId = currentUserId();
        return messageReviewService.decide(id, status, note, reviewerId);
    }

    /** 从 SecurityContext 获取当前管理员用户 ID */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
