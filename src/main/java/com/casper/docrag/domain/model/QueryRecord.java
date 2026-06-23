package com.casper.docrag.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 查詢歷史一筆紀錄（SPEC §3.3 query_history）。
 * 含延遲分解（embedding / retrieval / llm）與 token 成本，支援 M3 可觀測性與 M4 成本控制。
 */
public record QueryRecord(
        UUID id,
        String question,
        String answer,
        UUID documentScope,
        List<UUID> retrievedChunkIds,
        Boolean grounded,
        Integer tokenCost,
        Integer latencyMs,
        Integer embeddingMs,
        Integer retrievalMs,
        Integer llmMs,
        Instant createdAt
) {
}
