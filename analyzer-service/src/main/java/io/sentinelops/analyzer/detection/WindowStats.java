package io.sentinelops.analyzer.detection;

/** Aggregates for one service over the current sliding window. */
public record WindowStats(int requests, int errors, double errorRate, long p50Ms, long p95Ms, long p99Ms) {

    public static final WindowStats EMPTY = new WindowStats(0, 0, 0, 0, 0, 0);
}
