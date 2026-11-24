package io.sentinelops.analyzer.detection;

import io.sentinelops.analyzer.MutableClock;
import io.sentinelops.common.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    private MutableClock clock;
    private AnomalyDetector detector;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));
        DetectionProperties props = new DetectionProperties(Duration.ofMinutes(1), 10, 0.2, 2.0, 250, 0.2, 2, 1000);
        detector = new AnomalyDetector(props, clock);
    }

    @Test
    void ignoresServicesWithTooLittleTraffic() {
        send("orders", 5, 0, 500);
        Assessment a = only(detector);
        assertThat(a.judged()).isFalse();
        assertThat(a.anomalies()).isEmpty();
    }

    @Test
    void flagsErrorRateSpike() {
        send("payments", 20, 8, 80); // 40% errors
        Assessment a = only(detector);
        assertThat(a.judged()).isTrue();
        assertThat(a.has(AnomalyType.ERROR_RATE_SPIKE)).isTrue();
        assertThat(a.stats().errorRate()).isEqualTo(0.4);
    }

    @Test
    void learnsBaselineThenFlagsLatencyRegression() {
        send("search", 50, 0, 100);
        Assessment learning = only(detector);
        assertThat(learning.baselineP95()).isNull();          // first window only learns
        assertThat(learning.anomalies()).isEmpty();

        clock.advance(Duration.ofMinutes(2));                  // old samples leave the window
        send("search", 50, 0, 600);
        Assessment slow = only(detector);
        assertThat(slow.baselineP95()).isEqualTo(100.0);
        assertThat(slow.has(AnomalyType.LATENCY_REGRESSION)).isTrue();
    }

    @Test
    void regressionDoesNotPoisonTheBaseline() {
        send("search", 50, 0, 100);
        only(detector);
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(2));
            send("search", 50, 0, 900);
            assertThat(only(detector).has(AnomalyType.LATENCY_REGRESSION)).isTrue();
        }
        assertThat(only(detector).baselineP95()).isEqualTo(100.0);
    }

    @Test
    void latencyBelowFloorIsNeverARegression() {
        send("cache", 50, 0, 10);
        only(detector);
        clock.advance(Duration.ofMinutes(2));
        send("cache", 50, 0, 60); // 6x baseline, but still fast
        assertThat(only(detector).anomalies()).isEmpty();
    }

    @Test
    void dropsEventsOlderThanTheWindowAndClampsFutureOnes() {
        detector.record(event("late", 500, 10, clock.instant().minus(Duration.ofMinutes(5))));
        assertThat(detector.knownServices()).isEmpty();

        detector.record(event("skewed", 200, 10, clock.instant().plus(Duration.ofHours(1))));
        clock.advance(Duration.ofMinutes(2));
        assertThat(detector.peek("skewed").orElseThrow().stats().requests()).isZero();
    }

    @Test
    void percentileUsesNearestRank() {
        long[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThat(SlidingWindow.percentile(sorted, 0.5)).isEqualTo(5);
        assertThat(SlidingWindow.percentile(sorted, 0.95)).isEqualTo(10);
        assertThat(SlidingWindow.percentile(new long[]{42}, 0.99)).isEqualTo(42);
    }

    private void send(String service, int total, int errors, long latencyMs) {
        for (int i = 0; i < total; i++) {
            detector.record(event(service, i < errors ? 503 : 200, latencyMs, clock.instant()));
        }
    }

    private static TelemetryEvent event(String service, int status, long latency, Instant at) {
        return new TelemetryEvent(UUID.randomUUID().toString(), service, "GET /", status, latency, at);
    }

    private Assessment only(AnomalyDetector d) {
        var all = d.evaluate(clock.instant());
        assertThat(all).hasSize(1);
        return all.getFirst();
    }
}
