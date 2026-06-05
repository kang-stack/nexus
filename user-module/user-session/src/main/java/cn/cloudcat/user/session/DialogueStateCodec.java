package cn.cloudcat.user.session;

import cn.cloudcat.user.session.model.DialogueState;
import cn.cloudcat.user.session.entity.UserConversation;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * ext_json 中 dialogueState 字段读写。
 */
public final class DialogueStateCodec {

    private static final String KEY = "dialogueState";

    private DialogueStateCodec() {
    }

    public static DialogueState read(UserConversation conv) {
        if (conv == null || conv.getExtJson() == null || conv.getExtJson().isBlank()) {
            return DialogueState.empty();
        }
        try {
            JSONObject root = JSON.parseObject(conv.getExtJson());
            JSONObject node = root.getJSONObject(KEY);
            if (node == null) {
                return DialogueState.empty();
            }
            return node.to(DialogueState.class);
        } catch (Exception e) {
            return DialogueState.empty();
        }
    }

    public static String write(UserConversation conv, DialogueState state) {
        JSONObject root;
        if (conv.getExtJson() != null && !conv.getExtJson().isBlank()) {
            try {
                root = JSON.parseObject(conv.getExtJson());
            } catch (Exception e) {
                root = new JSONObject();
            }
        } else {
            root = new JSONObject();
        }
        root.put(KEY, state);
        return root.toJSONString();
    }
}
