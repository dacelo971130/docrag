package com.casper.docrag.web.dto;

import com.casper.docrag.domain.model.Document;

import java.time.Instant;
import java.util.UUID;

/** GET /api/documents 列表項（SPEC §3.2，另附 error/createdAt 便於前端顯示）。 */
public record DocumentView(
        UUID documentId,
        String name,
        String status,
        int chunkCount,
        String error,
        Instant createdAt
) {
    public static DocumentView from(Document d) {
        return new DocumentView(d.id(), d.name(), d.status().name(), d.chunkCount(), d.error(), d.createdAt());
    }
}
