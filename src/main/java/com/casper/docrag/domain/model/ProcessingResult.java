package com.casper.docrag.domain.model;

import java.util.UUID;

/** 文件處理管線的結果（SPEC §3.1 DocumentProcessor.process 的回傳）。 */
public record ProcessingResult(
        UUID documentId,
        DocumentStatus status,
        int chunkCount,
        String error
) {
    public static ProcessingResult ready(UUID documentId, int chunkCount) {
        return new ProcessingResult(documentId, DocumentStatus.READY, chunkCount, null);
    }

    public static ProcessingResult failed(UUID documentId, String error) {
        return new ProcessingResult(documentId, DocumentStatus.FAILED, 0, error);
    }

    public boolean isSuccess() {
        return status == DocumentStatus.READY;
    }
}
