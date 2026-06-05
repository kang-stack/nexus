package cn.cloudcat.user.common.rag.splitter;

import cn.cloudcat.user.common.constant.SplitType;
import cn.cloudcat.user.common.entity.DocumentSplitParam;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author Kang
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
public class DocumentSplitterFactory {
    /**
     * 根据切分参数返回对应的文档分割器实例。
     */
    public static DocumentSplitter getInstance(DocumentSplitParam documentSplitParam) {
        if (SplitType.TITLE.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        if (SplitType.LENGTH.name().equals(documentSplitParam.splitType())) {
            return new DocumentByWordSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        if (SplitType.SEPARATOR.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.separator(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        if (SplitType.REGEX.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.regex(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        if (SplitType.SMART.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), (int) (documentSplitParam.chunkSize() * 0.1));
        }

        if (SplitType.BROTHER.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderBrotherTextSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 未知类型回退到默认 TITLE 策略，避免 NPE
        log.warn("Unknown split type: {}, falling back to TITLE strategy", documentSplitParam.splitType());
        return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
    }
}
