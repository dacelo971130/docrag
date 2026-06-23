package com.casper.docrag.query;

import com.casper.docrag.config.AppProperties;
import com.casper.docrag.domain.EmbeddingProvider;
import com.casper.docrag.domain.LlmProvider;
import com.casper.docrag.domain.VectorStore;
import com.casper.docrag.domain.model.QueryRecord;
import com.casper.docrag.domain.model.RetrievedChunk;
import com.casper.docrag.persistence.QueryHistoryRepository;
import com.casper.docrag.support.TokenCostEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 查詢編排（SPEC §4.2 步驟 1–7）。限流（步驟 0）在控制器前置。
 *
 * <p>grounding 四層防線：
 * <ol>
 *   <li>防線 1（檢索門檻 minSimilarity）＋防線 2（documentScope）：於 {@link VectorStore#search} 內。</li>
 *   <li>門檻判斷：檢索為空則回固定訊息、不呼叫 LLM（省成本＋防幻覺）。</li>
 *   <li>防線 3（prompt 約束）：於 {@link PromptBuilder}。</li>
 *   <li>防線 4（後處理引用驗證）：於 {@link GroundingService}。</li>
 * </ol>
 * 每次查詢記錄延遲分解（embedding/retrieval/llm）與 token 成本到 query_history。
 */
@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final PromptBuilder promptBuilder;
    private final LlmProvider llmProvider;
    private final GroundingService groundingService;
    private final TokenCostEstimator tokenCostEstimator;
    private final QueryHistoryRepository historyRepository;
    private final AppProperties props;

    public QueryService(EmbeddingProvider embeddingProvider,
                        VectorStore vectorStore,
                        PromptBuilder promptBuilder,
                        LlmProvider llmProvider,
                        GroundingService groundingService,
                        TokenCostEstimator tokenCostEstimator,
                        QueryHistoryRepository historyRepository,
                        AppProperties props) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.promptBuilder = promptBuilder;
        this.llmProvider = llmProvider;
        this.groundingService = groundingService;
        this.tokenCostEstimator = tokenCostEstimator;
        this.historyRepository = historyRepository;
        this.props = props;
    }

    /**
     * 串流回答；每段 token 透過 {@code onToken} 輸出（由控制器餵入 SseEmitter）。
     * 方法回傳時代表生成結束且歷史已記錄。
     */
    public void streamAnswer(String question, String documentScope, Consumer<String> onToken) {
        long startNanos = System.nanoTime();
        UUID scope = parseScope(documentScope);
        AppProperties.Query q = props.query();

        // 1. 問題向量化
        long t = System.nanoTime();
        float[] queryVector = embeddingProvider.embed(question);
        int embeddingMs = elapsedMs(t);

        // 2. 向量檢索（含防線 1 門檻、防線 2 scope）
        t = System.nanoTime();
        List<RetrievedChunk> hits = vectorStore.search(queryVector, q.topK(), documentScope, q.minSimilarity());
        int retrievalMs = elapsedMs(t);

        // 3. 門檻判斷：無命中即回固定訊息，不呼叫 LLM
        if (hits.isEmpty()) {
            String message = q.noAnswerMessage();
            onToken.accept(message);
            save(question, message, scope, List.of(), false, 0, embeddingMs, retrievalMs, 0,
                    elapsedMs(startNanos));
            log.debug("檢索無命中（門檻 {}），回固定訊息", q.minSimilarity());
            return;
        }

        // 4. 組裝 prompt（防線 3）
        String prompt = promptBuilder.build(question, hits);

        // 5. LLM 串流生成
        StringBuilder answer = new StringBuilder();
        t = System.nanoTime();
        try {
            llmProvider.generateStream(prompt, token -> {
                answer.append(token);
                onToken.accept(token);
            });
        } catch (RuntimeException ex) {
            int llmMsOnError = elapsedMs(t);
            save(question, answer.toString(), scope, chunkIds(hits), true,
                    tokenCostEstimator.estimate(prompt, answer.toString()),
                    embeddingMs, retrievalMs, llmMsOnError, elapsedMs(startNanos));
            log.error("LLM 串流失敗，已記錄部分回答", ex);
            throw ex;
        }
        int llmMs = elapsedMs(t);
        String full = answer.toString();

        // 6. 後處理引用驗證（防線 4，可選）
        if (q.postValidation()) {
            GroundingService.GroundingResult g = groundingService.validate(full, hits.size());
            if (!g.valid()) {
                log.warn("回答含越界引用 {}（檢索 {} 筆）", g.invalidCitations(), hits.size());
            }
        }

        // 7. 記錄
        int tokenCost = tokenCostEstimator.estimate(prompt, full);
        save(question, full, scope, chunkIds(hits), true, tokenCost,
                embeddingMs, retrievalMs, llmMs, elapsedMs(startNanos));
    }

    public List<QueryRecord> history(int limit) {
        return historyRepository.findRecent(limit);
    }

    private void save(String question, String answer, UUID scope, List<UUID> chunkIds, boolean grounded,
                      int tokenCost, int embeddingMs, int retrievalMs, int llmMs, int latencyMs) {
        try {
            historyRepository.save(new QueryRecord(
                    UUID.randomUUID(), question, answer, scope, chunkIds, grounded,
                    tokenCost, latencyMs, embeddingMs, retrievalMs, llmMs, null));
        } catch (RuntimeException e) {
            // 記錄失敗不應影響使用者回應
            log.warn("寫入 query_history 失敗：{}", e.getMessage());
        }
    }

    private static List<UUID> chunkIds(List<RetrievedChunk> hits) {
        return hits.stream().map(RetrievedChunk::chunkId).toList();
    }

    private static UUID parseScope(String documentScope) {
        if (documentScope == null || documentScope.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(documentScope.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("documentScope 不是合法的 UUID：" + documentScope);
        }
    }

    private static int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }
}
