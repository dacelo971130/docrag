package com.casper.docrag.domain.model;

import java.time.Instant;
import java.util.UUID;

/** 文件主表的領域檢視（SPEC §3.3 documents）。 */
public record Document(
        UUID id,
        String name,
        DocumentStatus status,
        int chunkCount,
        String error,
        Instant createdAt
) {
}
