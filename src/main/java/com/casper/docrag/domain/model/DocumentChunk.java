package com.casper.docrag.domain.model;

import java.util.UUID;

/**
 * 已向量化、待寫入向量庫的文件片段（SPEC §3.1 VectorStore.upsert 的輸入）。
 *
 * <p>注意：record 對 float[] 採參考相等，本型別僅作為資料載體傳遞，不用於集合比較。
 */
public record DocumentChunk(
        UUID id,
        UUID documentId,
        int chunkIndex,
        String content,
        float[] embedding
) {
}
