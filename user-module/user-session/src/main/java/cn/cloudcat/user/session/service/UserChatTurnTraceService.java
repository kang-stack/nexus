package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.entity.UserChatTurnTrace;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 对话轮次 Skill 链轨迹持久化。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface UserChatTurnTraceService {

    /**
     * 异步写入轨迹 JSON。
     *
     * @param turnId           轮次 ID
     * @param conversationId   会话 ID
     * @param userMessageId    用户消息 ID
     * @param userId           用户 ID 字符串
     * @param userText         用户原文
     * @param outcome          最终路径
     * @param totalCostMs      Skill 链耗时
     * @param resolvedIntent   解析意图
     * @param primaryRootCause DMN 主根因
     * @param segmentCount     回复段数
     * @param traceJson        决策树 JSON 对象
     */
    void saveAsync(String turnId, String conversationId, String userMessageId, String userId,
                   String userText, String outcome, long totalCostMs,
                   String resolvedIntent, String primaryRootCause, int segmentCount,
                   Map<String, Object> traceJson);

    /** 按会话查询最近 N 条轨迹 */
    List<UserChatTurnTrace> listByConversation(String conversationId, int limit);

    /**
     * 分页查询轨迹（按 created_at 倒序）。
     *
     * @param conversationId 可选；为空时返回全部会话的轮次
     * @param page           页码，从 0 开始
     * @param size           每页条数
     */
    Map<String, Object> pageTraces(String conversationId, int page, int size);

    /** 按 turnId 查询 */
    Optional<UserChatTurnTrace> getByTurnId(String turnId);

    /** 转为 API 详情视图（含 trace JSON） */
    Map<String, Object> toView(UserChatTurnTrace trace);

    /** 列表项摘要（不含 trace JSON，减小分页载荷） */
    Map<String, Object> toSummaryView(UserChatTurnTrace trace);
}
