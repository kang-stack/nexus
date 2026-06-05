package cn.cloudcat.user.session;

import cn.cloudcat.user.session.entity.UserConversation;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 会话扩展 JSON 字段（ext_json）读写工具。
 * <p>
 * 会话表中 ext_json 列存储动态键值数据（如寒暄分类等），
 * 此类提供类型安全的读取和更新方法。
 *
 * @author Kang
 * @since 2026-06-02
 */
public final class ConversationExt {

    /** ext_json 中存储最近一次寒暄分类的 key */
    private static final String LAST_CHITCHAT = "lastChitchatCategory";

    private ConversationExt() {}

    /** 读取 ext_json 中存储的最近寒暄分类编码 */
    public static String lastChitchatCategory(UserConversation conv) {
        if (conv == null || conv.getExtJson() == null || conv.getExtJson().isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(conv.getExtJson()).getString(LAST_CHITCHAT);
        } catch (Exception e) {
            return null;
        }
    }

    /** 更新 ext_json 中的最近寒暄分类编码，返回新的 JSON 字符串 */
    public static String withLastChitchatCategory(UserConversation conv, String categoryCode) {
        JSONObject obj;
        if (conv.getExtJson() != null && !conv.getExtJson().isBlank()) {
            try {
                obj = JSON.parseObject(conv.getExtJson());
            } catch (Exception e) {
                obj = new JSONObject();
            }
        } else {
            obj = new JSONObject();
        }
        obj.put(LAST_CHITCHAT, categoryCode);
        return obj.toJSONString();
    }
}
