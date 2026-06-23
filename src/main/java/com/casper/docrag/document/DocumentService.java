package com.casper.docrag.document;

import com.casper.docrag.domain.DocumentProcessor;
import com.casper.docrag.domain.model.Document;
import com.casper.docrag.domain.model.ProcessingResult;
import com.casper.docrag.domain.model.UploadedDocument;
import com.casper.docrag.error.DocumentNotFoundException;
import com.casper.docrag.error.DocumentProcessingException;
import com.casper.docrag.persistence.DocumentRepository;
import com.casper.docrag.provider.vectorstore.PgVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 文件生命週期編排：上傳（建立 metadata、非同步觸發處理）、列表、刪除。
 *
 * <p>處理採非同步（PENDING→PROCESSING→READY/FAILED），上傳請求立即返回，避免長時間
 * embedding 阻塞 HTTP 執行緒；狀態由 GET /api/documents 觀察。錯誤一律記錄為 FAILED（§7）。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentProcessor processor;
    private final PgVectorStore vectorStore;
    private final Executor executor;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentProcessor processor,
                           PgVectorStore vectorStore,
                           @Qualifier("documentProcessingExecutor") Executor executor) {
        this.documentRepository = documentRepository;
        this.processor = processor;
        this.vectorStore = vectorStore;
        this.executor = executor;
    }

    public Document upload(String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new DocumentProcessingException("檔案內容為空");
        }
        String name = (filename == null || filename.isBlank()) ? "untitled" : filename;
        UUID id = UUID.randomUUID();

        documentRepository.insertPending(id, name);
        UploadedDocument doc = new UploadedDocument(id, name, contentType, content);
        executor.execute(() -> runProcessing(doc));

        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("插入後找不到文件 " + id));
    }

    /** 背景處理；任何例外都轉為 FAILED 並記錄原因。 */
    void runProcessing(UploadedDocument doc) {
        try {
            documentRepository.markProcessing(doc.id());
            vectorStore.deleteByDocumentId(doc.id()); // 冪等：重新處理前清空舊片段
            ProcessingResult result = processor.process(doc);
            if (result.isSuccess()) {
                documentRepository.markReady(doc.id(), result.chunkCount());
                log.info("文件處理完成 id={} chunks={}", doc.id(), result.chunkCount());
            } else {
                documentRepository.markFailed(doc.id(), result.error());
                log.warn("文件處理失敗 id={} error={}", doc.id(), result.error());
            }
        } catch (Exception e) {
            log.error("文件處理發生例外 id={}", doc.id(), e);
            documentRepository.markFailed(doc.id(), e.getMessage());
        }
    }

    public List<Document> list() {
        return documentRepository.findAll();
    }

    public void delete(UUID id) {
        if (!documentRepository.deleteById(id)) {
            throw new DocumentNotFoundException(id);
        }
    }
}
