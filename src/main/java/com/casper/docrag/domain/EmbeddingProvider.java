package com.casper.docrag.domain;

import java.util.List;

/**
 * Embedding 抽象 — 可替換 OpenAI / Ollama（SPEC §3.1）。
 *
 * <p>實作以介面為準，業務邏輯不得直接耦合具體 provider（SPEC §7 介面優先）。
 */
public interface EmbeddingProvider {

    /** 將單一文字向量化。 */
    float[] embed(String text);

    /** 批次向量化，降低呼叫次數與成本（SPEC §7 成本意識）。 */
    List<float[]> embedBatch(List<String> texts);

    /** 此 provider 產生的向量維度，必須與資料庫 schema 對齊。 */
    int dimensions();
}
