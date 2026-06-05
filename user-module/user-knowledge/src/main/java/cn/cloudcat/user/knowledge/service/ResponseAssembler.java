package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.model.ReplySegment;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 话术模板填充 + 结构化消息组装。
 *
 * 将 acc_user_kb_reply_script 话术模板 + payload_json 转换为 ReplySegment 列表。
 * 模板变量格式：${varName}
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class ResponseAssembler {

    /** 模板变量模式：${varName} */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    /**
     * 填充模板变量并组装 ReplySegment。
     *
     * @param contentTemplate 话术模板
     * @param msgType         消息类型
     * @param linksJson       links JSON 字符串（可为 null）
     * @param buttonJson      button JSON 字符串（可为 null）
     * @param actionCode      系统动作代码（可为 null）
     * @param isEnding        是否结束语
     * @param seq             序号
     * @param totalSeq        总数
     * @param variables       模板变量
     */
    public ReplySegment assemble(String contentTemplate, String msgType,
                                  String linksJson, String buttonJson,
                                  String actionCode, boolean isEnding,
                                  int seq, int totalSeq,
                                  Map<String, Object> variables) {
        String content = fillTemplate(contentTemplate, variables);

        ReplySegment seg = new ReplySegment();
        seg.setMessageType(msgType != null ? msgType : "TEXT");
        seg.setContent(content);
        seg.setSequenceNumber(seq);
        seg.setTotalSequence(totalSeq);
        seg.setEnding(isEnding);
        seg.setActionCode(actionCode);

        if (linksJson != null && !linksJson.isBlank()) {
            try {
                List<Map<String, Object>> links = JSON.parseArray(linksJson)
                        .stream()
                        .map(o -> (JSONObject) o)
                        .map(obj -> {
                            Map<String, Object> link = new LinkedHashMap<>();
                            link.put("text", obj.getString("text"));
                            link.put("type", obj.getString("type"));
                            link.put("url", obj.getString("url"));
                            return link;
                        })
                        .collect(Collectors.toList());
                seg.setLinks(links);
            } catch (Exception e) {
                log.warn("Failed to parse links JSON: {}", linksJson, e);
            }
        }

        if (buttonJson != null && !buttonJson.isBlank()) {
            try {
                JSONObject obj = JSON.parseObject(buttonJson);
                Map<String, Object> btn = new LinkedHashMap<>();
                btn.put("text", obj.getString("text"));
                btn.put("type", obj.getString("type"));
                btn.put("url", obj.getString("url"));
                btn.put("action", obj.getString("action"));
                seg.setButton(btn);
            } catch (Exception e) {
                log.warn("Failed to parse button JSON: {}", buttonJson, e);
            }
        }

        return seg;
    }

    /**
     * 单条话术快速组装（无 links / button）。
     */
    public ReplySegment simpleText(String contentTemplate, Map<String, Object> variables,
                                    int seq, int totalSeq) {
        return assemble(contentTemplate, "TEXT", null, null, null, false, seq, totalSeq, variables);
    }

    /**
     * 构建诊断类型 ReplySegment。
     * content 为空时返回 null，调用方跳过。
     *
     * @param diagnosisText 诊断文本
     * @param seq           序号
     * @param totalSeq      总数
     * @return ReplySegment 或 null
     */
    public ReplySegment assembleDiagnosis(String diagnosisText, int seq, int totalSeq) {
        if (diagnosisText == null || diagnosisText.isBlank()) {
            return null;
        }
        ReplySegment seg = new ReplySegment();
        seg.setMessageType(ReplySegment.DIAGNOSIS);
        seg.setContent(diagnosisText.trim());
        seg.setSequenceNumber(seq);
        seg.setTotalSequence(totalSeq);
        return seg;
    }

    /**
     * 构建转人工类型 ReplySegment。
     *
     * @param content  提示文本
     * @param seq      序号
     * @param totalSeq 总数
     * @return ReplySegment
     */
    public ReplySegment assembleHumanService(String content, int seq, int totalSeq) {
        ReplySegment seg = new ReplySegment();
        seg.setMessageType(ReplySegment.HUMAN_SERVICE);
        seg.setContent(content != null ? content : "正在为您转接人工客服...");
        seg.setSequenceNumber(seq);
        seg.setTotalSequence(totalSeq);
        return seg;
    }

    /** 将模板中的 ${varName} 占位符替换为 variables 中的对应值 */
    public String fillTemplate(String template, Map<String, Object> variables) {
        if (template == null) return "";
        if (variables == null || variables.isEmpty()) return template;

        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            Object value = variables.get(varName);
            String replacement = value != null ? value.toString() : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
