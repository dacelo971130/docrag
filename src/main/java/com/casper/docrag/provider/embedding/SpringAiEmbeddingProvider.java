package com.casper.docrag.provider.embedding;

import com.casper.docrag.domain.EmbeddingProvider;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

/**
 * {@link EmbeddingProvider} 的 Spring AI adapter（ADR-007）。包裝任一 Spring AI
 * {@link EmbeddingModel}（OpenAI / Ollama），領域層只認得 EmbeddingProvider 介面。
 *
 * <p>{@link #dimensions()} 回傳設定值而非詢問模型，避免啟動期外呼，並確保與資料庫 schema 一致。
 */
public class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel model;
    private final int dimensions;

    public SpringAiEmbeddingProvider(EmbeddingModel model, int dimensions) {
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return model.embed(texts);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
