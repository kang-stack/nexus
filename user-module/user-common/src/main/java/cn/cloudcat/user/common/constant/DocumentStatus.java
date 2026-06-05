package cn.cloudcat.user.common.constant;

/**
 * 文档处理生命周期状态。
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum DocumentStatus {
    /**
     * 初始状态
     */
    INIT,
    /**
     * 上传完成
     */
    UPLOADED,
    /**
     * 转换中
     */
    CONVERTING,
    /**
     * 转换完成
     */
    CONVERTED,
    /**
     * 分段进行中
     */
    SPLITTING,
    /**
     * 分块完成
     */
    CHUNKED,
    /**
     * 向量化进行中
     */
    EMBEDDING,
    /**
     * 向量存储完成
     */
    VECTOR_STORED,
    /**
     * 存储完成（不需要向量存储的使用这个状态）
     */
    STORED,
    /**
     * 已停用
     */
    DISABLED;
}
