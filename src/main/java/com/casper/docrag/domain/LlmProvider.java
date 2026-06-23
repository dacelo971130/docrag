package com.casper.docrag.domain;

import java.util.function.Consumer;

/**
 * LLM 抽象 — 支援串流（SPEC §3.1）。
 *
 * <p>串流回傳型別刻意不綁定 Flux（響應式）。串流端點已定案使用 Spring MVC + SseEmitter
 * （ADR-006）；介面以 {@link Consumer} 回呼保持中性，adapter 層負責把 token 餵進 SseEmitter。
 */
public interface LlmProvider {

    /** 逐 token 串流生成；每產生一段文字即呼叫 onToken。 */
    void generateStream(String prompt, Consumer<String> onToken);

    /** 非串流版本（測試與降級用）。 */
    String generate(String prompt);
}
