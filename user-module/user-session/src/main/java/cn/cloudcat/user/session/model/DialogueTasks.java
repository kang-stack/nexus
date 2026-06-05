package cn.cloudcat.user.session.model;

/**
 * 对话任务编码与业务意图映射。
 */
public final class DialogueTasks {

    public static final String NETWORK_UNKNOWN_PROBE = "NETWORK_UNKNOWN_PROBE";

    private DialogueTasks() {
    }

    public static String intentForTask(String activeTask) {
        if (activeTask == null) {
            return null;
        }
        return switch (activeTask) {
            case NETWORK_UNKNOWN_PROBE -> "网络问题";
            default -> null;
        };
    }

    public static boolean isActive(String activeTask) {
        return activeTask != null
                && !DialogueState.TASK_NONE.equals(activeTask)
                && !activeTask.isBlank();
    }
}
