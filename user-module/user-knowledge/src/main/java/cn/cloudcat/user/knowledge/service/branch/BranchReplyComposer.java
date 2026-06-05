package cn.cloudcat.user.knowledge.service.branch;

import cn.cloudcat.user.common.entity.KbReplyScript;
import cn.cloudcat.user.common.model.ReplySegment;
import cn.cloudcat.user.knowledge.service.ResponseAssembler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分支回复组装器：将 acc_user_kb_reply_script 列表逐个交由 ResponseAssembler 填充模板变量，
 * 生成 ReplySegment 列表。ending 仅由 ClosingScript 等业务场景单独设置。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
public class BranchReplyComposer {

    private final ResponseAssembler responseAssembler;

    public BranchReplyComposer(ResponseAssembler responseAssembler) {
        this.responseAssembler = responseAssembler;
    }

    /** 将分支回复脚本列表组装为 ReplySegment 序列 */
    public List<ReplySegment> compose(List<KbReplyScript> scripts, Map<String, Object> variables) {
        List<ReplySegment> segments = new ArrayList<>();
        int total = scripts.size();
        int idx = 0;
        for (KbReplyScript script : scripts) {
            idx++;
            ReplySegment seg = responseAssembler.assemble(
                    script.getTextTemplate(),
                    script.getContentType(),
                    null,
                    script.getPayloadJson(),
                    null,
                    false,
                    idx,
                    total,
                    variables);
            segments.add(seg);
        }
        return segments;
    }
}
