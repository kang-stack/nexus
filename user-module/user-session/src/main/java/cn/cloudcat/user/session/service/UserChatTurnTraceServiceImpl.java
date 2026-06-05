package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.entity.UserChatTurnTrace;
import cn.cloudcat.user.common.entity.User;
import cn.cloudcat.user.session.mapper.UserChatTurnTraceMapper;
import cn.cloudcat.user.common.mapper.UserMapper;
import cn.cloudcat.user.session.support.UserIdParser;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 Skill 链决策轨迹异步写入 acc_user_chat_turn_trace。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserChatTurnTraceServiceImpl implements UserChatTurnTraceService {

    private static final int USER_TEXT_MAX = 500;

    private final UserChatTurnTraceMapper traceMapper;
    private final UserMapper userMapper;

    @Override
    @Async("eventListenerExecutor")
    @Transactional
    public void saveAsync(String turnId, String conversationId, String userMessageId, String userId,
                          String userText, String outcome, long totalCostMs,
                          String resolvedIntent, String primaryRootCause, int segmentCount,
                          Map<String, Object> traceJson) {
        if (turnId == null || turnId.isBlank()) {
            return;
        }
        try {
            User user = resolveUser(userId);
            Map<String, Object> json = traceJson != null ? new LinkedHashMap<>(traceJson) : new LinkedHashMap<>();
            enrichTraceJsonUser(json, userId, user);

            UserChatTurnTrace row = new UserChatTurnTrace();
            row.setId(turnId);
            row.setTurnId(turnId);
            row.setConversationId(conversationId);
            row.setUserMessageId(userMessageId);
            row.setUserId(UserIdParser.toStoredLong(userId));
            if (userId != null && !userId.isBlank()) {
                row.setUserBizId(userId.trim());
            }
            if (user != null) {
                row.setUsername(user.getUsername());
                row.setUserBizId(user.getUserId());
            }
            row.setUserText(abbreviate(userText));
            row.setOutcome(outcome != null ? outcome : "UNKNOWN");
            row.setResolvedIntent(resolvedIntent);
            row.setPrimaryRootCause(primaryRootCause);
            row.setSegmentCount(segmentCount != 0 ? segmentCount : 0);
            row.setTotalCostMs(totalCostMs);
            row.setTraceJson(JSON.toJSONString(json));
            row.setCreatedAt(LocalDateTime.now());
            traceMapper.insert(row);
            log.info("acc_user_chat_turn_trace saved turnId={} conv={} user={} outcome={} costMs={}",
                    turnId, conversationId, row.getUsername(), outcome, totalCostMs);
        } catch (Exception e) {
            log.warn("acc_user_chat_turn_trace save failed turnId={} conv={}", turnId, conversationId, e);
        }
    }

    @Override
    public List<UserChatTurnTrace> listByConversation(String conversationId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        List<UserChatTurnTrace> list = traceMapper.selectList(new LambdaQueryWrapper<UserChatTurnTrace>()
                .eq(UserChatTurnTrace::getConversationId, conversationId)
                .orderByDesc(UserChatTurnTrace::getCreatedAt)
                .last("LIMIT " + cap));
        return list;
    }

    @Override
    public Map<String, Object> pageTraces(String conversationId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        LambdaQueryWrapper<UserChatTurnTrace> q = new LambdaQueryWrapper<UserChatTurnTrace>()
                .orderByDesc(UserChatTurnTrace::getCreatedAt);
        if (conversationId != null && !conversationId.isBlank()) {
            q.eq(UserChatTurnTrace::getConversationId, conversationId.trim());
        }
        Page<UserChatTurnTrace> pageResult = traceMapper.selectPage(new Page<>(safePage + 1L, safeSize), q);
        Map<String, User> userIndex = indexUsers(pageResult.getRecords());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", pageResult.getRecords().stream()
                .map(t -> toSummaryView(t, userIndex))
                .toList());
        result.put("total", pageResult.getTotal());
        result.put("page", safePage);
        result.put("size", safeSize);
        if (conversationId != null && !conversationId.isBlank()) {
            result.put("conversationId", conversationId.trim());
        }
        return result;
    }

    @Override
    public Optional<UserChatTurnTrace> getByTurnId(String turnId) {
        if (turnId == null || turnId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(traceMapper.selectById(turnId));
    }

    @Override
    public Map<String, Object> toSummaryView(UserChatTurnTrace trace) {
        return toSummaryView(trace, indexUsers(List.of(trace)));
    }

    @Override
    public Map<String, Object> toView(UserChatTurnTrace trace) {
        Map<String, User> userIndex = indexUsers(List.of(trace));
        Map<String, Object> view = toSummaryView(trace, userIndex);
        view.put("userMessageId", trace.getUserMessageId());
        if (trace.getTraceJson() != null && !trace.getTraceJson().isBlank()) {
            try {
                JSONObject parsed = JSON.parseObject(trace.getTraceJson());
                view.put("trace", parsed);
            } catch (Exception e) {
                view.put("traceRaw", trace.getTraceJson());
            }
        }
        return view;
    }

    private Map<String, Object> toSummaryView(UserChatTurnTrace trace, Map<String, User> userIndex) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("turnId", trace.getTurnId());
        view.put("conversationId", trace.getConversationId());
        view.put("userText", trace.getUserText());
        view.put("outcome", trace.getOutcome());
        view.put("resolvedIntent", trace.getResolvedIntent());
        view.put("primaryRootCause", trace.getPrimaryRootCause());
        view.put("segmentCount", trace.getSegmentCount());
        view.put("totalCostMs", trace.getTotalCostMs());
        view.put("createdAt", trace.getCreatedAt());
        applyUserFields(view, trace, userIndex);
        return view;
    }

    private void applyUserFields(Map<String, Object> view, UserChatTurnTrace trace, Map<String, User> userIndex) {
        String bizId = trace.getUserBizId();
        String username = trace.getUsername();
        if (bizId == null || username == null) {
            User user = lookupUser(trace, userIndex);
            if (user != null) {
                bizId = user.getUserId();
                username = user.getUsername();
            }
        }
        if (bizId != null && !bizId.isBlank()) {
            view.put("userId", bizId);
        } else if (trace.getUserId() != null) {
            view.put("userId", String.valueOf(trace.getUserId()));
        }
        if (username != null && !username.isBlank()) {
            view.put("username", username);
        }
    }

    private User lookupUser(UserChatTurnTrace trace, Map<String, User> userIndex) {
        if (trace.getUserBizId() != null && !trace.getUserBizId().isBlank()) {
            User hit = userIndex.get(trace.getUserBizId());
            if (hit != null) {
                return hit;
            }
        }
        if (trace.getUserId() != null) {
            return userIndex.get("pk:" + trace.getUserId());
        }
        return null;
    }

    private Map<String, User> indexUsers(List<UserChatTurnTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return Map.of();
        }
        Set<String> bizIds = traces.stream()
                .map(UserChatTurnTrace::getUserBizId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        Set<Long> pkIds = traces.stream()
                .map(UserChatTurnTrace::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, User> index = new LinkedHashMap<>();
        if (!bizIds.isEmpty()) {
            List<User> byBiz = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getUserId, bizIds));
            for (User u : byBiz) {
                index.put(u.getUserId(), u);
            }
        }
        if (!pkIds.isEmpty()) {
            List<User> byPk = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getId, pkIds));
            for (User u : byPk) {
                index.put("pk:" + u.getId(), u);
                index.putIfAbsent(u.getUserId(), u);
            }
        }
        return index;
    }

    private User resolveUser(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) {
            return null;
        }
        User byBiz = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserId, userIdStr.trim())
                .last("LIMIT 1"));
        if (byBiz != null) {
            return byBiz;
        }
        Long stored = UserIdParser.toStoredLong(userIdStr);
        if (stored == null) {
            return null;
        }
        return userMapper.selectById(stored);
    }

    private static void enrichTraceJsonUser(Map<String, Object> json, String userIdStr, User user) {
        if (userIdStr != null && !userIdStr.isBlank()) {
            json.put("userId", userIdStr.trim());
        }
        if (user != null) {
            json.put("userId", user.getUserId());
            json.put("username", user.getUsername());
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.replace('\n', ' ').trim();
        return trimmed.length() <= USER_TEXT_MAX ? trimmed : trimmed.substring(0, USER_TEXT_MAX);
    }
}
