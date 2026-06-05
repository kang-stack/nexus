package cn.cloudcat.intelligent.rag.util;

import cn.cloudcat.user.common.constant.RetrievalSource;
import cn.cloudcat.intelligent.rag.model.RagReference;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;

import java.util.List;
import java.util.stream.Collectors;

import static cn.cloudcat.user.common.constant.MetadataKeyConstant.*;

/**
 * RAG 引用工具类
 * <p>
 * 将 LangChain4j 检索结果 Content 对象转换为前端可展示的 RagReference 格式，
 * 提取文档 ID、标题、URL、片段内容、评分等元数据。
 *
 * @author Kang
 * @since 2026-06-02
 */
public class ReferenceUtil {

    /** 批量转换 Content 列表为 RagReference 列表 */
    public static List<RagReference> getRagReferences(List<Content> contents, RetrievalSource retrievalSource) {
        return contents.stream()
                .map(content -> getRagReference(content, retrievalSource))
                .collect(Collectors.toList());
    }

    /** 将单个 Content 转换为 RagReference，提取 ES 元数据字段 */
    public static RagReference getRagReference(Content content, RetrievalSource retrievalSource) {
        return RagReference.builder()
                .documentId(content.textSegment().metadata().getInteger(DOC_ID) + "")
                .documentTitle(content.textSegment().metadata().getString(FILE_NAME))
                .url(content.textSegment().metadata().getString(URL))
                .chunkId(content.textSegment().metadata().getString(CHUNK_ID))
                .chunkContent(content.textSegment().text())
                .similarityScore((Double) content.metadata().get(ContentMetadata.SCORE))
                .retrievalSource(retrievalSource)
                .rerankScore((Double) content.metadata().get(ContentMetadata.RERANKED_SCORE))
                .build();
    }
}
