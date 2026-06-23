package com.casper.docrag.support;

import java.util.function.Supplier;

/** 極簡同步重試（M4：LLM 失敗重試）。線性退避，僅攔截 RuntimeException。 */
public final class Retries {

    private Retries() {
    }

    public static <T> T withRetry(int maxRetries, long backoffMs, Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt < maxRetries) {
                    sleep(backoffMs * (attempt + 1));
                }
            }
        }
        throw last;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("retry interrupted", e);
        }
    }
}
