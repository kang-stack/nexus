package cn.cloudcat.user.portal.service;

import cn.cloudcat.user.portal.entity.KnowledgeDocument;
import cn.cloudcat.user.portal.entity.KnowledgeSegment;
import cn.cloudcat.user.portal.event.DocumentChunkedEvent;
import cn.cloudcat.user.common.constant.DocumentStatus;
import cn.cloudcat.user.common.constant.KnowledgeBaseType;
import cn.cloudcat.user.common.constant.MetadataKeyConstant;
import cn.cloudcat.user.common.constant.SegmentStatus;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.document.Metadata;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 常见问题 Excel → acc_user_knowledge_document + acc_user_knowledge_segment，触发 ES 向量嵌入。
 * 列：问题 / 答案（或 question / answer）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FaqExcelImportService {

    private final KnowledgeDocumentService documentService;
    private final KnowledgeSegmentService segmentService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Map<String, Object> importFile(MultipartFile file, String uploadUser) throws IOException {
        List<FaqRow> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), FaqRow.class, new ReadListener<FaqRow>() {
            @Override
            public void invoke(FaqRow data, AnalysisContext context) {
                if (data.hasContent()) {
                    rows.add(data);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();

        if (rows.isEmpty()) {
            return Map.of("success", false, "message", "未读取到有效 FAQ 行");
        }

        String title = "FAQ导入-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setDocTitle(title);
        doc.setUploadUser(uploadUser);
        doc.setStatus(DocumentStatus.CHUNKED);
        doc.setKnowledgeBaseType(KnowledgeBaseType.DOCUMENT_SEARCH);
        doc.setDescription("常见问题 Excel 批量导入");
        documentService.save(doc);

        List<KnowledgeSegment> segments = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            FaqRow row = rows.get(i);
            String text = "问：" + row.questionText() + "\n答：" + row.answerText();
            KnowledgeSegment segment = new KnowledgeSegment();
            segment.setText(text);
            segment.setChunkId(SnowflakeIdGenerator.getInstance().nextIdStr());
            segment.setDocumentId(doc.getDocId());
            segment.setChunkOrder(i);
            segment.setSkipEmbedding(0);
            segment.setStatus(SegmentStatus.STORED);

            Metadata metadata = new Metadata();
            metadata.put(MetadataKeyConstant.DOC_ID, doc.getDocId());
            metadata.put(MetadataKeyConstant.FILE_NAME, title);
            metadata.put(MetadataKeyConstant.CHUNK_ID, segment.getChunkId());
            segment.setMetadata(JSON.toJSONString(metadata.toMap()));
            segments.add(segment);
        }

        segmentService.saveBatch(segments);
        eventPublisher.publishEvent(new DocumentChunkedEvent(this, doc.getDocId(), doc, segments.size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("documentId", doc.getDocId());
        result.put("segmentCount", segments.size());
        result.put("title", title);
        log.info("FAQ import done docId={} segments={}", doc.getDocId(), segments.size());
        return result;
    }

    public byte[] exportTemplate() throws IOException {
        FaqRow sample = new FaqRow();
        sample.setQuestionCn("如何查询流量剩余？");
        sample.setAnswerCn("打开设备管理页面，在套餐信息中可查看剩余流量。");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, FaqRow.class).sheet("FAQ").doWrite(List.of(sample));
        return out.toByteArray();
    }

    @Data
    public static class FaqRow {
        @ExcelProperty(value = "问题")
        private String questionCn;
        @ExcelProperty(value = "答案")
        private String answerCn;
        @ExcelProperty(value = "question")
        private String questionEn;
        @ExcelProperty(value = "answer")
        private String answerEn;

        boolean hasContent() {
            return questionText() != null && !questionText().isBlank()
                    && answerText() != null && !answerText().isBlank();
        }

        String questionText() {
            if (questionCn != null && !questionCn.isBlank()) {
                return questionCn.trim();
            }
            return questionEn != null ? questionEn.trim() : null;
        }

        String answerText() {
            if (answerCn != null && !answerCn.isBlank()) {
                return answerCn.trim();
            }
            return answerEn != null ? answerEn.trim() : null;
        }
    }
}
