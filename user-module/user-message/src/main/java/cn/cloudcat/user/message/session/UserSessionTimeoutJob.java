package cn.cloudcat.user.message.session;

import cn.cloudcat.user.session.SessionStateMachine;
import cn.cloudcat.user.session.model.ConversationStatus;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.session.service.UserMessageService;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.session.mapper.UserConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务端会话超时闭环：空闲 5min / 满意度 2min。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserSessionTimeoutJob {

    private final UserConversationMapper conversationMapper;
    private final UserConversationService conversationService;
    private final UserMessageService messageService;
    private final UserSessionTimeoutService sessionTimeoutService;
    private final SessionStateMachine stateMachine;

    @Scheduled(fixedDelay = 30_000)
    public void pollTimeouts() {
        List<UserConversation> open = conversationMapper.selectList(new LambdaQueryWrapper<UserConversation>()
                .in(UserConversation::getStatus,
                        ConversationStatus.AWAITING_USER.name(),
                        ConversationStatus.AWAITING_SATISFACTION.name()));
        for (UserConversation conv : open) {
            try {
                if (ConversationStatus.AWAITING_USER.name().equals(conv.getStatus())
                        && sessionTimeoutService.isIdleExpired(conv.getId())) {
                    handleIdleTimeout(conv);
                } else if (ConversationStatus.AWAITING_SATISFACTION.name().equals(conv.getStatus())
                        && sessionTimeoutService.isSatisfactionExpired(conv.getId())) {
                    handleSatisfactionTimeout(conv);
                }
            } catch (Exception e) {
                log.warn("timeout job failed conv={}", conv.getId(), e);
            }
        }
    }

    private void handleIdleTimeout(UserConversation conv) {
        log.info("idle timeout conv={}", conv.getId());
        messageService.saveAssistantText(conv.getId(), sessionTimeoutService.timeoutMessage(), "SYSTEM");
        conversationService.applyStatus(conv, stateMachine.apply(conv, SessionEvent.TIMEOUT_CLOSE).name());
        conversationService.markSatisfactionPending(conv);
        conversationService.applyStatus(conv, stateMachine.apply(conv, SessionEvent.SATISFACTION_PROMPT).name());
        messageService.saveSatisfactionCard(conv.getId());
        sessionTimeoutService.startSatisfactionTimer(conv.getId());
    }

    private void handleSatisfactionTimeout(UserConversation conv) {
        log.info("satisfaction timeout conv={}", conv.getId());
        conversationService.applyStatus(conv, stateMachine.apply(conv, SessionEvent.TIMEOUT_CLOSE).name());
        conversationService.close(conv, "SAT_TIMEOUT");
        sessionTimeoutService.cancelTimers(conv.getId());
    }
}
