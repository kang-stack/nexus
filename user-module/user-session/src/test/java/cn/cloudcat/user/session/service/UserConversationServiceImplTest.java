package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.SessionStateMachine;
import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SatisfactionStatus;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.session.mapper.UserConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Kang
 * @since 2026-06-02
 */
@ExtendWith(MockitoExtension.class)
class UserConversationServiceImplTest {

    @Mock
    private UserConversationMapper conversationMapper;

    @Mock
    private SessionStateMachine stateMachine;

    private UserConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserConversationServiceImpl(conversationMapper, stateMachine);
    }

    // ===== create =====

    @Test
    void createWithoutIntentReturnsActiveConversation() {
        UserConversation result = service.create("123", "SN001", null);
        assertNotNull(result.getId());
        assertEquals("SN001", result.getDeviceSn());
        assertEquals("WECHAT", result.getChannel());
        assertNull(result.getEntryIntent());
        assertEquals(ConversationStatus.ACTIVE.name(), result.getStatus());
        assertEquals(SatisfactionStatus.NONE.name(), result.getSatisfactionStatus());
        assertEquals(0, result.getTurnCount());
        assertEquals(0, result.getChitchatOnlyCount());
        assertEquals(0, result.getSolutionGiven());
        verify(conversationMapper).insert(any(UserConversation.class));
    }

    @Test
    void createWithIntentStoresEntryIntent() {
        UserConversation result = service.create("123", "SN001", "NET_PROBLEM");
        assertEquals("SN001", result.getDeviceSn());
        assertEquals("WECHAT", result.getChannel());
        assertEquals("NET_PROBLEM", result.getEntryIntent());
    }

    @Test
    void createBlankIntentStoredAsProvided() {
        UserConversation result = service.create("123", "SN001", "   ");
        assertEquals("   ", result.getEntryIntent());
        assertEquals("SN001", result.getDeviceSn());
    }

    // ===== getRequired =====

    @Test
    void getRequiredReturnsConversationWhenExists() {
        // 验证会话存在时返回
        UserConversation conv = new UserConversation();
        conv.setId("CONV-001");
        when(conversationMapper.selectById("CONV-001")).thenReturn(conv);

        UserConversation result = service.getRequired("CONV-001");
        assertEquals("CONV-001", result.getId());
    }

    @Test
    void getRequiredThrowsWhenNotFound() {
        // 验证会话不存在时抛出异常
        when(conversationMapper.selectById("NOT_EXIST")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.getRequired("NOT_EXIST"));
    }

    // ===== getForUser =====

    @Test
    void getForUserReturnsConversationWhenOwnerMatch() {
        // 验证用户ID匹配时返回会话
        UserConversation conv = new UserConversation();
        conv.setId("CONV-001");
        conv.setUserId(123L);
        when(conversationMapper.selectById("CONV-001")).thenReturn(conv);

        UserConversation result = service.getForUser("CONV-001", "123");
        assertEquals("CONV-001", result.getId());
    }

    @Test
    void getForUserThrowsWhenOwnerMismatch() {
        // 验证用户ID不匹配时抛出权限异常
        UserConversation conv = new UserConversation();
        conv.setId("CONV-001");
        conv.setUserId(999L);
        when(conversationMapper.selectById("CONV-001")).thenReturn(conv);

        assertThrows(IllegalArgumentException.class, () -> service.getForUser("CONV-001", "123"));
    }

    // ===== onUserMessage =====

    @Test
    void onUserMessageBusinessTurnsIncrementsTurnCount() {
        // 验证业务消息（非寒暄）增加turn_count
        UserConversation conv = createTestConv();
        conv.setTurnCount(3);
        conv.setChitchatOnlyCount(2);
        when(stateMachine.apply(any(), eq(SessionEvent.USER_MESSAGE))).thenReturn(ConversationStatus.ACTIVE);

        service.onUserMessage(conv, false);

        assertEquals(ConversationStatus.ACTIVE.name(), conv.getStatus());
        assertEquals(4, conv.getTurnCount());
        assertEquals(0, conv.getChitchatOnlyCount()); // 重置连续寒暄计数
        assertNotNull(conv.getLastUserAt());
    }

    @Test
    void onUserMessageChitchatDoesNotIncrementTurnCount() {
        // 验证寒暄消息不增加turn_count且不重置chitchat_only_count
        UserConversation conv = createTestConv();
        conv.setTurnCount(3);
        conv.setChitchatOnlyCount(2);
        when(stateMachine.apply(any(), eq(SessionEvent.USER_MESSAGE))).thenReturn(ConversationStatus.ACTIVE);

        service.onUserMessage(conv, true);

        assertEquals(3, conv.getTurnCount()); // 寒暄不增加轮次
        assertEquals(2, conv.getChitchatOnlyCount()); // 寒暄不重置（由onChitchatReply来增加）
    }

    // ===== onChitchatReply =====

    @Test
    void onChitchatReplyIncrementsChitchatOnlyCount() {
        // 验证寒暄回复增加连续寒暄计数并记录category
        UserConversation conv = createTestConv();
        conv.setChitchatOnlyCount(1);

        service.onChitchatReply(conv, "GREETING");

        assertEquals(2, conv.getChitchatOnlyCount());
        assertNotNull(conv.getLastAiAt());
        verify(conversationMapper).updateById(conv);
    }

    // ===== markSolutionGiven =====

    @Test
    void markSolutionGivenSetsToOne() {
        // 验证标记已给方案
        UserConversation conv = createTestConv();
        conv.setSolutionGiven(0);

        service.markSolutionGiven(conv);

        assertEquals(1, conv.getSolutionGiven());
    }

    // ===== markSatisfactionPending =====

    @Test
    void markSatisfactionPendingSetsStatus() {
        // 验证标记满意度待评价
        UserConversation conv = createTestConv();
        conv.setSatisfactionStatus(SatisfactionStatus.NONE.name());

        service.markSatisfactionPending(conv);

        assertEquals(SatisfactionStatus.PENDING.name(), conv.getSatisfactionStatus());
    }

    // ===== close =====

    @Test
    void closeSetsClosedStatusAndReason() {
        // 验证关闭会话设置状态和原因
        UserConversation conv = createTestConv();
        conv.setStatus(ConversationStatus.AWAITING_SATISFACTION.name());

        service.close(conv, "TIMEOUT");

        assertEquals(ConversationStatus.CLOSED.name(), conv.getStatus());
        assertEquals("TIMEOUT", conv.getCloseReason());
    }

    // ===== toStatusView =====

    @Test
    void toStatusViewContainsRequiredFields() {
        // 验证状态视图包含所有必需字段
        UserConversation conv = createTestConv();
        conv.setAiReplyLocked(0);
        conv.setSolutionGiven(1);

        Map<String, Object> view = service.toStatusView(conv);

        assertEquals("CONV-001", view.get("id"));
        assertEquals(ConversationStatus.ACTIVE.name(), view.get("status"));
        assertEquals(false, view.get("aiReplyLocked"));
        assertEquals(SatisfactionStatus.NONE.name(), view.get("satisfactionStatus"));
        assertEquals(5, view.get("turnCount"));
        assertEquals(true, view.get("solutionGiven"));
    }

    @Test
    void toStatusViewAiReplyLockedTrue() {
        // 验证aiReplyLocked=1时返回true
        UserConversation conv = createTestConv();
        conv.setAiReplyLocked(1);

        Map<String, Object> view = service.toStatusView(conv);

        assertEquals(true, view.get("aiReplyLocked"));
    }

    // ===== applyStatus =====

    @Test
    void applyStatusUpdatesStatusAndTimestamp() {
        // 验证状态更新并刷新时间戳
        UserConversation conv = createTestConv();

        service.applyStatus(conv, ConversationStatus.AI_REPLYING.name());

        assertEquals(ConversationStatus.AI_REPLYING.name(), conv.getStatus());
        verify(conversationMapper).updateById(conv);
    }

    // ===== updateLatestDiagnosisId =====

    @Test
    void updateLatestDiagnosisIdSetsDiagnosisId() {
        // 验证更新最近诊断ID
        UserConversation conv = createTestConv();

        service.updateLatestDiagnosisId(conv, "DIAG-001");

        assertEquals("DIAG-001", conv.getLatestDiagnosisId());
    }

    // ===== parseUserId edge cases =====

    @Test
    void nullUserIdReturnsAnonymous() {
        // 验证null用户ID返回0（匿名用户）
        UserConversation result = service.create(null, "SN001", null);
        assertEquals(0L, result.getUserId());
    }

    @Test
    void nonNumericUserIdUsesHashCode() {
        // 验证非数字用户ID使用hashCode
        UserConversation result = service.create("anonymous-user", "SN001", null);
        assertNotNull(result.getUserId());
    }

    private UserConversation createTestConv() {
        UserConversation conv = new UserConversation();
        conv.setId("CONV-001");
        conv.setUserId(123L);
        conv.setStatus(ConversationStatus.ACTIVE.name());
        conv.setSatisfactionStatus(SatisfactionStatus.NONE.name());
        conv.setTurnCount(5);
        conv.setChitchatOnlyCount(0);
        conv.setSolutionGiven(0);
        conv.setAiReplyLocked(0);
        return conv;
    }
}
