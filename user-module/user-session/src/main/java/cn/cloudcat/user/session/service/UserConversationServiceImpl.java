package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.ConversationExt;
import cn.cloudcat.user.session.DialogueStateCodec;
import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SatisfactionStatus;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.session.mapper.UserConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V2 会话核心服务实现。
 * 负责 acc_user_conversation 表的 CRUD、状态机驱动和辅助字段维护。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@RequiredArgsConstructor
public class UserConversationServiceImpl implements UserConversationService {

    private final UserConversationMapper conversationMapper;
    private final cn.cloudcat.user.session.SessionStateMachine stateMachine;

    @Override
    @Transactional
    public UserConversation create(String userId, String deviceSn, String entryIntent) {
        UserConversation conv = new UserConversation();
        conv.setId(SnowflakeIdGenerator.getInstance().nextIdStr());
        conv.setUserId(parseUserId(userId));
        conv.setDeviceSn(deviceSn);
        conv.setChannel("WECHAT");
        conv.setEntryIntent(entryIntent);
        conv.setTurnCount(0);
        conv.setChitchatOnlyCount(0);
        conv.setSolutionGiven(0);
        conv.setAiReplyLocked(0);
        conv.setStatus(ConversationStatus.ACTIVE.name());
        conv.setSatisfactionStatus(SatisfactionStatus.NONE.name());
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    @Override
    public UserConversation getRequired(String conversationId) {
        UserConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        return conv;
    }

    @Override
    public UserConversation getForUser(String conversationId, String userId) {
        UserConversation conv = getRequired(conversationId);
        Long uid = parseUserId(userId);
        // 校验会话归属权：会话 userId 必须与请求用户一致
        if (conv.getUserId() == null || !conv.getUserId().equals(uid)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return conv;
    }

    @Override
    @Transactional
    public void applyStatus(UserConversation conv, String newStatus) {
        conv.setStatus(newStatus);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void onUserMessage(UserConversation conv, boolean chitchat) {
        // 通过状态机计算目标状态
        ConversationStatus next = stateMachine.apply(conv, SessionEvent.USER_MESSAGE);
        conv.setStatus(next.name());
        conv.setLastUserAt(LocalDateTime.now());
        // 非寒暄消息：重置寒暄计数器并递增轮次
        if (!chitchat) {
            conv.setChitchatOnlyCount(0);
            conv.setTurnCount((conv.getTurnCount() != null ? conv.getTurnCount() : 0) + 1);
        }
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void onChitchatReply(UserConversation conv, String categoryCode) {
        int count = conv.getChitchatOnlyCount() != null ? conv.getChitchatOnlyCount() : 0;
        // 递增连续寒暄计数并记录分类到 ext_json
        conv.setChitchatOnlyCount(count + 1);
        conv.setExtJson(ConversationExt.withLastChitchatCategory(conv, categoryCode));
        conv.setLastAiAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void updateLatestDiagnosisId(UserConversation conv, String diagnosisId) {
        conv.setLatestDiagnosisId(diagnosisId);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void close(UserConversation conv, String closeReason) {
        conv.setStatus(ConversationStatus.CLOSED.name());
        conv.setCloseReason(closeReason);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void markSolutionGiven(UserConversation conv) {
        conv.setSolutionGiven(1);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void markSatisfactionPending(UserConversation conv) {
        conv.setSatisfactionStatus(SatisfactionStatus.PENDING.name());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    @Transactional
    public void saveDialogueState(UserConversation conv, DialogueState state) {
        if (conv == null) {
            return;
        }
        DialogueState toSave = state != null ? state : DialogueState.empty();
        conv.setExtJson(DialogueStateCodec.write(conv, toSave));
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    public Map<String, Object> toStatusView(UserConversation conv) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", conv.getId());
        view.put("status", conv.getStatus());
        // aiReplyLocked 字段在数据库中为 0/1，转为 boolean
        view.put("aiReplyLocked", conv.getAiReplyLocked() != null && conv.getAiReplyLocked() == 1);
        view.put("satisfactionStatus", conv.getSatisfactionStatus());
        view.put("turnCount", conv.getTurnCount());
        view.put("solutionGiven", conv.getSolutionGiven() != null && conv.getSolutionGiven() == 1);
        return view;
    }

    /** 将 userId 字符串解析为 Long，非法值时回退到 hashCode */
    private static Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return (long) userId.hashCode();
        }
    }
}
