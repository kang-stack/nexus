package cn.cloudcat.user.session.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话内对话状态（存于 acc_user_conversation.ext_json.dialogueState）。
 */
@Data
public class DialogueState {

    public static final String TASK_NONE = "NONE";

    private String activeTask = TASK_NONE;
    private int step;
    private Map<String, String> slots = new LinkedHashMap<>();
    /** 上轮未处理完的业务意图队列 */
    private List<String> pendingIntentQueue = new ArrayList<>();
    private String lastPrimaryIntent;
    private String lastRootCause;

    public static DialogueState empty() {
        return new DialogueState();
    }
}
