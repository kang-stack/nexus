package cn.cloudcat.user.message.search;

import cn.cloudcat.user.session.entity.UserMessage;
import cn.cloudcat.user.session.mapper.UserMessageMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息搜索服务
 * <p>
 * 提供管理后台的聊天消息搜索功能。优先使用 Elasticsearch 全文检索，
 * ES 不可用时自动降级到 MySQL LIKE 查询。
 * 支持按关键词、会话 ID、角色过滤，结果按时间倒序。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class UserMessageSearchService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestClient restClient;
    private final UserMessageMapper messageMapper;

    public UserMessageSearchService(
            @Autowired(required = false) RestClient restClient,
            UserMessageMapper messageMapper) {
        this.restClient = restClient;
        this.messageMapper = messageMapper;
    }

    /** 搜索消息：ES 优先，失败或未启用时降级到 MySQL LIKE 查询 */
    public Map<String, Object> search(String keyword, String conversationId, String role, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        if (restClient == null) {
            Map<String, Object> fallback = searchFromMysql(keyword, conversationId, role, safePage, safeSize);
            fallback.put("source", "mysql");
            fallback.put("fallback", true);
            fallback.put("warning", "Elasticsearch 未启用，已使用 MySQL 检索");
            return fallback;
        }
        try {
            JSONObject body = buildQuery(keyword, conversationId, role, safePage, safeSize);
            Request request = new Request("POST", "/" + UserMessageIndexService.INDEX + "/_search");
            request.setJsonEntity(body.toJSONString());
            Response response = restClient.performRequest(request);
            Map<String, Object> result = parseResponse(response);
            result.put("source", "elasticsearch");
            return result;
        } catch (Exception e) {
            log.warn("UserMessage ES search failed, fallback to MySQL keyword={}", keyword, e);
            Map<String, Object> fallback = searchFromMysql(keyword, conversationId, role, safePage, safeSize);
            fallback.put("source", "mysql");
            fallback.put("fallback", true);
            String msg = e.getMessage() != null ? e.getMessage() : "search failed";
            fallback.put("warning", "Elasticsearch 不可用，已使用 MySQL 检索：" + msg);
            return fallback;
        }
    }

    /** MySQL 降级搜索：使用 LIKE 模糊匹配 */
    private Map<String, Object> searchFromMysql(
            String keyword, String conversationId, String role, int page, int size) {
        LambdaQueryWrapper<UserMessage> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(UserMessage::getContent, keyword.trim());
        }
        if (conversationId != null && !conversationId.isBlank()) {
            q.eq(UserMessage::getConversationId, conversationId.trim());
        }
        if (role != null && !role.isBlank()) {
            q.eq(UserMessage::getRole, role.trim());
        }
        q.orderByDesc(UserMessage::getCreatedAt);

        Page<UserMessage> pageResult = messageMapper.selectPage(new Page<>(page + 1L, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserMessage msg : pageResult.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", msg.getId());
            row.put("conversationId", msg.getConversationId());
            row.put("role", msg.getRole());
            row.put("contentType", msg.getContentType());
            row.put("content", msg.getContent());
            row.put("replySource", msg.getReplySource());
            row.put("intentCode", msg.getIntentCode());
            row.put("createdAt", msg.getCreatedAt() != null ? ISO.format(msg.getCreatedAt()) : null);
            items.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("items", items);
        return result;
    }

    /** 构建 ES bool 查询 DSL：must=match 关键词，filter=term 过滤会话/角色 */
    private JSONObject buildQuery(String keyword, String conversationId, String role, int page, int size) {
        JSONObject bool = new JSONObject();
        JSONArray must = new JSONArray();
        JSONArray filter = new JSONArray();

        if (keyword != null && !keyword.isBlank()) {
            JSONObject match = new JSONObject();
            match.put("match", JSONObject.of("content", keyword.trim()));
            must.add(match);
        } else {
            // 无关键词时 match_all
            must.add(JSONObject.of("match_all", JSONObject.of()));
        }

        if (conversationId != null && !conversationId.isBlank()) {
            filter.add(JSONObject.of("term", JSONObject.of("conversationId", conversationId.trim())));
        }
        if (role != null && !role.isBlank()) {
            filter.add(JSONObject.of("term", JSONObject.of("role", role.trim())));
        }

        bool.put("must", must);
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }

        JSONObject query = new JSONObject();
        query.put("query", JSONObject.of("bool", bool));
        query.put("from", page * size);
        query.put("size", size);
        query.put("sort", JSONArray.of(JSONObject.of("createdAt", JSONObject.of("order", "desc"))));
        return query;
    }

    /** 解析 ES 搜索响应，提取 hits.total 和 hits.hits._source */
    private Map<String, Object> parseResponse(Response response) throws IOException {
        String raw = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject root = JSON.parseObject(raw);
        JSONObject hitsObj = root.getJSONObject("hits");
        long total = 0;
        if (hitsObj != null) {
            Object totalNode = hitsObj.get("total");
            if (totalNode instanceof JSONObject totalObj) {
                total = totalObj.getLongValue("value");
            } else if (totalNode instanceof Number n) {
                total = n.longValue();
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        JSONArray hits = hitsObj != null ? hitsObj.getJSONArray("hits") : null;
        if (hits != null) {
            for (int i = 0; i < hits.size(); i++) {
                JSONObject hit = hits.getJSONObject(i);
                JSONObject source = hit.getJSONObject("_source");
                if (source == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", source.getString("id"));
                row.put("conversationId", source.getString("conversationId"));
                row.put("role", source.getString("role"));
                row.put("contentType", source.getString("contentType"));
                row.put("content", source.getString("content"));
                row.put("replySource", source.getString("replySource"));
                row.put("intentCode", source.getString("intentCode"));
                row.put("createdAt", source.getString("createdAt"));
                items.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("items", items);
        return result;
    }
}
