package cn.cloudcat.user.common.entity;

/**
 * 文档切分参数记录。
 *
 * @author Kang
 * @since 2026-06-02
 */
public record DocumentSplitParam(
        /** 切分方式 */
        String splitType,
        /** 分块大小 */
        Integer chunkSize,
        /** 重叠大小 */
        Integer overlap,
        /** 标题级别 */
        Integer titleLevel,
        /** 分隔符 */
        String separator,
        /** 正则表达式 */
        String regex) {
}
