package cn.cloudcat.user.message.review;

import cn.cloudcat.user.common.constant.EmotionTag;
import cn.cloudcat.user.common.constant.RiskLevel;
import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.user.message.review.entity.UserMessageReview;
import cn.cloudcat.user.session.mapper.UserMessageMapper;
import cn.cloudcat.user.message.review.mapper.UserMessageReviewMapper;
import cn.cloudcat.user.session.service.UserMessageServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息审核服务
 * <p>
 * 对 AI 生成的助手消息（BRANCH/RAG 来源）在高风险情绪场景下自动标记为 FLAGGED，
 * 供管理员审核（APPROVED / REJECTED）。审核结果通过 UserMessageReviewLifecycleListener
 * 触发后继动作（如删除消息、通知等）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserMessageReviewService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FLAGGED = "FLAGGED";

    private final UserMessageReviewMapper reviewMapper;
    private final UserMessageMapper messageMapper;

    /** 高风险消息自动入队：仅 BRANCH/RAG 来源的 assistant 消息在 HIGH 风险/辱骂/愤怒时标记 */
    @Transactional
    public void enqueueIfNeeded(UserMessage message, String emotionTag, String emotionRisk) {
        if (message == null || message.getId() == null) {
            return;
        }
        // 仅审核 AI 生成的助手消息
        if (!UserMessageServiceImpl.ROLE_ASSISTANT.equals(message.getRole())) {
            return;
        }
        // 仅审核 BRANCH（知识分支）和 RAG 来源的消息
        String source = message.getReplySource();
        if (source == null || (!UserMessageServiceImpl.SOURCE_BRANCH.equals(source)
                && !UserMessageServiceImpl.SOURCE_RAG.equals(source))) {
            return;
        }
        // 仅高风险情绪触发审核
        boolean flagged = RiskLevel.HIGH.name().equals(emotionRisk)
                || EmotionTag.ABUSE.name().equals(emotionTag)
                || EmotionTag.ANGRY.name().equals(emotionTag);
        if (!flagged) {
            return;
        }
        // 防重复入队
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<UserMessageReview>()
                .eq(UserMessageReview::getMessageId, message.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        UserMessageReview row = new UserMessageReview();
        row.setMessageId(message.getId());
        row.setConversationId(message.getConversationId());
        row.setReviewStatus(STATUS_FLAGGED);
        row.setRiskLevel(emotionRisk != null ? emotionRisk : RiskLevel.MEDIUM.name());
        row.setEmotionTag(emotionTag);
        reviewMapper.insert(row);
        log.debug("message review queued messageId={} emotion={}", message.getId(), emotionTag);
    }

    /** 分页查询审核列表，支持按状态过滤 */
    public List<Map<String, Object>> listReviews(String status, int page, int size) {
        LambdaQueryWrapper<UserMessageReview> q = new LambdaQueryWrapper<UserMessageReview>()
                .orderByDesc(UserMessageReview::getCreatedAt);
        if (status != null && !status.isBlank()) {
            q.eq(UserMessageReview::getReviewStatus, status);
        }
        q.last("LIMIT " + Math.max(1, size) + " OFFSET " + Math.max(0, page) * Math.max(1, size));
        return reviewMapper.selectList(q).stream().map(this::toView).toList();
    }

    /** 统计指定状态的审核记录数 */
    public long countReviews(String status) {
        LambdaQueryWrapper<UserMessageReview> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(UserMessageReview::getReviewStatus, status);
        }
        Long count = reviewMapper.selectCount(q);
        return count != null ? count : 0;
    }

    /** 管理员审核决定：批准或驳回 */
    @Transactional
    public UserMessageReview decide(Long id, String status, String reviewNote, Long reviewerAdminId) {
        if (status == null || (!STATUS_APPROVED.equals(status) && !STATUS_REJECTED.equals(status))) {
            throw new IllegalArgumentException("reviewStatus 须为 APPROVED 或 REJECTED");
        }
        UserMessageReview row = reviewMapper.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("审核记录不存在: " + id);
        }
        row.setReviewStatus(status);
        row.setReviewNote(reviewNote);
        row.setReviewerAdminId(reviewerAdminId);
        row.setReviewedAt(LocalDateTime.now());
        reviewMapper.updateById(row);
        return reviewMapper.selectById(id);
    }

    /** 将审核记录转为前端展示视图，关联查询原始消息内容 */
    private Map<String, Object> toView(UserMessageReview row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("messageId", row.getMessageId());
        m.put("conversationId", row.getConversationId());
        m.put("reviewStatus", row.getReviewStatus());
        m.put("riskLevel", row.getRiskLevel());
        m.put("emotionTag", row.getEmotionTag());
        m.put("reviewNote", row.getReviewNote());
        m.put("reviewerAdminId", row.getReviewerAdminId());
        m.put("reviewedAt", row.getReviewedAt());
        m.put("createdAt", row.getCreatedAt());
        if (row.getMessageId() != null) {
            UserMessage msg = messageMapper.selectById(row.getMessageId());
            if (msg != null) {
                m.put("messageContent", msg.getContent());
                m.put("replySource", msg.getReplySource());
                m.put("messageRole", msg.getRole());
            }
        }
        return m;
    }
}
