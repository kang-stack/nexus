package cn.cloudcat.intelligent.rag.model;

import cn.cloudcat.user.common.constant.RetrievalSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * RAG 检索引用模型
 * <p>
 * 封装单个检索片段的元数据，包含文档信息、片段内容、
 * 向量相似度评分和重排序评分，供前端渲染引用来源卡片。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagReference {
    private String documentId;
    private String url;
    private String documentTitle;
    private String chunkId;
    private String chunkContent;
    private Double similarityScore;
    private Double rerankScore;
    private RetrievalSource retrievalSource;
    private Map<String, Object> metadata;
}
