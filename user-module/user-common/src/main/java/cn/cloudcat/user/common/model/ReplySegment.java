package cn.cloudcat.user.common.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条回复消息段 — 供 MultiMessageOrchestrator 编排使用。
 */
public class ReplySegment {

    /** 诊断文本，需后续走 RAG 检索增强 */
    public static final String DIAGNOSIS = "DIAGNOSIS";

    /** 满意度卡片 */
    public static final String SATISFACTION = "SATISFACTION";

    /** 转人工客服 */
    public static final String HUMAN_SERVICE = "HUMAN_SERVICE";

    /** TEXT / TEXT_LINKS / BUTTON / BUTTON_LINKS */
    private String messageType;

    /** 填充占位符后的话术文本 */
    private String content;

    /** 文字链接列表 */
    private List<Map<String, Object>> links;

    /** 操作按钮 */
    private Map<String, Object> button;

    /** 需自动执行的系统动作代码 */
    private String actionCode;

    /** 后端消息ID（用于满意度评价等回传） */
    private String messageId;

    /** 是否为结束语 */
    private boolean ending;

    /** 消息序号 */
    private int sequenceNumber;

    /** 总消息数 */
    private int totalSequence;

    /** 意图标签（多任务编排） */
    private String intentLabel;

    /** 任务序号（0-based） */
    private Integer taskIndex;

    /** 任务总数 */
    private Integer taskTotal;

    /** 是否为新气泡（前端据此创建新 assistant 消息） */
    private boolean newBubble;

    public ReplySegment() {}

    public ReplySegment(String messageType, String content, int sequenceNumber, int totalSequence) {
        this.messageType = messageType;
        this.content = content;
        this.sequenceNumber = sequenceNumber;
        this.totalSequence = totalSequence;
    }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<Map<String, Object>> getLinks() { return links; }
    public void setLinks(List<Map<String, Object>> links) { this.links = links; }

    public Map<String, Object> getButton() { return button; }
    public void setButton(Map<String, Object> button) { this.button = button; }

    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }

    public boolean isEnding() { return ending; }
    public void setEnding(boolean ending) { this.ending = ending; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public int getTotalSequence() { return totalSequence; }
    public void setTotalSequence(int totalSequence) { this.totalSequence = totalSequence; }

    public String getIntentLabel() { return intentLabel; }
    public void setIntentLabel(String intentLabel) { this.intentLabel = intentLabel; }

    public Integer getTaskIndex() { return taskIndex; }
    public void setTaskIndex(Integer taskIndex) { this.taskIndex = taskIndex; }

    public Integer getTaskTotal() { return taskTotal; }
    public void setTaskTotal(Integer taskTotal) { this.taskTotal = taskTotal; }

    public boolean isNewBubble() { return newBubble; }
    public void setNewBubble(boolean newBubble) { this.newBubble = newBubble; }

    public Map<String, Object> toDbPatch() {
        Map<String, Object> patch = new LinkedHashMap<>();
        if (messageType != null) patch.put("messageType", messageType);
        if (links != null && !links.isEmpty()) patch.put("links", links);
        if (button != null && !button.isEmpty()) patch.put("button", button);
        patch.put("sequenceNumber", sequenceNumber);
        patch.put("totalSequence", totalSequence);
        patch.put("isEnding", ending);
        return patch;
    }

    public Map<String, Object> toEventData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageType", messageType);
        data.put("content", content);
        if (links != null && !links.isEmpty()) data.put("links", links);
        if (button != null && !button.isEmpty()) data.put("button", button);
        if (messageId != null) data.put("messageId", messageId);
        data.put("sequenceNumber", sequenceNumber);
        data.put("totalSequence", totalSequence);
        data.put("isEnding", ending);
        if (intentLabel != null) data.put("intentLabel", intentLabel);
        if (taskIndex != null) data.put("taskIndex", taskIndex);
        if (taskTotal != null) data.put("taskTotal", taskTotal);
        data.put("newBubble", newBubble);
        return data;
    }
}
