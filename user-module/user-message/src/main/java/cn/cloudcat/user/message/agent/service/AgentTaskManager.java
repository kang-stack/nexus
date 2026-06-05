package cn.cloudcat.user.message.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Agent 任务管理器 — 管理流式输出的中断/停止。
 * 双层映射：本地 ConcurrentHashMap + Redis（跨实例）。
 * 通过 Redis Pub/Sub 实现分布式停止信号广播。
 */
@Slf4j
@Component
public class AgentTaskManager implements InitializingBean, DisposableBean {

    private static final String TASK_KEY_PREFIX = "icc:task:";
    private static final String STOP_TOPIC_NAME = "icc:task:stop";
    private static final long TASK_TTL_MINUTES = 30;
    private static final long TTL_REFRESH_INTERVAL_MINUTES = 5;

    private final String instanceId;
    private final RedissonClient redissonClient;
    private final RTopic stopTopic;
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService ttlRefreshScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "agent-ttl-refresh");
                t.setDaemon(true);
                return t;
            });

    private int listenerId;

    public AgentTaskManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.stopTopic = redissonClient.getTopic(STOP_TOPIC_NAME, StringCodec.INSTANCE);
        log.info("AgentTaskManager init, instanceId={}", instanceId);
    }

    private RBucket<String> getTaskBucket(String conversationId) {
        return redissonClient.getBucket(TASK_KEY_PREFIX + conversationId, StringCodec.INSTANCE);
    }

    @Override
    public void afterPropertiesSet() {
        listenerId = stopTopic.addListener(String.class, (channel, conversationId) -> {
            handleRemoteStop(conversationId);
        });
        ttlRefreshScheduler.scheduleAtFixedRate(
                this::refreshTaskTtls,
                TTL_REFRESH_INTERVAL_MINUTES,
                TTL_REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
        log.info("AgentTaskManager started, subscribed to stop topic, TTL refresh={}min",
                TTL_REFRESH_INTERVAL_MINUTES);
    }

    @Override
    public void destroy() {
        try { stopTopic.removeListener(listenerId); } catch (Exception e) { log.warn("remove listener fail", e); }
        ttlRefreshScheduler.shutdown();
        for (String cid : taskMap.keySet()) {
            doRemoveTask(cid);
        }
        log.info("AgentTaskManager destroyed, instanceId={}", instanceId);
    }

    // ---- TaskInfo ----

    public static class TaskInfo {
        private final Sinks.Many<String> sink;
        private volatile Disposable disposable;
        private final long createTime;
        private final String agentType;
        private volatile StringBuilder partialContent; // 收集已输出文本用于 stopped 事件

        public TaskInfo(Sinks.Many<String> sink, String agentType) {
            this.sink = sink;
            this.agentType = agentType;
            this.createTime = System.currentTimeMillis();
            this.partialContent = new StringBuilder();
        }

        public Sinks.Many<String> getSink() { return sink; }
        public Disposable getDisposable() { return disposable; }
        public void setDisposable(Disposable d) { this.disposable = d; }
        public long getCreateTime() { return createTime; }
        public String getAgentType() { return agentType; }
        public void appendContent(String text) {
            if (partialContent != null) partialContent.append(text);
        }
        public String getPartialContent() {
            return partialContent != null ? partialContent.toString() : "";
        }
        public void clearPartialContent() { partialContent = null; }
    }

    // ---- register ----

    public TaskInfo registerTask(String conversationId, Sinks.Many<String> sink, String agentType) {
        TaskInfo existing = taskMap.get(conversationId);
        if (existing != null) {
            log.warn("task already running locally: conversationId={}", conversationId);
            return null;
        }
        RBucket<String> bucket = getTaskBucket(conversationId);
        boolean acquired = bucket.trySet(instanceId, TASK_TTL_MINUTES, TimeUnit.MINUTES);
        if (!acquired) {
            String holder = bucket.get();
            log.warn("task already on instance={}: conversationId={}", holder, conversationId);
            return null;
        }
        TaskInfo taskInfo = new TaskInfo(sink, agentType);
        taskMap.put(conversationId, taskInfo);
        log.info("task registered: conversationId={}, agentType={}, instanceId={}",
                conversationId, agentType, instanceId);
        return taskInfo;
    }

    // ---- disposable ----

    public void setDisposable(String conversationId, Disposable disposable) {
        TaskInfo ti = taskMap.get(conversationId);
        if (ti != null) ti.setDisposable(disposable);
    }

    // ---- stop ----

    public boolean stopTask(String conversationId) {
        // fast path: local
        TaskInfo local = taskMap.get(conversationId);
        if (local != null) {
            log.info("stop local: conversationId={}", conversationId);
            doStopTask(conversationId, local);
            return true;
        }
        // check Redis
        RBucket<String> bucket = getTaskBucket(conversationId);
        if (!bucket.isExists()) return false;
        String holder = bucket.get();
        if (instanceId.equals(holder)) return false;
        // broadcast
        long receivers = stopTopic.publish(conversationId);
        log.info("stop broadcast: conversationId={}, receivers={}", conversationId, receivers);
        return true;
    }

    public boolean hasRunningTask(String conversationId) {
        if (taskMap.containsKey(conversationId)) return true;
        RBucket<String> bucket = getTaskBucket(conversationId);
        return bucket.isExists();
    }

    // ---- internal ----

    private void handleRemoteStop(String conversationId) {
        TaskInfo ti = taskMap.remove(conversationId);
        if (ti == null) return;
        log.info("remote stop: conversationId={}", conversationId);
        doStopTask(conversationId, ti);
    }

    private void doStopTask(String conversationId, TaskInfo ti) {
        try {
            Disposable d = ti.getDisposable();
            if (d != null && !d.isDisposed()) {
                d.dispose();
                log.info("disposed: conversationId={}", conversationId);
            }
            Sinks.Many<String> sink = ti.getSink();
            if (sink != null) {
                try {
                    String partial = ti.getPartialContent();
                    sink.tryEmitNext(createStopMessage(partial));
                    sink.tryEmitComplete();
                } catch (Exception e) {
                    log.warn("emit stop message fail: conversationId={}", conversationId, e);
                }
            }
        } finally {
            doRemoveTask(conversationId);
        }
    }

    private void doRemoveTask(String conversationId) {
        taskMap.remove(conversationId);
        RBucket<String> bucket = getTaskBucket(conversationId);
        String holder = bucket.get();
        if (instanceId.equals(holder)) {
            bucket.delete();
        }
    }

    private void refreshTaskTtls() {
        if (taskMap.isEmpty()) return;
        for (String cid : taskMap.keySet()) {
            try {
                RBucket<String> bucket = getTaskBucket(cid);
                String holder = bucket.get();
                if (instanceId.equals(holder)) {
                    bucket.expire(Duration.ofMinutes(TASK_TTL_MINUTES));
                } else {
                    taskMap.remove(cid);
                }
            } catch (Exception e) {
                log.error("TTL refresh fail: conversationId={}", cid, e);
            }
        }
    }

    private String createStopMessage(String partialContent) {
        JSONObject obj = new JSONObject();
        obj.put("type", "stopped");
        obj.put("content", partialContent != null ? partialContent : "");
        return JSON.toJSONString(obj);
    }
}
