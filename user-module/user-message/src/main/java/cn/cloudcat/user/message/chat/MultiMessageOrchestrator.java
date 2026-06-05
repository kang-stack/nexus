package cn.cloudcat.user.message.chat;

import cn.cloudcat.user.common.sse.AgentResponse;
import cn.cloudcat.user.common.model.ReplySegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 多消息回复编排器（PRD 5.4.2）。
 *
 * 将知识库/RAG 返回的消息列表按顺序、带延迟地转为 SSE 事件流。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class MultiMessageOrchestrator {

    /** 最多发送条数 */
    private static final int MAX_MESSAGES = 5;
    /** 消息间隔下限（秒）— V2 规范 5~10s */
    private static final long DELAY_MIN_SEC = 5;
    /** 消息间隔上限（秒） */
    private static final long DELAY_MAX_SEC = 10;

    /**
     * 将回复段列表编排为 SSE Flux（V2 规范 5~10s 间隔）。
     * <p>
     * 规则：
     * <ul>
     *   <li>第1条立即发送（无延迟）</li>
     *   <li>后续每条前插入 typing 事件 + 随机 5~10s 延迟</li>
     *   <li>超过 5 条自动截断</li>
     *   <li>末尾自动追加 complete 事件</li>
     * </ul>
     *
     * @param segments 回复段列表（已排序），超过 5 条会被截断
     * @return Flux 延迟多消息 SSE 序列
     */
    public Flux<String> orchestrate(List<ReplySegment> segments) {
        // 委托给 emitSegments，末尾自动追加 complete 事件
        return emitSegments(segments, true);
    }

    /**
     * 发送分段消息事件。
     *
     * @param segments       回复段列表
     * @param appendComplete 是否在末尾追加 complete 事件（streamTurn 由调用方自行追加时为 false）
     */
    public Flux<String> emitSegments(List<ReplySegment> segments, boolean appendComplete) {
        if (segments == null || segments.isEmpty()) {
            return appendComplete ? Flux.just(AgentResponse.complete()) : Flux.empty();
        }
        List<ReplySegment> limited = segments.size() > MAX_MESSAGES
                ? segments.subList(0, MAX_MESSAGES)
                : segments;

        Flux<String> flux = Flux.empty();
        for (int i = 0; i < limited.size(); i++) {
            ReplySegment seg = limited.get(i);
            int index = i;
            Flux<String> messageFlux = Flux.just(AgentResponse.message(seg.toEventData()));
            // 非首条：插入 typing + 5~10s 随机延迟
            if (index > 0) {
                long delaySec = ThreadLocalRandom.current().nextLong(DELAY_MIN_SEC, DELAY_MAX_SEC + 1);
                messageFlux = Flux.just(AgentResponse.typing())
                        .concatWith(messageFlux)
                        .delaySubscription(Duration.ofSeconds(delaySec));
            }
            flux = flux.concatWith(messageFlux);
        }
        return appendCompleteIfNeeded(flux, appendComplete);
    }

    // 按需追加 complete 事件到 Flux 末尾
    private Flux<String> appendCompleteIfNeeded(Flux<String> flux, boolean appendComplete) {
        if (appendComplete) {
            return flux.concatWith(Flux.just(AgentResponse.complete()));
        }
        return flux;
    }

    /**
     * 单条消息立即发送（无延迟，无 typing）。
     * 用于规则引擎/RAG 只返回一条消息时的简单路径。
     */
    public Flux<String> singleMessage(ReplySegment segment) {
        return Flux.just(
                AgentResponse.message(segment.toEventData()),
                AgentResponse.complete()
        );
    }
}
