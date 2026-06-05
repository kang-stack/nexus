package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.entity.UserConversation;

import java.util.Map;

/**
 * V2 会话核心服务接口。
 * 管理 acc_user_conversation 表的 CRUD、状态变迁和辅助字段更新。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface UserConversationService {

    /** 创建新会话，返回持久化后的对象 */
    UserConversation create(String userId, String deviceSn, String entryIntent);

    /** 按 ID 查找会话，不存在则抛出 IllegalArgumentException */
    UserConversation getRequired(String conversationId);

    /** 按 ID + 所属用户查找会话，权限校验未通过则抛异常 */
    UserConversation getForUser(String conversationId, String userId);

    /** 直接更新会话状态字段 */
    void applyStatus(UserConversation conv, String newStatus);

    /** 收到用户消息后更新状态、轮次和最近活动时间 */
    void onUserMessage(UserConversation conv, boolean chitchat);

    /** 寒暄回复后的辅助字段更新（累计寒暄计数、更新 ext_json） */
    void onChitchatReply(UserConversation conv, String categoryCode);

    /** 关闭会话并记录关闭原因 */
    void close(UserConversation conv, String closeReason);

    /** 标记会话已给出有效解决方案 */
    void markSolutionGiven(UserConversation conv);

    /** 将满意度状态置为 PENDING（准备弹出评价提示） */
    void markSatisfactionPending(UserConversation conv);

    /** 记录最近一次诊断运行 ID */
    void updateLatestDiagnosisId(UserConversation conv, String diagnosisId);

    /** 构建前端状态视图 Map（包含 id、status、aiReplyLocked 等字段） */
    Map<String, Object> toStatusView(UserConversation conv);

    /** 持久化 ext_json 中的 DialogueState */
    void saveDialogueState(UserConversation conv, DialogueState state);
}
