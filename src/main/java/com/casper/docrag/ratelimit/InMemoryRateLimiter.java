package com.casper.docrag.ratelimit;

import com.casper.docrag.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 記憶體內 Bucket4j 限流（單機，預設後端）。每 IP 一個 bucket + 一個全域 bucket（ADR-009 分層）。
 * 先消耗 per-IP token，再消耗全域 token；任一不足即拒絕。
 */
@Component
@ConditionalOnProperty(name = "app.ratelimit.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final AppProperties.RateLimit cfg;
    private final Bucket globalBucket;
    private final ConcurrentMap<String, Bucket> perIpBuckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(AppProperties props) {
        this.cfg = props.ratelimit();
        this.globalBucket = newBucket(cfg.global());
    }

    @Override
    public Result tryConsume(String ipKey) {
        Bucket ipBucket = perIpBuckets.computeIfAbsent(ipKey, k -> newBucket(cfg.perIp()));
        ConsumptionProbe ip = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ip.isConsumed()) {
            return Result.rejected(toSeconds(ip));
        }
        ConsumptionProbe global = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!global.isConsumed()) {
            return Result.rejected(toSeconds(global));
        }
        return Result.ok();
    }

    private static Bucket newBucket(AppProperties.RateLimit.Bucket b) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(b.capacity())
                .refillGreedy(b.refillTokens(), b.refillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static long toSeconds(ConsumptionProbe probe) {
        return Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
    }
}
