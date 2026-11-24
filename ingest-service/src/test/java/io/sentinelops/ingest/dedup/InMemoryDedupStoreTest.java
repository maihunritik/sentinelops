package io.sentinelops.ingest.dedup;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDedupStoreTest {

    @Test
    void firstSeenOnlyOnceWithinTtl() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-24T10:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        InMemoryDedupStore store = new InMemoryDedupStore(Duration.ofMinutes(10), clock);

        assertThat(store.markIfFirstSeen("e1")).isTrue();
        assertThat(store.markIfFirstSeen("e1")).isFalse();
        assertThat(store.markIfFirstSeen("e2")).isTrue();

        now.set(now.get().plus(Duration.ofMinutes(11)));
        assertThat(store.markIfFirstSeen("e1")).isTrue();
    }
}
