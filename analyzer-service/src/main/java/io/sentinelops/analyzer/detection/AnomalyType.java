package io.sentinelops.analyzer.detection;

public enum AnomalyType {
    /** Share of 5xx responses in the window crossed the threshold. */
    ERROR_RATE_SPIKE,
    /** p95 latency rose well above the service's own learned baseline. */
    LATENCY_REGRESSION
}
