package io.sentinelops.ingest.dedup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed dedup using {@code SET key 1 NX EX ttl}: a single round trip that is safe
 * across any number of ingest replicas.
 */
@Component
@ConditionalOnProperty(name = "sentinel.dedup.store", havingValue = "redis", matchIfMissing = true)
public class RedisDedupStore implements DedupStore {

    private static final String PREFIX = "sentinel:dedup:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisDedupStore(StringRedisTemplate redis,
                           @Value("${sentinel.dedup.ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public boolean markIfFirstSeen(String eventId) {
        Boolean created = redis.opsForValue().setIfAbsent(PREFIX + eventId, "1", ttl);
        return Boolean.TRUE.equals(created);
    }
}
