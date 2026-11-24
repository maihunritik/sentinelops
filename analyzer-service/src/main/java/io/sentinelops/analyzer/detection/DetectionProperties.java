package io.sentinelops.analyzer.detection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tuning knobs for the anomaly detector.
 *
 * @param window              length of the sliding window evaluated for each service
 * @param minSamples          a service needs at least this many requests in the window to be judged
 * @param errorRateThreshold  fraction of 5xx responses that counts as an error-rate spike
 * @param latencyFactor       p95 must exceed {@code baseline * latencyFactor} to count as a regression
 * @param latencyFloorMs      p95 below this is never a regression, whatever the baseline
 * @param baselineAlpha       smoothing factor for the EWMA latency baseline (0..1)
 * @param recoveryEvaluations consecutive healthy evaluations needed before an incident auto-resolves
 * @param maxSamplesPerService memory bound for one service's window
 */
@ConfigurationProperties("sentinel.detection")
public record DetectionProperties(
        @DefaultValue("PT1M") Duration window,
        @DefaultValue("20") int minSamples,
        @DefaultValue("0.2") double errorRateThreshold,
        @DefaultValue("2.0") double latencyFactor,
        @DefaultValue("250") long latencyFloorMs,
        @DefaultValue("0.2") double baselineAlpha,
        @DefaultValue("2") int recoveryEvaluations,
        @DefaultValue("20000") int maxSamplesPerService) {
}
