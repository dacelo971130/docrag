package com.casper.docrag.provider.embedding;

import com.casper.docrag.domain.EmbeddingProvider;
import com.casper.docrag.support.Hashing;
import org.springframework.cache.Cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Embedding 快取裝飾器（M4：成本控制）。以文字 SHA-256 為鍵記憶向量，重複文字（含相同問題）
 * 免重打 API。批次查詢只對 cache miss 的項目呼叫底層 provider。
 *
 * <p>使用 Spring Cache 抽象：預設 Caffeine（單機），'redis' profile 改 Redis（跨實例共享）。
 */
public class CachingEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProvider delegate;
    private final Cache cache;

    public CachingEmbeddingProvider(EmbeddingProvider delegate, Cache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public float[] embed(String text) {
        String key = Hashing.sha256Hex(text);
        float[] cached = cache.get(key, float[].class);
        if (cached != null) {
            return cached;
        }
        float[] vector = delegate.embed(text);
        cache.put(key, vector);
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        float[][] results = new float[texts.size()][];
        List<String> misses = new ArrayList<>();
        List<Integer> missIndexes = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            float[] cached = cache.get(Hashing.sha256Hex(texts.get(i)), float[].class);
            if (cached != null) {
                results[i] = cached;
            } else {
                misses.add(texts.get(i));
                missIndexes.add(i);
            }
        }

        if (!misses.isEmpty()) {
            List<float[]> embedded = delegate.embedBatch(misses);
            for (int j = 0; j < embedded.size(); j++) {
                int idx = missIndexes.get(j);
                results[idx] = embedded.get(j);
                cache.put(Hashing.sha256Hex(texts.get(idx)), embedded.get(j));
            }
        }
        return Arrays.asList(results);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }
}
