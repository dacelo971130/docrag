package com.casper.docrag.domain;

import com.casper.docrag.domain.model.DocumentChunk;
import com.casper.docrag.domain.model.RetrievedChunk;

import java.util.List;

/**
 * 向量儲存抽象 — 初期 pgvector，未來可換 Qdrant（SPEC §3.1，ADR-001）。
 */
public interface VectorStore {

    /** 寫入（或覆寫）一批已向量化的片段。 */
    void upsert(List<DocumentChunk> chunks);

    /**
     * 以 cosine 相似度檢索最相關的片段。
     *
     * @param queryVector   問題向量
     * @param topK          取回前 K 筆
     * @param documentScope 限定檢索範圍的 documentId（null = 全部）— 防線 2（scope 過濾）
     * @param minSimilarity 低於此相似度的片段一律丟棄 — 防線 1（檢索門檻）
     * @return 依相似度由高到低排序、且 similarity ≥ minSimilarity 的片段
     */
    List<RetrievedChunk> search(float[] queryVector, int topK, String documentScope, double minSimilarity);
}
