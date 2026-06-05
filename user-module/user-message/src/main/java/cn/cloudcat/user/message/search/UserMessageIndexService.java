package cn.cloudcat.user.message.search;

import cn.cloudcat.user.session.service.UserMessageLifecycleListener;
import cn.cloudcat.user.session.entity.UserMessage;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import cn.cloudcat.common.integration.elasticsearch.ElasticSearchProperties;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * acc_user_message 异步写入 ES（@Async），供运营检索；若后续需 MQ 解耦，优先 Kafka，不用 RocketMQ。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@ConditionalOnProperty(prefix = ElasticSearchProperties.PREFIX, name = "enabled", havingValue = "true")
@Slf4j
public class UserMessageIndexService implements UserMessageLifecycleListener {

    public static final String INDEX = "icc-cs-message";

    private final RestClient restClient;

    public UserMessageIndexService(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 异步将消息写入 ES 索引（eventListenerExecutor 线程池） */
    @Override
    @Async("eventListenerExecutor")
    public void onMessageSaved(UserMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        try {
            ensureIndex();
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", message.getId());
            doc.put("conversationId", message.getConversationId());
            doc.put("role", message.getRole());
            doc.put("contentType", message.getContentType());
            doc.put("content", message.getContent());
            doc.put("replySource", message.getReplySource());
            doc.put("intentCode", message.getIntentCode());
            doc.put("branchId", message.getBranchId());
            doc.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);

            Request request = new Request("PUT", "/" + INDEX + "/_doc/" + message.getId());
            request.setJsonEntity(JSON.toJSONString(doc));
            restClient.performRequest(request);
        } catch (Exception e) {
            log.warn("UserMessage ES index failed id={}", message.getId(), e);
        }
    }

    /** 确保 ES 索引存在，不存在时自动创建（含 mapping） */
    private void ensureIndex() throws Exception {
        try {
            restClient.performRequest(new Request("HEAD", "/" + INDEX));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                String mapping = """
                        {
                          "mappings": {
                            "properties": {
                              "id": { "type": "keyword" },
                              "conversationId": { "type": "keyword" },
                              "role": { "type": "keyword" },
                              "contentType": { "type": "keyword" },
                              "content": { "type": "text", "analyzer": "standard" },
                              "replySource": { "type": "keyword" },
                              "intentCode": { "type": "keyword" },
                              "createdAt": { "type": "date", "format": "strict_date_optional_time||epoch_millis||yyyy-MM-dd'T'HH:mm:ss" }
                            }
                          }
                        }
                        """;
                Request create = new Request("PUT", "/" + INDEX);
                create.setJsonEntity(mapping);
                Response resp = restClient.performRequest(create);
                log.info("Created ES index {} status={}", INDEX, resp.getStatusLine().getStatusCode());
            } else {
                throw e;
            }
        }
    }
}
