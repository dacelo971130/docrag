package com.casper.docrag.provider.llm;

import com.casper.docrag.domain.LlmProvider;
import com.casper.docrag.support.Retries;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.function.Consumer;

/**
 * {@link LlmProvider} 的 Spring AI adapter（ADR-007）。包裝任一 Spring AI
 * {@link ChatModel}（OpenAI / Ollama / Anthropic）。
 *
 * <p>串流以 {@code Flux.toStream()} 轉為阻塞迭代，逐 token 餵入 {@link Consumer}，
 * 讓上層維持 MVC + SseEmitter（ADR-006），領域介面不外露 Flux。
 *
 * <p>降級（fallback）策略不在此處：adapter 只負責呼叫模型，失敗就拋出；fallback 訊息由
 * 應用層（QueryService / 控制器）套用，以維持責任分離。{@link #generate} 帶重試（M4）。
 */
public class SpringAiLlmProvider implements LlmProvider {

    private final ChatModel model;
    private final int maxRetries;
    private final long backoffMs;

    public SpringAiLlmProvider(ChatModel model, int maxRetries, long backoffMs) {
        this.model = model;
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
    }

    @Override
    public void generateStream(String prompt, Consumer<String> onToken) {
        model.stream(new Prompt(prompt))
                .toStream()
                .forEach(response -> {
                    Generation generation = response.getResult();
                    if (generation == null) {
                        return;
                    }
                    AssistantMessage output = generation.getOutput();
                    if (output == null) {
                        return;
                    }
                    String token = output.getText();
                    if (token != null && !token.isEmpty()) {
                        onToken.accept(token);
                    }
                });
    }

    @Override
    public String generate(String prompt) {
        return Retries.withRetry(maxRetries, backoffMs, () -> {
            Generation generation = model.call(new Prompt(prompt)).getResult();
            return generation == null || generation.getOutput() == null
                    ? ""
                    : generation.getOutput().getText();
        });
    }
}
