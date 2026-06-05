package cn.cloudcat.user.common.constant;

/**
 * 元数据的键常量
 * @author Kang
 * @since 2026-06-02
 */
public class MetadataKeyConstant {
    /**
     * 文件名称
     */
    public static final String FILE_NAME = "fileName";


    /** 文档ID */
    public static final String DOC_ID = "docId";

    /** 块ID */
    public static final String CHUNK_ID = "chunkId";

    /**
     * 父块ID
     */
    public static final String PARENT_CHUNK_ID = "parentChunkId";

    /**
     * 同级块ID
     */
    public static final String BROTHER_CHUNK_ID = "brotherChunkId";

    /** 同级块的序号 */
    public static final String BROTHER_CHUNK_INDEX = "brotherChunkIndex";

    /** 同级块的总数 */
    public static final String BROTHER_CHUNK_TOTAL = "brotherChunkTotal";

    /**
     * 头级别
     */
    public static final String HEADER_LEVEL = "headerLevel";

    /**
     * 访问权限
     */
    public static final String ACCESSIBLE_BY = "accessibleBy";

    /**
     * 文件地址
     */
    public static final String URL = "url";

    /**
     * 文件版本
     */
    public static final String VERSION = "version";

    /**
     * 分类
     */
    public static final String CATEGORY = "category";

    /**
     * 摘要
     */
    public static final String SUMMARY = "summary";

    /**
     * 关键字
     */
    public static final String KEYWORDS = "keywords";

    /**
     * 跳过embedding标记，true表示不需要做embedding
     */
    public static final String SKIP_EMBEDDING = "skipEmbedding";
}
