package com.casper.docrag.web;

import com.casper.docrag.document.DocumentService;
import com.casper.docrag.domain.model.Document;
import com.casper.docrag.web.dto.DocumentView;
import com.casper.docrag.web.dto.UploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** 文件 REST 端點（SPEC §3.2）。 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 上傳文件；非同步處理，回 202 + {documentId, status}。 */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Document doc = documentService.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new UploadResponse(doc.id(), doc.status().name()));
    }

    /** 列出文件與處理狀態。 */
    @GetMapping
    public List<DocumentView> list() {
        return documentService.list().stream().map(DocumentView::from).toList();
    }

    /** 刪除文件（chunks 由 FK 串接刪除），回 204。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
