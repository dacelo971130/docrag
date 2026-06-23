package com.casper.docrag.ratelimit;

import com.casper.docrag.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis(Lettuce) 分散式 Bucket4j 限流（ADR-009）。多應用實例共享同一限額狀態，
 * 解決記憶體內限流在水平擴展時各自為政的問題。僅在 {@code app.ratelimit.backend=redis}
 * 時啟用，需 Redis 在線（docker compose up -d redis）。
 */
@Component
@ConditionalOnProperty(name = "app.ratelimit.backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter, DisposableBean {

    private final AppProperties.RateLimit cfg;
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final LettuceBasedProxyManager<String> proxyManager;

    public RedisRateLimiter(AppProperties props,
                            @Value("${spring.data.redis.host:localhost}") String host,
                            @Value("${spring.data.redis.port:6379}") int port) {
        this.cfg = props.ratelimit();
        this.redisClient = RedisClient.create(RedisURI.builder().withHost(host).withPort(port).build());
        this.connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        Duration ttl = max(cfg.perIp().refillPeriod(), cfg.global().refillPeriod()).multipliedBy(2);
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(ttl))
                .build();
    }

    @Override
    public Result tryConsume(String ipKey) {
        ConsumptionProbe ip = bucket("rl:ip:" + ipKey, cfg.perIp()).tryConsumeAndReturnRemaining(1);
        if (!ip.isConsumed()) {
            return Result.rejected(toSeconds(ip));
        }
        ConsumptionProbe global = bucket("rl:global", cfg.global()).tryConsumeAndReturnRemaining(1);
        if (!global.isConsumed()) {
            return Result.rejected(toSeconds(global));
        }
        return Result.ok();
    }

    private Bucket bucket(String key, AppProperties.RateLimit.Bucket b) {
        return proxyManager.builder().build(key, configuration(b));
    }

    private static BucketConfiguration configuration(AppProperties.RateLimit.Bucket b) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(b.capacity())
                .refillGreedy(b.refillTokens(), b.refillPeriod())
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static long toSeconds(ConsumptionProbe probe) {
        return Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
    }

    @Override
    public void destroy() {
        connection.close();
        redisClient.shutdown();
    }
}
