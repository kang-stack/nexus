package cn.cloudcat.user.portal.controller;

import cn.cloudcat.user.common.constant.DocumentStatus;
import cn.cloudcat.user.common.entity.DocumentSplitParam;
import cn.cloudcat.user.portal.entity.DocumentUploadParam;
import cn.cloudcat.user.portal.entity.KnowledgeDocument;
import cn.cloudcat.user.portal.service.DocumentProcessService;
import cn.cloudcat.user.portal.service.KnowledgeDocumentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/cs/v1/admin/document")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(maxAge = 3600)
public class PortalDocumentController {

    private final DocumentProcessService documentProcessService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    @Qualifier("eventListenerExecutor")
    private Executor eventListenerExecutor;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<KnowledgeDocument> upload(
            @ModelAttribute DocumentUploadParam param) throws IOException {
        log.info("文档上传请求: title={}, knowledgeBaseType={}", param.title(), param.knowledgeBaseType());
        KnowledgeDocument doc = documentProcessService.upload(param);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/split/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> split(
            @PathVariable Long documentId,
            @RequestBody DocumentSplitParam splitParam) {
        log.info("文档分段请求: documentId={}, splitType={}", documentId, splitParam.splitType());
        KnowledgeDocument doc = knowledgeDocumentService.getById(documentId);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (doc.getStatus() != DocumentStatus.CONVERTED
                && doc.getStatus() != DocumentStatus.SPLITTING) {
            return ResponseEntity.badRequest()
                    .body("文档状态不正确，当前状态: " + doc.getStatus() + "，需要: CONVERTED 或 SPLITTING");
        }
        doc.setStatus(DocumentStatus.SPLITTING);
        knowledgeDocumentService.updateById(doc);

        executeAsyncWithStatusRollback(
                () -> documentProcessService.split(doc, splitParam),
                documentId,
                DocumentStatus.SPLITTING,
                DocumentStatus.CONVERTED,
                "分段失败");

        return ResponseEntity.ok("分段任务已启动");
    }

    @PostMapping("/embedding")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> embedding(@RequestParam Long documentId) {
        KnowledgeDocument doc = knowledgeDocumentService.getById(documentId);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (doc.getStatus() != DocumentStatus.CHUNKED
                && doc.getStatus() != DocumentStatus.EMBEDDING) {
            return ResponseEntity.badRequest()
                    .body("文档状态不正确，当前状态: " + doc.getStatus() + "，需要: CHUNKED 或 EMBEDDING");
        }
        doc.setStatus(DocumentStatus.EMBEDDING);
        knowledgeDocumentService.updateById(doc);

        executeAsyncWithStatusRollback(
                () -> documentProcessService.embedAndStore(doc),
                documentId,
                DocumentStatus.EMBEDDING,
                DocumentStatus.CHUNKED,
                "向量化失败");

        return ResponseEntity.ok("向量化任务已启动");
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Page<KnowledgeDocument> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return knowledgeDocumentService.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .orderByDesc(KnowledgeDocument::getCreatedAt)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KnowledgeDocument getById(@PathVariable Long id) {
        return knowledgeDocumentService.getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        documentProcessService.deleteDocument(id);
        return ResponseEntity.ok("删除成功");
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> disable(@PathVariable Long id) {
        KnowledgeDocument doc = knowledgeDocumentService.getById(id);
        if (doc != null) {
            doc.setStatus(DocumentStatus.DISABLED);
            knowledgeDocumentService.updateById(doc);
        }
        return ResponseEntity.ok("停用成功");
    }

    @PostMapping("/models/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public ResponseEntity<Map<String, String>> refreshModels() {
        return ResponseEntity.ok(Map.of("message", "模型配置刷新成功"));
    }

    /**
     * 异步执行文档处理任务，失败时自动回滚文档状态。
     *
     * @param task            要执行的任务
     * @param docId           文档ID
     * @param processingStatus 任务开始前的中间状态
     * @param rollbackStatus  失败后回滚到的状态
     * @param errorMsg        错误日志描述
     */
    private void executeAsyncWithStatusRollback(
            Runnable task,
            Long docId,
            DocumentStatus processingStatus,
            DocumentStatus rollbackStatus,
            String errorMsg) {
        eventListenerExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("{}: documentId={}", errorMsg, docId, e);
                KnowledgeDocument latest = knowledgeDocumentService.getById(docId);
                if (latest != null && latest.getStatus() == processingStatus) {
                    latest.setStatus(rollbackStatus);
                    knowledgeDocumentService.updateById(latest);
                }
            }
        });
    }
}
