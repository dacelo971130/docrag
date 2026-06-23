package com.casper.docrag.query;

import com.casper.docrag.config.AppProperties;
import com.casper.docrag.domain.EmbeddingProvider;
import com.casper.docrag.domain.LlmProvider;
import com.casper.docrag.domain.VectorStore;
import com.casper.docrag.domain.model.RetrievedChunk;
import com.casper.docrag.persistence.QueryHistoryRepository;
import com.casper.docrag.support.TokenCostEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock
    EmbeddingProvider embeddingProvider;
    @Mock
    VectorStore vectorStore;
    @Mock
    LlmProvider llmProvider;
    @Mock
    QueryHistoryRepository historyRepository;

    QueryService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Embedding("openAiEmbeddingModel", 1536),
                new AppProperties.Llm("openAiChatModel", 2, 500, "降級訊息"),
                new AppProperties.Chunking("fixed", 512, 64),
                new AppProperties.Query(5, 0.75, "文件中找不到相關資訊。", true),
                new AppProperties.RateLimit(true, "memory",
                        new AppProperties.RateLimit.Bucket(20, 20, Duration.ofSeconds(60)),
                        new AppProperties.RateLimit.Bucket(200, 200, Duration.ofSeconds(60)),
                        ""));
        service = new QueryService(embeddingProvider, vectorStore, new PromptBuilder(), llmProvider,
                new GroundingService(), new TokenCostEstimator(), historyRepository, props);
    }

    @Test
    void emptyRetrievalReturnsFixedMessageAndSkipsLlm() {
        when(embeddingProvider.embed("問題")).thenReturn(new float[]{0.1f, 0.2f});
        when(vectorStore.search(any(), eq(5), isNull(), eq(0.75))).thenReturn(List.of());

        StringBuilder output = new StringBuilder();
        service.streamAnswer("問題", null, output::append);

        // 門檻判斷：回固定訊息、不呼叫 LLM（省成本＋防幻覺）
        assertThat(output.toString()).isEqualTo("文件中找不到相關資訊。");
        verifyNoInteractions(llmProvider);
        verify(historyRepository).save(argThat(r -> Boolean.FALSE.equals(r.grounded())));
    }

    @Test
    void withHitsStreamsLlmTokensAndRecordsGrounded() {
        when(embeddingProvider.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        RetrievedChunk hit = new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), 0, "相關內容", 0.9);
        when(vectorStore.search(any(), eq(5), isNull(), eq(0.75))).thenReturn(List.of(hit));
        doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(1);
            onToken.accept("答");
            onToken.accept("案");
            return null;
        }).when(llmProvider).generateStream(anyString(), any());

        StringBuilder output = new StringBuilder();
        service.streamAnswer("問題", null, output::append);

        assertThat(output.toString()).isEqualTo("答案");
        verify(llmProvider).generateStream(anyString(), any());
        verify(historyRepository).save(argThat(r ->
                Boolean.TRUE.equals(r.grounded()) && "答案".equals(r.answer()) && r.retrievedChunkIds().size() == 1));
    }

    @Test
    void passesDocumentScopeToVectorSearch() {
        UUID scope = UUID.randomUUID();
        when(embeddingProvider.embed(anyString())).thenReturn(new float[]{0.1f});
        when(vectorStore.search(any(), eq(5), eq(scope.toString()), eq(0.75))).thenReturn(List.of());

        service.streamAnswer("問題", scope.toString(), s -> {
        });

        verify(vectorStore).search(any(), eq(5), eq(scope.toString()), eq(0.75));
    }
}
