package com.casper.docrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 應用程式可調設定（prefix {@code app}）。集中管理 provider 選擇、分塊、檢索門檻與限流，
 * 使「切換 OpenAI ↔ Ollama 僅需改設定」成立（SPEC §6）。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Embedding embedding,
        Llm llm,
        Chunking chunking,
        Query query,
        RateLimit ratelimit
) {

    public record Embedding(
            /** Spring AI EmbeddingModel 的 bean 名稱（openAiEmbeddingModel / ollamaEmbeddingModel）。 */
            @DefaultValue("openAiEmbeddingModel") String bean,
            /** 向量維度，必須等於資料庫 schema 的 embedding 維度。 */
            @DefaultValue("1536") int dimension
    ) {
    }

    public record Llm(
            /** Spring AI ChatModel 的 bean 名稱（openAiChatModel / ollamaChatModel / anthropicChatModel）。 */
            @DefaultValue("openAiChatModel") String bean,
            @DefaultValue("2") int maxRetries,
            @DefaultValue("500") long retryBackoffMs,
            @DefaultValue("抱歉，生成服務暫時無法使用，請稍後再試。") String fallbackMessage
    ) {
    }

    public record Chunking(
            @DefaultValue("fixed") String strategy,
            @DefaultValue("512") int chunkSize,
            @DefaultValue("64") int overlap
    ) {
    }

    public record Query(
            @DefaultValue("5") int topK,
            @DefaultValue("0.75") double minSimilarity,
            @DefaultValue("文件中找不到相關資訊。") String noAnswerMessage,
            @DefaultValue("true") boolean postValidation
    ) {
    }

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            /** memory | redis（ADR-009）。 */
            @DefaultValue("memory") String backend,
            Bucket perIp,
            Bucket global,
            /** 非空時，/api/query/** 需帶 X-Access-Code 標頭。 */
            @DefaultValue("") String accessCode
    ) {
        public record Bucket(
                @DefaultValue("20") long capacity,
                @DefaultValue("20") long refillTokens,
                @DefaultValue("60s") Duration refillPeriod
        ) {
        }
    }
}
