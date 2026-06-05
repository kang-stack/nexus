package cn.cloudcat.user.message.chat;

import cn.cloudcat.user.agent.skill.SkillContext;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.session.service.UserMessageService;
import cn.cloudcat.user.session.service.UserMessageServiceImpl;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.user.knowledge.engine.IntentResolverService;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import cn.cloudcat.user.message.rag.model.RagChatParam;
import cn.cloudcat.user.message.rag.service.RagApplicationService;
import cn.cloudcat.user.message.chat.memory.UserChatMemoryStore;
import cn.cloudcat.user.message.session.UserSessionTimeoutService;
import cn.cloudcat.user.message.conversation.EndingDetector;
import cn.cloudcat.user.message.chat.MultiMessageOrchestrator;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * V2 RAG 兜底：LangChain4j ES 检索 + 写入 acc_user_message。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserRagFallbackService {

    private final RagApplicationService ragApplicationService;
    private final UserChatMemoryStore csChatMemoryStore;
    private final UserMessageService messageService;
    private final MultiMessageOrchestrator multiMessageOrchestrator;
    private final EndingDetector endingDetector;
    private final UserConversationService conversationService;
    private final UserSessionTimeoutService sessionTimeoutService;
    private final IntentResolverService intentResolver;

    /**
     * RAG 兜底流程：ES 检索 → LLM 流式生成 → 持久化 → 延迟多消息 SSE。
     * <p>
     * 仅在 Skill 链标记 needRag=true（知识库未命中）时由 streamTurn 调用。
     *
     * @param ctx  Skill 上下文（含用户消息、意图提示、primaryRootCause 等）
     * @param conv 当前会话
     * @return Flux 流式 SSE 事件序列
     */
    public Flux<String> stream(SkillContext ctx, UserConversation conv) {
        // 1. 确定查询文本（优先 ragQuery，其次原始用户文本）
        String query = ctx.ragQuery() != null ? ctx.ragQuery() : ctx.userText();
        csChatMemoryStore.evictCache(ctx.conversationId());

        // 2. 创建占位 assistant 消息（后续流式更新内容）
        UserMessage assistantMsg = messageService.saveAssistantText(
                ctx.conversationId(), "", UserMessageServiceImpl.SOURCE_RAG);
        // 若 DMN 已诊断出主因，写入消息 payload
        if (ctx.primaryRootCause() != null && !ctx.primaryRootCause().isBlank()) {
            messageService.updatePayloadJson(assistantMsg.getId(),
                    JSON.toJSONString(java.util.Map.of("primaryRootCause", ctx.primaryRootCause())));
        }

        // 3. 解析意图 + 构建 RagChatParam
        IntentRecognitionResult intent = resolveIntent(ctx);
        RagChatParam param = new RagChatParam(ctx.userId(), ctx.conversationId(), null,
                query, assistantMsg.getId(), intent);

        // 4. LLM 流式生成：逐 chunk 拼接到 contentBuilder，同时实时更新消息内容
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<String> finalContent = new AtomicReference<>("");

        return ragApplicationService.doChatWithMemory(param, csChatMemoryStore, content -> {
                    finalContent.set(content); // 每次 onComplete 回调时更新最终内容
                    messageService.updateAssistantContent(assistantMsg.getId(), content);
                    conversationService.markSolutionGiven(conv);
                })
                .doOnNext(chunk -> appendChunk(contentBuilder, chunk)) // 非 JSON chunk 直接拼接
                .thenMany(Flux.defer(() -> finishStream(ctx, conv, assistantMsg, contentBuilder, finalContent)));
    }

    // LLM 流生成完毕后：结束检测 → 构建 ReplySegment → 按需满意度 → SSE 发送
    private Flux<String> finishStream(SkillContext ctx, UserConversation conv, UserMessage assistantMsg,
                                      StringBuilder contentBuilder, AtomicReference<String> finalContent) {
        String body = finalContent.get();
        if (body.isBlank()) {
            // 回调未触发时 fallback 到 StringBuilder 内容
            body = contentBuilder.toString();
            if (!body.isBlank()) {
                messageService.updateAssistantContent(assistantMsg.getId(), body);
                conversationService.markSolutionGiven(conv);
            }
        }

        if (body.isBlank()) {
            return Flux.empty();
        }

        // 结束语检测：识别"感谢使用""已为您处理"等措辞
        boolean ending = endingDetector.isAiEnding(body);
        ReplySegment seg = new ReplySegment("TEXT", body, 1, ending ? 2 : 1);
        seg.setMessageId(assistantMsg.getId());
        seg.setEnding(ending);
        seg.setNewBubble(false);

        List<ReplySegment> segments = new ArrayList<>();
        segments.add(seg);

        if (ending) {
            // 结束语命中 → 追加满意度卡片 + 启动 120s 满意度计时
            segments.add(buildSatisfactionSegment(ctx.conversationId()));
            sessionTimeoutService.startSatisfactionTimer(ctx.conversationId());
            conversationService.markSatisfactionPending(conv);
        } else {
            // 未命中 → 重置 300s 空闲计时
            sessionTimeoutService.refreshIdleTimer(ctx.conversationId());
        }

        return multiMessageOrchestrator.emitSegments(segments, true);
    }

    // 构建满意度卡片段（先写 acc_user_message，再包装为 ReplySegment）
    private ReplySegment buildSatisfactionSegment(String conversationId) {
        UserMessage satMsg = messageService.saveSatisfactionCard(conversationId);
        ReplySegment sat = new ReplySegment(ReplySegment.SATISFACTION, null, 2, 2);
        sat.setMessageId(satMsg.getId());
        sat.setNewBubble(true);
        return sat;
    }

    // 从 SSE chunk 中提取文本内容；JSON type=text 才填充，其他类型（thinking/progress/reference）跳过
    private void appendChunk(StringBuilder sb, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return;
        }
        if (chunk.startsWith("{")) {
            try {
                var json = JSON.parseObject(chunk);
                if ("text".equals(json.getString("type"))) {
                    String c = json.getString("content");
                    if (c != null) {
                        sb.append(c);
                    }
                }
            } catch (Exception ignored) {
                // progress / reference / thinking 事件忽略
            }
        } else {
            sb.append(chunk);
        }
    }

    // 意图解析（优先级：intentHint > resolvedIntent > userText fallback）
    private IntentRecognitionResult resolveIntent(SkillContext ctx) {
        String hint = ctx.intentHint();
        if (hint != null && !hint.isBlank() && !hint.equals(ctx.userText())) {
            String code = intentResolver.resolve(hint);
            return IntentRecognitionResult.quickService(code != null ? code : hint, ctx.userText());
        }
        String resolved = ctx.resolvedIntent();
        if (resolved != null && !resolved.isBlank() && !resolved.equals(ctx.userText())) {
            String code = intentResolver.resolve(resolved);
            return IntentRecognitionResult.quickService(code != null ? code : resolved, ctx.userText());
        }
        String fallback = intentResolver.resolve(ctx.userText());
        if (fallback == null || fallback.isBlank()) {
            fallback = "其他问题";
        }
        return IntentRecognitionResult.quickService(fallback, ctx.userText());
    }
}
