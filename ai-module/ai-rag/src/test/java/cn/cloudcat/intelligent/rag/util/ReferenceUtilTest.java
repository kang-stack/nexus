package cn.cloudcat.intelligent.rag.util;

import cn.cloudcat.user.common.constant.RetrievalSource;
import cn.cloudcat.intelligent.rag.model.RagReference;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cn.cloudcat.user.common.constant.MetadataKeyConstant.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Kang
 * @since 2026-05-28
 */
class ReferenceUtilTest {

    @Test
    void shouldMapAllFieldsFromContentToRagReference() {
        TextSegment segment = TextSegment.from("这是检索到的文档内容片段", new Metadata(Map.of(
                DOC_ID, 123,
                FILE_NAME, "设备使用手册.pdf",
                URL, "https://minio.example.com/docs/manual.pdf",
                CHUNK_ID, "chunk-abc-001"
        )));
        Content content = Content.from(segment, Map.of(
                ContentMetadata.SCORE, 0.85,
                ContentMetadata.RERANKED_SCORE, 0.92
        ));

        RagReference ref = ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID);

        assertThat(ref.getDocumentId()).isEqualTo("123");
        assertThat(ref.getDocumentTitle()).isEqualTo("设备使用手册.pdf");
        assertThat(ref.getUrl()).isEqualTo("https://minio.example.com/docs/manual.pdf");
        assertThat(ref.getChunkId()).isEqualTo("chunk-abc-001");
        assertThat(ref.getChunkContent()).isEqualTo("这是检索到的文档内容片段");
        assertThat(ref.getSimilarityScore()).isEqualTo(0.85);
        assertThat(ref.getRerankScore()).isEqualTo(0.92);
        assertThat(ref.getRetrievalSource()).isEqualTo(RetrievalSource.HYBRID);
    }

    @Test
    void shouldHandleMissingOptionalFields() {
        TextSegment segment = TextSegment.from("内容");
        Content content = Content.from(segment, Map.of());

        RagReference ref = ReferenceUtil.getRagReference(content, RetrievalSource.VECTOR);

        assertThat(ref.getDocumentId()).isEqualTo("null");
        assertThat(ref.getDocumentTitle()).isNull();
        assertThat(ref.getUrl()).isNull();
        assertThat(ref.getChunkId()).isNull();
        assertThat(ref.getSimilarityScore()).isNull();
        assertThat(ref.getRerankScore()).isNull();
    }

    @Test
    void shouldMapMultipleReferences() {
        TextSegment seg1 = TextSegment.from("片段1", new Metadata(Map.of(DOC_ID, 1, FILE_NAME, "doc1.pdf",
                CHUNK_ID, "c1", URL, "/url1")));
        TextSegment seg2 = TextSegment.from("片段2", new Metadata(Map.of(DOC_ID, 2, FILE_NAME, "doc2.pdf",
                CHUNK_ID, "c2", URL, "/url2")));
        Content c1 = Content.from(seg1, Map.of(ContentMetadata.SCORE, 0.9));
        Content c2 = Content.from(seg2, Map.of(ContentMetadata.SCORE, 0.7));

        List<RagReference> refs = ReferenceUtil.getRagReferences(
                List.of(c1, c2), RetrievalSource.KEYWORD);

        assertThat(refs).hasSize(2);
        assertThat(refs.get(0).getDocumentId()).isEqualTo("1");
        assertThat(refs.get(1).getDocumentId()).isEqualTo("2");
    }

    @Test
    void shouldSetCorrectRetrievalSource() {
        TextSegment segment = TextSegment.from("text", new Metadata(Map.of(DOC_ID, 1)));
        Content content = Content.from(segment, Map.of());

        assertThat(ReferenceUtil.getRagReference(content, RetrievalSource.VECTOR).getRetrievalSource())
                .isEqualTo(RetrievalSource.VECTOR);
        assertThat(ReferenceUtil.getRagReference(content, RetrievalSource.KEYWORD).getRetrievalSource())
                .isEqualTo(RetrievalSource.KEYWORD);
        assertThat(ReferenceUtil.getRagReference(content, RetrievalSource.RERANK).getRetrievalSource())
                .isEqualTo(RetrievalSource.RERANK);
    }

    @Test
    void shouldReturnNullScoreWhenNotPresentInMetadata() {
        TextSegment segment = TextSegment.from("内容", new Metadata(Map.of(DOC_ID, 42)));
        Content content = Content.from(segment, Map.of()); // no SCORE or RERANKED_SCORE

        RagReference ref = ReferenceUtil.getRagReference(content, RetrievalSource.HYBRID);

        assertThat(ref.getSimilarityScore()).isNull();
        assertThat(ref.getRerankScore()).isNull();
    }
}
