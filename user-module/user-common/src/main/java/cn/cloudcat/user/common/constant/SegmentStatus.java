package cn.cloudcat.user.common.constant;

/**
 * 文档片段存储状态。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum SegmentStatus {
    /**
     * 关系型数据库存储完成
     */
    STORED,
    /**
     * 向量数据库存储完成（非必须）
     */
    VECTOR_STORED;
}
