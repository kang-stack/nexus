package cn.cloudcat.user.session;

import cn.cloudcat.user.session.model.SessionPhase;
import cn.cloudcat.user.session.entity.UserConversation;
import org.springframework.stereotype.Component;

/**
 * 寒暄阶段解析器。
 * <p>
 * 根据会话轮次和寒暄次数判断当前处于哪个阶段：
 * 首次（FIRST）、中期（MID）、重复寒暄（REPEAT）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
public class SessionPhaseResolver {

    /**
     * 根据会话状态和匹配结果解析寒暄阶段。
     *
     * @param conv               当前会话
     * @param lastChitchatCategory 上一轮命中的寒暄分类
     * @param matchedCategory    本轮命中的寒暄分类
     * @return 寒暄阶段
     */
    public SessionPhase resolve(UserConversation conv, String lastChitchatCategory, String matchedCategory) {
        int turn = conv.getTurnCount() != null ? conv.getTurnCount() : 0;
        int chitchatOnly = conv.getChitchatOnlyCount() != null ? conv.getChitchatOnlyCount() : 0;

        // 会话首轮且无寒暄历史 → 首次阶段
        if (turn == 0 && chitchatOnly == 0) {
            return SessionPhase.FIRST;
        }
        // 连续命中同一分类 → 重复寒暄阶段
        if (matchedCategory != null && matchedCategory.equals(lastChitchatCategory) && chitchatOnly > 0) {
            return SessionPhase.REPEAT;
        }
        return SessionPhase.MID;
    }
}
