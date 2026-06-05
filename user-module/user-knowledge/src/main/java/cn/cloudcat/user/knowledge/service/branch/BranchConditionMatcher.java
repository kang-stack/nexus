package cn.cloudcat.user.knowledge.service.branch;

import cn.cloudcat.user.common.entity.KbBranch;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * 评估 acc_user_kb_branch.condition_json（结构化 AND/OR）；无条件分支始终命中。
 * <p>
 * 支持操作符：eq, ne, gt, gte, lt, lte, in, between, exists, not_exists。
 * 数值比较操作（gt/gte/lt/lte/between）使用 BigDecimal 确保精度。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
public class BranchConditionMatcher {

    /** 评估分支的条件 JSON 是否匹配当前上下文；无条件分支始终返回 true */
    public boolean matches(KbBranch branch, Map<String, Object> context) {
        String json = branch.getConditionJson();
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            JSONObject root = JSON.parseObject(json);
            JSONArray items = root.getJSONArray("items");
            if (items == null || items.isEmpty()) {
                return true;
            }
            String match = root.getString("match");
            boolean and = match == null || "AND".equalsIgnoreCase(match);
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                boolean ok = evalItem(item, context);
                if (and && !ok) {
                    return false;
                }
                if (!and && ok) {
                    return true;
                }
            }
            return and;
        } catch (Exception e) {
            return false;
        }
    }

    /** 评估单个条件项（一条 JSON 规则） */
    private boolean evalItem(JSONObject item, Map<String, Object> context) {
        String field = item.getString("field");
        String op = item.getString("op");
        String val = item.getString("val");
        Object actual = context.get(field);

        if (op == null) {
            return false;
        }

        return switch (op.toLowerCase()) {
            case "exists" -> actual != null && !String.valueOf(actual).isBlank();
            case "not_exists" -> actual == null || String.valueOf(actual).isBlank();
            case "eq" -> evalEq(actual, val);
            case "ne" -> !evalEq(actual, val);
            case "gt" -> evalCompare(actual, val) > 0;
            case "gte" -> evalCompare(actual, val) >= 0;
            case "lt" -> evalCompare(actual, val) < 0;
            case "lte" -> evalCompare(actual, val) <= 0;
            case "in" -> evalIn(actual, val);
            case "between" -> evalBetween(actual, val);
            default -> false;
        };
    }

    /** 等值比较：将 actual 转为字符串后与 expected 做 trim 后精确匹配 */
    private boolean evalEq(Object actual, String expected) {
        if (expected == null) {
            return actual == null || String.valueOf(actual).isBlank();
        }
        return actual != null && Objects.equals(String.valueOf(actual).trim(), expected.trim());
    }

    /** 数值比较：优先使用 BigDecimal 精确比较，非数字回退到字典序比较 */
    private int evalCompare(Object actual, String expected) {
        if (actual == null || expected == null || expected.isBlank()) {
            return -2; // invalid comparison
        }
        try {
            BigDecimal a = new BigDecimal(String.valueOf(actual).trim());
            BigDecimal b = new BigDecimal(expected.trim());
            return a.compareTo(b);
        } catch (NumberFormatException e) {
            // fallback to lexicographic comparison for non-numeric values
            return String.valueOf(actual).trim().compareTo(expected.trim());
        }
    }

    /** 判断 actual 是否在逗号分隔的值列表中 */
    private boolean evalIn(Object actual, String val) {
        if (val == null) {
            return false;
        }
        String a = actual != null ? String.valueOf(actual).trim() : "";
        String[] parts = val.split(",");
        for (String p : parts) {
            if (a.equals(p.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 判断 actual 是否在 "lower,upper" 范围内（含边界） */
    private boolean evalBetween(Object actual, String val) {
        if (val == null || !val.contains(",")) {
            return false;
        }
        String[] parts = val.split(",");
        if (parts.length != 2) {
            return false;
        }
        String lower = parts[0].trim();
        String upper = parts[1].trim();
        return evalCompare(actual, lower) >= 0 && evalCompare(actual, upper) <= 0;
    }
}
