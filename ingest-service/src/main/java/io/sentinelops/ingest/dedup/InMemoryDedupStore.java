package io.sentinelops.ingest.dedup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node dedup for local development and tests. Not shared between replicas.
 */
@Component
@ConditionalOnProperty(name = "sentinel.dedup.store", havingValue = "memory")
public class InMemoryDedupStore implements DedupStore {

    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public InMemoryDedupStore(@Value("${sentinel.dedup.ttl:PT10M}") Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    InMemoryDedupStore(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public boolean markIfFirstSeen(String eventId) {
        Instant now = clock.instant();
        if (seen.size() > 100_000) {
            seen.values().removeIf(expiry -> expiry.isBefore(now));
        }
        boolean[] first = {false};
        seen.compute(eventId, (id, expiry) -> {
            if (expiry == null || expiry.isBefore(now)) {
                first[0] = true;
                return now.plus(ttl);
            }
            return expiry;
        });
        return first[0];
    }
}
