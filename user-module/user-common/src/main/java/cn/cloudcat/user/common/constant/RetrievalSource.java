package cn.cloudcat.user.common.constant;

/**
 * 检索来源类型。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum RetrievalSource {
    /** 向量语义检索 */
    VECTOR,
    /** 关键词全文检索 */
    KEYWORD,
    /** 混合检索（向量+关键词） */
    HYBRID,
    /** 重排序后的结果 */
    RERANK
}
