package cn.cloudcat.user.portal.service.impl;

import cn.cloudcat.user.common.constant.DocumentStatus;
import cn.cloudcat.user.common.constant.FileType;
import cn.cloudcat.user.common.constant.KnowledgeBaseType;
import cn.cloudcat.user.common.constant.SegmentStatus;
import cn.cloudcat.user.common.entity.DocumentSplitParam;
import cn.cloudcat.user.portal.entity.DocumentUploadParam;
import cn.cloudcat.user.portal.entity.KnowledgeDocument;
import cn.cloudcat.user.portal.entity.KnowledgeSegment;
import cn.cloudcat.user.portal.event.DocumentChunkedEvent;
import cn.cloudcat.user.portal.event.DocumentConvertedEvent;
import cn.cloudcat.user.portal.service.*;
import cn.cloudcat.user.common.util.FileTypeUtil;
import cn.cloudcat.common.redis.lock.DistributeLock;
import cn.cloudcat.user.common.constant.MetadataKeyConstant;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.common.rag.splitter.DocumentSplitterFactory;
import cn.cloudcat.user.portal.rag.ExcelSplitter;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Stopwatch;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档处理服务实现类
 * 负责文档的业务流程处理：上传、转换、分段、向量化
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Service
public class DocumentProcessServiceImpl implements DocumentProcessService {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private FileProcessServiceFactory fileProcessServiceFactory;

    @Autowired
    private KnowledgeSegmentService knowledgeSegmentService;

    @Autowired(required = false)
    private ElasticsearchEmbeddingStore elasticsearchEmbeddingStore;

    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    @DistributeLock(scene = "document-upload", keyExpression = "#documentUploadParam.uploadUser", waitTime = 0)
    public KnowledgeDocument upload(DocumentUploadParam documentUploadParam) throws IOException {
        try {
            log.info("start to upload ....");
            String fileName = documentUploadParam.file().getOriginalFilename();
            // 用minio上传
            String fileUrl = fileStorageService.uploadFile(documentUploadParam.file(), fileName);

            // 构建文档记录
            KnowledgeDocument document = new KnowledgeDocument();
            document.setDocTitle(documentUploadParam.title());
            document.setUploadUser(documentUploadParam.uploadUser());
            document.setDocUrl(fileUrl);
            document.setStatus(DocumentStatus.UPLOADED);
            document.setAccessibleBy(documentUploadParam.accessibleBy());
            document.setDescription(documentUploadParam.description());
            document.setKnowledgeBaseType(KnowledgeBaseType.valueOf(documentUploadParam.knowledgeBaseType()));
            document.setTableName(documentUploadParam.tableName());

            // 保存到数据库
            boolean result = knowledgeDocumentService.save(document);
            Assert.isTrue(result, "文件上传失败");

            FileProcessService fileProcessService = fileProcessServiceFactory.get(FileTypeUtil.getFileType(fileName, documentUploadParam.file()), document.getKnowledgeBaseType());
            if (fileProcessService != null) {
                fileProcessService.processDocument(document, documentUploadParam.file().getInputStream());
            }

            if (document.getKnowledgeBaseType() == KnowledgeBaseType.DOCUMENT_SEARCH) {
                document.setStatus(DocumentStatus.CONVERTED);
                document.setConvertedDocUrl(fileUrl);
                result = knowledgeDocumentService.updateById(document);
                Assert.isTrue(result, "文件状态更新失败");
            } else {
                document.setStatus(DocumentStatus.STORED);
                document.setConvertedDocUrl(fileUrl);
                result = knowledgeDocumentService.updateById(document);
                Assert.isTrue(result, "文件状态更新失败");
            }
            return document;
        } catch (Exception e) {
            throw new IOException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    @DistributeLock(scene = "document-split", keyExpression = "#document.docId", waitTime = 30000)
    public int split(KnowledgeDocument document, DocumentSplitParam documentSplitParam) {
        // 加锁后重新从DB读取最新状态，防止并发竞态导致重复分段
        document = knowledgeDocumentService.getById(document.getDocId());

        // 1. 查询文档
        Assert.notNull(document, "文档不存在");
        Assert.notNull(document.getConvertedDocUrl(), "文档未转换完成");

        if (document.getStatus() == DocumentStatus.CHUNKED) {
            // 返回已切分的分段数量
            Long chunkedCount = knowledgeSegmentService.count(new QueryWrapper<KnowledgeSegment>().eq("document_id", document.getDocId()).eq("skipEmbedding", 0));
            return chunkedCount.intValue();
        }

        if (document.getStatus() != DocumentStatus.CONVERTED
                && document.getStatus() != DocumentStatus.SPLITTING) {
            throw new RuntimeException("文档状态不为CONVERTED或SPLITTING，当前状态: " + document.getStatus());
        }

        // 2. 从MinIO下载文件内容
        String convertedDocUrl = document.getConvertedDocUrl();
        String objectName = extractObjectNameFromUrl(convertedDocUrl);
        Assert.notNull(objectName, "无法解析文档URL");

        List<KnowledgeSegment> knowledgeSegments = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        try (InputStream inputStream = fileStorageService.downloadFile(objectName)) {
            //EXCEL单独处理，因为他不是Document类型
            if (FileType.EXCEL == FileTypeUtil.getFileType(document.getConvertedDocUrl()) || FileType.CSV == FileTypeUtil.getFileType(document.getConvertedDocUrl())) {
                ExcelSplitter splitter = new ExcelSplitter(documentSplitParam.chunkSize(), false);
                segments = splitter.split(inputStream.readAllBytes());
            } else {
                DocumentSplitter splitter = DocumentSplitterFactory.getInstance(documentSplitParam);
                Document doc = Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                segments = splitter.split(doc);
            }
        } catch (Exception e) {
            throw new RuntimeException("下载文档失败: " + e.getMessage(), e);
        }

        // 4. 转换为 KnowledgeSegment 并保存
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            KnowledgeSegment knowledgeSegment = new KnowledgeSegment();
            knowledgeSegment.setText(segment.text());
            String chunkId = segment.metadata().getString(MetadataKeyConstant.CHUNK_ID);
            knowledgeSegment.setChunkId(chunkId != null ? chunkId : SnowflakeIdGenerator.getInstance().nextIdStr());
            Metadata metadata = segment.metadata();
            metadata.put(MetadataKeyConstant.DOC_ID, document.getDocId());
            metadata.put(MetadataKeyConstant.FILE_NAME, document.getDocTitle());
            metadata.put(MetadataKeyConstant.URL, document.getDocUrl());

            //todo metadata统一处理(权限相关、多版本相关）
            knowledgeSegment.setMetadata(JSON.toJSONString(metadata.toMap()));
            knowledgeSegment.setDocumentId(document.getDocId());
            knowledgeSegment.setChunkOrder(i);

            // 检查是否需要跳过嵌入
            Integer skipEmbedding = metadata.getInteger(MetadataKeyConstant.SKIP_EMBEDDING);
            if (skipEmbedding != null && skipEmbedding == 1) {
                knowledgeSegment.setSkipEmbedding(1);
                knowledgeSegment.setStatus(SegmentStatus.STORED);
            } else {
                knowledgeSegment.setSkipEmbedding(0);
                knowledgeSegment.setStatus(SegmentStatus.STORED);
            }

            knowledgeSegments.add(knowledgeSegment);
        }

        // 5. 批量保存片段
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean saveResult = knowledgeSegmentService.saveBatch(knowledgeSegments);
        Assert.isTrue(saveResult, "保存知识片段失败");
        log.info("保存知识片段耗时: {}", stopwatch.elapsed().toMillis());

        int segmentCount = knowledgeSegments.size();

        // 6. 更新文档状态为 CHUNKED
        document.setStatus(DocumentStatus.CHUNKED);
        boolean updateResult = knowledgeDocumentService.updateById(document);
        Assert.isTrue(updateResult, "更新文档状态失败");

        // 发送文档已分段事件
        publishChunkedEvent(document, segmentCount);

        return segmentCount;
    }

    @Override
    @DistributeLock(scene = "document-split", keyExpression = "#document.docId", waitTime = 30000)
    public boolean embedAndStore(KnowledgeDocument document) {
        // 加锁后重新从DB读取最新状态，防止并发竞态
        document = knowledgeDocumentService.getById(document.getDocId());

        if (document == null) {
            return false;
        }

        if (document.getStatus() == DocumentStatus.VECTOR_STORED) {
            return true;
        }

        if (document.getStatus() != DocumentStatus.CHUNKED
                && document.getStatus() != DocumentStatus.EMBEDDING) {
            return false;
        }

        if (elasticsearchEmbeddingStore == null) {
            log.warn("Elasticsearch 未启用，跳过文档向量入库 docId={}", document.getDocId());
            return false;
        }

        // 分页扫描全部document_id为docId且status为STORED的文档片段
        LambdaQueryWrapper<KnowledgeSegment> queryWrapper = Wrappers.<KnowledgeSegment>lambdaQuery()
                .eq(KnowledgeSegment::getDocumentId, document.getDocId())
                .eq(KnowledgeSegment::getStatus, SegmentStatus.STORED)
                .isNull(KnowledgeSegment::getEmbeddingId)
                .eq(KnowledgeSegment::getSkipEmbedding, 0);

        Page<KnowledgeSegment> page = knowledgeSegmentService.page(new Page<>(1, 100), queryWrapper);

        while (page.getCurrent() == 1 || page.hasNext()) {
            List<KnowledgeSegment> textSegmentsToEmbed = page.getRecords();
            List<TextSegment> textSegments = textSegmentsToEmbed.stream().map(segment -> TextSegment.from(segment.getText(), Metadata.from(segment.getMetadataMap()))).toList();
            // 获取嵌入向量
            Response<List<Embedding>> embeddingResponse = openAiEmbeddingModel.embedAll(textSegments);

            // 存储嵌入向量
            List<String> embeddingIds = elasticsearchEmbeddingStore.addAll(embeddingResponse.content(), textSegments);

            //todo 事务处理

            // 更新文档片段状态
            for (int i = 0; i < textSegmentsToEmbed.size(); i++) {
                String embeddingId = embeddingIds.get(i);
                KnowledgeSegment knowledgeSegment = textSegmentsToEmbed.get(i);
                knowledgeSegment.setEmbeddingId(embeddingId);
                knowledgeSegment.setStatus(SegmentStatus.VECTOR_STORED);
                knowledgeSegmentService.updateById(knowledgeSegment);
            }

            // 继续扫描下一页
            page = knowledgeSegmentService.page(new Page<>(page.getCurrent() + 1, 100), queryWrapper);
        }

        //double check
        long segmentCount = knowledgeSegmentService.count(queryWrapper);
        if (segmentCount == 0) {
            // 更新文档状态
            document.setStatus(DocumentStatus.VECTOR_STORED);
            return knowledgeDocumentService.updateById(document);
        }

        log.warn("向量存储失败，存在部分分段没有存储成功，未成功的数量： " + segmentCount);
        return false;
    }

    @Override
    @DistributeLock(scene = "document-split", keyExpression = "#docId", waitTime = 0)
    public void deleteDocument(Long docId) {
        KnowledgeDocument document = knowledgeDocumentService.getById(docId);
        if (document == null) {
            log.warn("deleteDocument: document not found, docId={}", docId);
            return;
        }

        // 检查文档是否正在被处理，避免并发删除造成数据不一致
        if (document.getStatus() == DocumentStatus.SPLITTING
                || document.getStatus() == DocumentStatus.EMBEDDING) {
            throw new IllegalStateException(
                    "文档正在处理中，无法删除。当前状态: " + document.getStatus());
        }

        // 1. 收集 ES 向量 ID，然后先删 DB 再删 ES
        // 先删 DB：即使后续 ES 清理失败，DB 已清理，ES 残留可由补偿任务处理
        List<KnowledgeSegment> segments = knowledgeSegmentService.list(
                Wrappers.<KnowledgeSegment>lambdaQuery()
                        .eq(KnowledgeSegment::getDocumentId, docId));
        List<String> embeddingIds = segments.stream()
                .map(KnowledgeSegment::getEmbeddingId)
                .filter(id -> id != null && !id.isEmpty())
                .toList();

        try {
            knowledgeSegmentService.remove(
                    Wrappers.<KnowledgeSegment>lambdaQuery()
                            .eq(KnowledgeSegment::getDocumentId, docId));
            log.info("deleteDocument: removed {} segments, docId={}", segments.size(), docId);
        } catch (Exception e) {
            log.warn("deleteDocument: failed to clean segments, docId={}", docId, e);
        }

        try {
            if (elasticsearchEmbeddingStore != null && !embeddingIds.isEmpty()) {
                elasticsearchEmbeddingStore.removeAll(embeddingIds);
                log.info("deleteDocument: removed {} ES vectors, docId={}", embeddingIds.size(), docId);
            }
        } catch (Exception e) {
            log.warn("deleteDocument: failed to clean ES vectors, docId={}", docId, e);
        }

        // 2. 删除 MinIO 文件
        try {
            for (String url : new String[]{document.getDocUrl(), document.getConvertedDocUrl()}) {
                if (url != null && !url.isEmpty()) {
                    String objectName = extractObjectNameFromUrl(url);
                    if (objectName != null) {
                        fileStorageService.deleteFile(objectName);
                    }
                }
            }
            log.info("deleteDocument: cleaned MinIO files, docId={}", docId);
        } catch (Exception e) {
            log.warn("deleteDocument: failed to clean MinIO files, docId={}", docId, e);
        }

        // 3. 删除文档记录
        knowledgeDocumentService.removeById(docId);
        log.info("deleteDocument: completed, docId={}", docId);
    }

    // ==================== 事件发布方法 ====================

    /**
     * 发送文档已转换事件
     *
     * @Deprecated 不再使用事件驱动，靠用户在前端手动触发分段，因为需要用户选择分段方式。
     */
    @Deprecated
    private void publishConvertedEvent(KnowledgeDocument document) {
        log.info("发送文档CONVERTED事件，documentId: {}", document.getDocId());
        DocumentConvertedEvent event = new DocumentConvertedEvent(this, document.getDocId(), document);
        eventPublisher.publishEvent(event);
    }

    /**
     * 发送文档已分段事件
     */
    private void publishChunkedEvent(KnowledgeDocument document, int segmentCount) {
        log.info("发送文档CHUNKED事件，documentId: {}, segmentCount: {}", document.getDocId(), segmentCount);
        DocumentChunkedEvent event = new DocumentChunkedEvent(this, document.getDocId(), document, segmentCount);
        eventPublisher.publishEvent(event);
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过后缀名判断是否为 PDF 文件
     */
    private boolean isPdfFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        return fileName.toLowerCase().endsWith(".pdf");
    }

    /**
     * 通过 Apache Tika 检测文件内容类型判断是否为 PDF 文件
     */
    private boolean isPdfContent(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String mimeType = new Tika().detect(is);
            return "application/pdf".equals(mimeType);
        } catch (IOException e) {
            log.error("文件类型检测失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从MinIO URL中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        // URL格式: http://endpoint/bucketName/objectName
        int lastSlashIndex = url.lastIndexOf(bucketName) + bucketName.length();
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1) {
            return null;
        }
        return url.substring(lastSlashIndex + 1);
    }
}
