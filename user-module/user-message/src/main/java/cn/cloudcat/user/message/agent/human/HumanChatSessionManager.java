package cn.cloudcat.user.message.agent.human;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 浜哄伐瀹㈡湇鎺掗槦涓庝細璇濈鐞嗭紝鍩轰簬 Redis銆? */
@Slf4j
@Component
public class HumanChatSessionManager {

    private static final String QUEUE_KEY = "icc:human:queue";
    private static final String SESSION_KEY_PREFIX = "icc:human:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final RedissonClient redissonClient;

    public HumanChatSessionManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private RScoredSortedSet<String> getQueue() {
        return redissonClient.getScoredSortedSet(QUEUE_KEY, StringCodec.INSTANCE);
    }

    private RBucket<String> getSessionBucket(String conversationId) {
        return redissonClient.getBucket(SESSION_KEY_PREFIX + conversationId, StringCodec.INSTANCE);
    }

    // ---- 鎺掗槦 ----

    public long enqueueCustomer(String conversationId, String userId) {
        RScoredSortedSet<String> queue = getQueue();
        long score = System.currentTimeMillis();
        queue.add(score, conversationId);
        log.info("customer enqueued: conversationId={}, userId={}, position={}",
                conversationId, userId, queue.rank(conversationId) + 1);
        return queue.rank(conversationId) + 1; // 1-based position
    }

    public String dequeueNext() {
        RScoredSortedSet<String> queue = getQueue();
        String next = queue.pollFirst();
        if (next != null) {
            log.info("dequeued: conversationId={}, remaining={}", next, queue.size());
        }
        return next;
    }

    public long getQueuePosition(String conversationId) {
        Integer rank = getQueue().rank(conversationId);
        return rank == null ? -1 : rank + 1;
    }

    public long getQueueLength() {
        return getQueue().size();
    }

    public boolean isInQueue(String conversationId) {
        return getQueue().contains(conversationId);
    }

    public void removeFromQueue(String conversationId) {
        getQueue().remove(conversationId);
    }

    // ---- 浼氳瘽 ----

    public void createSession(String conversationId, String humanAgentId, String humanAgentName) {
        JSONObject obj = new JSONObject();
        obj.put("humanAgentId", humanAgentId);
        obj.put("humanAgentName", humanAgentName);
        obj.put("status", "ACTIVE");
        obj.put("createdAt", System.currentTimeMillis());
        getSessionBucket(conversationId).set(obj.toJSONString(), SESSION_TTL.toSeconds(), TimeUnit.SECONDS);
        log.info("human session created: conversationId={}, agent={}", conversationId, humanAgentId);
    }

    public JSONObject getSessionInfo(String conversationId) {
        String json = getSessionBucket(conversationId).get();
        if (json == null) return null;
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasActiveSession(String conversationId) {
        JSONObject info = getSessionInfo(conversationId);
        return info != null && "ACTIVE".equals(info.getString("status"));
    }

    public void closeSession(String conversationId) {
        getSessionBucket(conversationId).delete();
        log.info("human session closed: conversationId={}", conversationId);
    }
}
