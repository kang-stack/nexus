package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.session.mapper.UserMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * V2 消息持久化服务实现。
 * 管理 acc_user_message 表的写入、查询，并触发消息生命周期监听器（如 ES 索引）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class UserMessageServiceImpl implements UserMessageService {

    /** 角色：用户 */
    public static final String ROLE_USER = "USER";
    /** 角色：AI 助手 */
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    /** 内容类型：纯文本 */
    public static final String TYPE_TEXT = "TEXT";
    /** 内容类型：满意度卡片 */
    public static final String TYPE_SATISFACTION = "SATISFACTION";
    /** 回复来源：寒暄 FastPath */
    public static final String SOURCE_CHITCHAT = "CHITCHAT";
    /** 回复来源：RAG 兜底 */
    public static final String SOURCE_RAG = "RAG";
    /** 回复来源：知识库分支命中 */
    public static final String SOURCE_BRANCH = "BRANCH";

    private final UserMessageMapper messageMapper;
    private final List<UserMessageLifecycleListener> lifecycleListeners;

    public UserMessageServiceImpl(UserMessageMapper messageMapper,
                                @Autowired(required = false) List<UserMessageLifecycleListener> lifecycleListeners) {
        this.messageMapper = messageMapper;
        this.lifecycleListeners = lifecycleListeners != null ? lifecycleListeners : Collections.emptyList();
    }

    @Override
    @Transactional
    public UserMessage saveUserMessage(String conversationId, String text, String clientMsgId) {
        return insert(conversationId, ROLE_USER, TYPE_TEXT, text, null, null, clientMsgId);
    }

    @Override
    @Transactional
    public UserMessage saveAssistantText(String conversationId, String text, String replySource) {
        return insert(conversationId, ROLE_ASSISTANT, TYPE_TEXT, text, replySource, null, null);
    }

    @Override
    @Transactional
    public UserMessage saveAssistantStructured(String conversationId, String text, String replySource, String payloadJson) {
        return insert(conversationId, ROLE_ASSISTANT, TYPE_TEXT, text, replySource, payloadJson, null);
    }

    @Override
    @Transactional
    public UserMessage saveSatisfactionCard(String conversationId) {
        return insert(conversationId, ROLE_ASSISTANT, TYPE_SATISFACTION, null, "SYSTEM", "{}", null);
    }

    @Override
    public List<UserMessage> listByConversation(String conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getConversationId, conversationId)
                .orderByAsc(UserMessage::getCreatedAt));
    }

    @Override
    public List<UserMessage> getRecentMessages(String conversationId, int limit) {
        return messageMapper.selectList(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getConversationId, conversationId)
                .eq(UserMessage::getContentType, TYPE_TEXT)
                .orderByDesc(UserMessage::getCreatedAt)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    @Transactional
    public void updateAssistantContent(String messageId, String content) {
        UserMessage update = new UserMessage();
        update.setId(messageId);
        update.setContent(content);
        messageMapper.updateById(update);
        UserMessage saved = messageMapper.selectById(messageId);
        if (saved != null) {
            notifyListeners(saved);
        }
    }

    @Override
    @Transactional
    public void updatePayloadJson(String messageId, String payloadJson) {
        UserMessage update = new UserMessage();
        update.setId(messageId);
        update.setPayloadJson(payloadJson);
        messageMapper.updateById(update);
    }

    /** 构造并插入一条消息记录，随后通知所有生命周期监听器 */
    private UserMessage insert(String conversationId, String role, String contentType,
                             String content, String replySource, String payloadJson, String clientMsgId) {
        UserMessage msg = new UserMessage();
        msg.setId(SnowflakeIdGenerator.getInstance().nextIdStr());
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContentType(contentType);
        msg.setContent(content);
        msg.setPayloadJson(payloadJson);
        msg.setReplySource(replySource);
        msg.setClientMsgId(clientMsgId);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        notifyListeners(msg);
        return msg;
    }

    /** 遍历并通知所有 UserMessageLifecycleListener，单个监听器失败不影响其他 */
    private void notifyListeners(UserMessage msg) {
        for (UserMessageLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onMessageSaved(msg);
            } catch (Exception e) {
                log.warn("UserMessageLifecycleListener failed id={}", msg.getId(), e);
            }
        }
    }
}
