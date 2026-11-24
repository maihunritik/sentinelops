package io.sentinelops.analyzer.detection;

import java.util.Set;

/**
 * Result of evaluating one service.
 *
 * @param judged       false when there was too little traffic to say anything
 * @param baselineP95  learned normal p95 latency, or {@code null} while still learning
 */
public record Assessment(String service, WindowStats stats, Double baselineP95, boolean judged,
                         Set<AnomalyType> anomalies) {

    public boolean has(AnomalyType type) {
        return anomalies.contains(type);
    }
}
