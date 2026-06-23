package com.casper.docrag.ratelimit;

/** 限流抽象（ADR-009）。實作可為記憶體內或 Redis 分散式，皆基於 Bucket4j。 */
public interface RateLimiter {

    Result tryConsume(String ipKey);

    /** @param allowed 是否放行；retryAfterSeconds 為被拒時建議的重試秒數。 */
    record Result(boolean allowed, long retryAfterSeconds) {

        public static Result ok() {
            return new Result(true, 0);
        }

        public static Result rejected(long retryAfterSeconds) {
            return new Result(false, retryAfterSeconds);
        }
    }
}
