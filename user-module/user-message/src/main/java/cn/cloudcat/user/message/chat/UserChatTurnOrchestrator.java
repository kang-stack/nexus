package cn.cloudcat.user.message.chat;

import cn.cloudcat.user.agent.ChatAgent;
import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.agent.trace.ChatTurnMdc;
import cn.cloudcat.user.agent.trace.TurnOutcomeResolver;
import cn.cloudcat.user.agent.trace.TurnTracePublisher;
import cn.cloudcat.user.session.model.SessionEvent;
import cn.cloudcat.user.session.SessionStateMachine;
import cn.cloudcat.user.session.service.UserChatTurnTraceService;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.session.service.UserMessageService;
import cn.cloudcat.user.session.service.UserMessageServiceImpl;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.common.sse.AgentResponse;
import cn.cloudcat.user.message.chat.memory.UserChatMemoryStore;
import cn.cloudcat.user.message.session.UserSessionTimeoutService;
import cn.cloudcat.user.message.policy.TurnPolicyService;
import cn.cloudcat.user.message.chat.DuplicateQuestionService;
import cn.cloudcat.user.message.chat.MultiMessageOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserChatTurnOrchestrator {

    private final ChatAgent chatAgent;
    private final UserConversationService conversationService;
    private final UserMessageService messageService;
    private final MultiMessageOrchestrator multiMessageOrchestrator;
    private final UserSessionTimeoutService sessionTimeoutService;
    private final SessionStateMachine stateMachine;
    private final UserRagFallbackService ragFallbackService;
    private final UserChatMemoryStore csChatMemoryStore;
    private final DuplicateQuestionService duplicateQuestionService;
    private final UserChatTurnTraceService turnTraceService;
    private final TurnPolicyService turnPolicyService;

    /**
     * 核心对话轮次编排：用户消息 → Skill链 → 规则/知识库 → 延迟多消息SSE。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验会话归属，保存用户消息</li>
     *   <li>重复提问检测（同类问题走快捷回复）</li>
     *   <li>ChatAgent Skill 责任链：Chitchat → Intent → Facts → DMN → acc_user_kb_branch → Compose</li>
     *   <li>若 needRag，走 LangChain4j ES 检索兜底</li>
     *   <li>否则持久化分段消息，按需追加满意度卡片，SSE 延迟发送</li>
     * </ol>
     */
    @Transactional
    public Flux<String> streamTurn(String conversationId, String userId, String message, String quickServiceCode) {
        String turnId = SnowflakeIdGenerator.getInstance().nextIdStr();
        ChatTurnMdc.bind(turnId, conversationId);
        try {
            return doStreamTurn(turnId, conversationId, userId, message, quickServiceCode);
        } finally {
            ChatTurnMdc.clear();
        }
    }

    private Flux<String> doStreamTurn(String turnId, String conversationId, String userId,
                                      String message, String quickServiceCode) {
        // 1. 校验会话归属并保存用户消息
        UserConversation conv = conversationService.getForUser(conversationId, userId);
        UserMessage userMsg = messageService.saveUserMessage(conversationId, message, null);

        // 2. 同会话重复提问检测（相同/相似问题走快捷回复，避免重复触发LLM）
        if (duplicateQuestionService.isDuplicate(conversationId, message)) {
            return streamDuplicateReply(turnId, conversationId, conv, message, userMsg.getId());
        }

        // 3. 构建 SkillContext 并执行 ChatAgent Skill 责任链
        String intentHint = intentHint(message, quickServiceCode);
        SkillContext ctx = new SkillContext(conversationId, userId, message, intentHint);
        ctx.bindTurn(turnId, userMsg.getId());
        ctx.setConversation(conv);

        long chainStart = System.currentTimeMillis();
        ctx = chatAgent.handle(ctx);
        long chainCost = System.currentTimeMillis() - chainStart;

        if (ctx.dialogueState() != null) {
            turnPolicyService.afterTurn(ctx.dialogueState(), ctx);
            conversationService.saveDialogueState(conv, ctx.dialogueState());
        }

        conversationService.onUserMessage(conv, ctx.chitchat()); // 更新会话状态（寒暄不计轮次）

        // 4. 知识库未命中 → RAG 兜底（LangChain4j ES 检索 + LLM）
        if (ctx.needRag()) {
            TurnTracePublisher.publish(turnTraceService, ctx, "RAG", chainCost);
            return streamRag(ctx, conv);
        }

        // 5. 无回复段 → 错误兜底
        if (ctx.replySegments().isEmpty()) {
            TurnTracePublisher.publish(turnTraceService, ctx, "ERROR", chainCost);
            return Flux.just(AgentResponse.error("未能生成回复"),
                    AgentResponse.complete(Map.of("turnId", turnId, "outcome", "ERROR")));
        }

        TurnTracePublisher.publish(turnTraceService, ctx, TurnOutcomeResolver.resolve(ctx), chainCost);

        // 6. 持久化回复段到 acc_user_message 表
        List<ReplySegment> segments = persistSegments(conversationId, ctx);
        // 7. 按需追加满意度卡片 + 启动满意度/空闲计时器
        if (chatAgent.shouldDispatchSatisfaction(ctx)) {
            segments = appendSatisfactionSegment(conversationId, segments);
            sessionTimeoutService.startSatisfactionTimer(conversationId); // 120s满意度计时
        } else {
            sessionTimeoutService.refreshIdleTimer(conversationId); // 300s空闲计时
        }

        // 8. 状态机：ACTIVE → AI_REPLYING → AWAITING_USER
        markAiReplyDone(conv);
        // 9. 标记已给方案 / 满意度待评价
        if (ctx.conversation().getSolutionGiven() != null && ctx.conversation().getSolutionGiven() == 1) {
            conversationService.markSolutionGiven(conv);
        }
        if (ctx.closingTriggered()) {
            conversationService.markSatisfactionPending(conv);
        }

        // 10. 组装 SSE Flux：session事件 → 延迟多消息 → complete事件（含 intentCode 等元数据）
        Map<String, Object> completeMeta = completeMeta(ctx);
        return Flux.concat(
                Flux.just(AgentResponse.session(conversationId)),
                multiMessageOrchestrator.emitSegments(segments, false),
                Flux.just(AgentResponse.complete(completeMeta))
        );
    }

    // 从 SkillContext 提取元数据放入 SSE complete 事件
    private static Map<String, Object> completeMeta(SkillContext ctx) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (ctx.turnId() != null && !ctx.turnId().isBlank()) {
            meta.put("turnId", ctx.turnId());
        }
        if (ctx.resolvedIntent() != null && !ctx.resolvedIntent().isBlank()) {
            meta.put("intentCode", ctx.resolvedIntent());
        }
        meta.put("outcome", TurnOutcomeResolver.resolve(ctx));
        return meta;
    }

    // RAG 兜底分支：清除记忆缓存 → 标记 AI 回复完成 → 流式检索+LLM
    private Flux<String> streamRag(SkillContext ctx, UserConversation conv) {
        csChatMemoryStore.evictCache(ctx.conversationId());
        markAiReplyDone(conv);
        return Flux.concat(
                Flux.just(AgentResponse.session(ctx.conversationId())),
                ragFallbackService.stream(ctx, conv)
        );
    }

    // 将 Skill 生成的 ReplySegment 列表持久化为 acc_user_message 行
    private List<ReplySegment> persistSegments(String conversationId, SkillContext ctx) {
        List<ReplySegment> persisted = new ArrayList<>();
        String source = ctx.chitchat() ? UserMessageServiceImpl.SOURCE_CHITCHAT : "BRANCH";
        for (ReplySegment seg : ctx.replySegments()) {
            if (ReplySegment.SATISFACTION.equals(seg.getMessageType())) {
                // 满意度卡片：保存为独立消息
                UserMessage msg = messageService.saveSatisfactionCard(conversationId);
                seg.setMessageId(msg.getId());
            } else {
                // 文本/按钮消息：构建JSON payload（links + button），写入 acc_user_message
                String payloadJson = MessagePayloadUtil.toPayloadJson(seg.getLinks(), seg.getButton());
                UserMessage msg = payloadJson != null
                        ? messageService.saveAssistantStructured(conversationId, seg.getContent(), source, payloadJson)
                        : messageService.saveAssistantText(conversationId, seg.getContent(), source);
                seg.setMessageId(msg.getId());
            }
            persisted.add(seg);
        }
        return persisted;
    }

    // 在消息列表末尾追加满意度评价卡片（SATISFACTION 类型气泡）
    private List<ReplySegment> appendSatisfactionSegment(String conversationId, List<ReplySegment> segments) {
        List<ReplySegment> all = new ArrayList<>(segments);
        UserMessage satMsg = messageService.saveSatisfactionCard(conversationId);
        ReplySegment sat = new ReplySegment(ReplySegment.SATISFACTION, null,
                segments.size() + 1, segments.size() + 1);
        sat.setMessageId(satMsg.getId());
        sat.setNewBubble(true);
        all.add(sat);
        return all;
    }

    // 执行两次状态转换：ACTIVE → AI_REPLYING → AWAITING_USER
    private void markAiReplyDone(UserConversation conv) {
        conversationService.applyStatus(conv, stateMachine.apply(conv, SessionEvent.AI_REPLY_START).name());
        conversationService.applyStatus(conv, stateMachine.apply(conv, SessionEvent.AI_REPLY_DONE).name());
    }

    // 构建会话状态视图（状态、剩余秒数、是否过期）
    public Map<String, Object> sessionView(String conversationId, String userId) {
        UserConversation conv = conversationService.getForUser(conversationId, userId);
        Map<String, Object> view = new LinkedHashMap<>(conversationService.toStatusView(conv));
        long remaining = sessionTimeoutService.getIdleRemainingSeconds(conversationId);
        view.put("remainingSeconds", remaining);
        view.put("expired", remaining <= 0);
        return view;
    }

    // 重复提问快捷回复：提示用户转人工，不走 Skill 链
    private Flux<String> streamDuplicateReply(String turnId, String conversationId, UserConversation conv,
                                              String message, String userMessageId) {
        turnTraceService.saveAsync(turnId, conversationId, userMessageId, null, message,
                "DUPLICATE", 0L, null, null, 1,
                Map.of("finalPath", "DUPLICATE", "skills", List.of()));

        ReplySegment seg = new ReplySegment("TEXT",
                "您刚才已经咨询过类似的问题，目前没有新的解决方案。如需进一步帮助，建议联系人工客服。",
                1, 1);
        java.util.Map<String, Object> button = new LinkedHashMap<>();
        button.put("text", "转人工客服");
        button.put("type", "action");
        button.put("action", "TRANSFER_TO_HUMAN");
        seg.setButton(button);
        seg.setNewBubble(true);
        seg.setEnding(true);

        SkillContext dupCtx = new SkillContext(conversationId, "", "", "");
        dupCtx.addSegments(List.of(seg));
        List<ReplySegment> segments = persistSegments(conversationId, dupCtx);
        markAiReplyDone(conv);
        sessionTimeoutService.refreshIdleTimer(conversationId);
        return Flux.concat(
                Flux.just(AgentResponse.session(conversationId)),
                multiMessageOrchestrator.emitSegments(segments, false),
                Flux.just(AgentResponse.complete(Map.of("turnId", turnId, "outcome", "DUPLICATE")))
        );
    }

    // 解析意图提示：quickServiceCode 优先，否则用原始消息文本
    private static String intentHint(String message, String quickServiceCode) {
        if (quickServiceCode != null && !quickServiceCode.isBlank()) {
            return quickServiceCode;
        }
        return message;
    }
}
