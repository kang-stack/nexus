package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.entity.UserMessage;

import java.util.List;

/**
 * V2 消息持久化服务接口。
 * 管理 acc_user_message 表的写入、查询和更新操作。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface UserMessageService {

    /** 持久化用户消息 */
    UserMessage saveUserMessage(String conversationId, String text, String clientMsgId);

    /** 持久化 AI 纯文本回复 */
    UserMessage saveAssistantText(String conversationId, String text, String replySource);

    /** 持久化 AI 结构化回复（含 payload JSON） */
    UserMessage saveAssistantStructured(String conversationId, String text, String replySource, String payloadJson);

    /** 持久化满意度评价卡片消息 */
    UserMessage saveSatisfactionCard(String conversationId);

    /** 按会话 ID 查询所有消息，按时间升序 */
    List<UserMessage> listByConversation(String conversationId);

    /** 按会话 ID 查询最近 N 条文本消息，按时间降序 */
    List<UserMessage> getRecentMessages(String conversationId, int limit);

    /** 更新 AI 消息的正文内容（用于流式输出完成后补全） */
    void updateAssistantContent(String messageId, String content);

    /** 更新消息的 payload_json 字段 */
    void updatePayloadJson(String messageId, String payloadJson);
}
