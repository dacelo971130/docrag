package com.casper.docrag.domain.model;

import java.util.UUID;

/**
 * 向量檢索命中的片段（SPEC §3.1）。
 * 必含 {@code similarity} 分數，供檢索門檻判斷（防線 1）與後處理引用驗證（防線 4）使用。
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        double similarity
) {
}
