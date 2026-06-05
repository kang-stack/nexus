package cn.cloudcat.user.agent.skill;

/**
 * V2 Skill 链接口。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface ChatSkill {

    /** 越小越先执行 */
    int order();

    String name();

    boolean supports(SkillContext ctx);

    void execute(SkillContext ctx);

    /**
     * 主链短路（handled/needRag）后仍执行，用于结束语、会话状态等后处理。
     */
    default boolean runInPostPhase() {
        return order() >= 40;
    }
}
