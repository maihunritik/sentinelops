package io.sentinelops.analyzer.detection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Time-based sliding window of request samples for one service. Samples are kept in arrival
 * order; old ones are pruned on every read. Not thread-safe on its own; callers synchronise.
 */
final class SlidingWindow {

    private record Sample(long atMillis, long latencyMs, boolean error) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final long windowMillis;
    private final int maxSamples;
    private int errors;

    SlidingWindow(Duration window, int maxSamples) {
        this.windowMillis = window.toMillis();
        this.maxSamples = maxSamples;
    }

    void add(Instant at, long latencyMs, boolean error) {
        if (samples.size() >= maxSamples) {
            evict(samples.pollFirst());
        }
        samples.addLast(new Sample(at.toEpochMilli(), latencyMs, error));
        if (error) {
            errors++;
        }
    }

    WindowStats stats(Instant now) {
        prune(now);
        int n = samples.size();
        if (n == 0) {
            return WindowStats.EMPTY;
        }
        long[] latencies = new long[n];
        int i = 0;
        for (Sample s : samples) {
            latencies[i++] = s.latencyMs();
        }
        Arrays.sort(latencies);
        return new WindowStats(n, errors, (double) errors / n,
                percentile(latencies, 0.50), percentile(latencies, 0.95), percentile(latencies, 0.99));
    }

    private void prune(Instant now) {
        long cutoff = now.toEpochMilli() - windowMillis;
        // Samples arrive roughly in time order. A slightly late sample sitting behind a fresher one is
        // pruned once it reaches the head, which is accurate enough for a window of a minute or so.
        while (!samples.isEmpty() && samples.peekFirst().atMillis() < cutoff) {
            evict(samples.pollFirst());
        }
    }

    private void evict(Sample s) {
        if (s != null && s.error()) {
            errors--;
        }
    }

    /** Nearest-rank percentile on a sorted array. */
    static long percentile(long[] sorted, double p) {
        int rank = (int) Math.ceil(p * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }
}
