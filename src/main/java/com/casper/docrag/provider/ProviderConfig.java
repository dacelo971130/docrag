package com.casper.docrag.provider;

import com.casper.docrag.config.AppProperties;
import com.casper.docrag.domain.EmbeddingProvider;
import com.casper.docrag.domain.LlmProvider;
import com.casper.docrag.provider.embedding.CachingEmbeddingProvider;
import com.casper.docrag.provider.embedding.SpringAiEmbeddingProvider;
import com.casper.docrag.provider.llm.SpringAiLlmProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 依設定選定要包裝的 Spring AI 模型（DIP，ADR-005）。
 *
 * <p>注入 {@code Map<beanName, model>}（Spring 會放入所有同型別 bean），再以
 * {@code app.embedding.bean} / {@code app.llm.bean} 指定名稱挑選。切換 provider 僅需改設定。
 */
@Configuration
public class ProviderConfig {

    @Bean
    public EmbeddingProvider embeddingProvider(Map<String, EmbeddingModel> embeddingModels,
                                               AppProperties props,
                                               CacheManager cacheManager) {
        String beanName = props.embedding().bean();
        EmbeddingModel model = embeddingModels.get(beanName);
        if (model == null) {
            throw new IllegalStateException(
                    "找不到 embedding model bean '" + beanName + "'。可用：" + embeddingModels.keySet()
                            + "。請檢查 app.embedding.bean 與對應 starter/API key 設定。");
        }
        EmbeddingProvider base = new SpringAiEmbeddingProvider(model, props.embedding().dimension());
        Cache cache = cacheManager.getCache("embeddings");
        return cache == null ? base : new CachingEmbeddingProvider(base, cache);
    }

    @Bean
    public LlmProvider llmProvider(Map<String, ChatModel> chatModels, AppProperties props) {
        String beanName = props.llm().bean();
        ChatModel model = chatModels.get(beanName);
        if (model == null) {
            throw new IllegalStateException(
                    "找不到 chat model bean '" + beanName + "'。可用：" + chatModels.keySet()
                            + "。請檢查 app.llm.bean 與對應 starter/API key 設定。");
        }
        return new SpringAiLlmProvider(model, props.llm().maxRetries(), props.llm().retryBackoffMs());
    }
}
