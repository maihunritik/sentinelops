package io.sentinelops.analyzer.detection;

import io.sentinelops.common.TelemetryEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streaming anomaly detector. Keeps a sliding window per service and compares it against two
 * rules:
 * <ul>
 *     <li><b>Error-rate spike</b>: the share of 5xx responses is above a fixed threshold.</li>
 *     <li><b>Latency regression</b>: p95 latency is several times the service's own EWMA baseline.
 *     The baseline is only updated from healthy windows, so an ongoing regression cannot slowly
 *     teach the detector that slowness is normal.</li>
 * </ul>
 * State lives in memory. Kafka partitions telemetry by service, so each service is owned by
 * exactly one analyzer replica.
 */
@Component
public class AnomalyDetector {

    private static final class ServiceState {
        final SlidingWindow window;
        Double baselineP95;

        ServiceState(DetectionProperties props) {
            this.window = new SlidingWindow(props.window(), props.maxSamplesPerService());
        }
    }

    private final Map<String, ServiceState> services = new ConcurrentHashMap<>();
    private final DetectionProperties props;
    private final Clock clock;

    public AnomalyDetector(DetectionProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public void record(TelemetryEvent event) {
        Instant now = clock.instant();
        // Clamp client clock skew: a timestamp from the future would otherwise never expire.
        Instant at = event.timestamp().isAfter(now) ? now : event.timestamp();
        if (at.isBefore(now.minus(props.window()))) {
            return; // too late to matter for the live window
        }
        ServiceState state = services.computeIfAbsent(event.service(), s -> new ServiceState(props));
        synchronized (state) {
            state.window.add(at, event.latencyMs(), event.isError());
        }
    }

    /** Evaluates every known service. */
    public List<Assessment> evaluate(Instant now) {
        List<Assessment> out = new ArrayList<>(services.size());
        services.forEach((service, state) -> out.add(assess(service, state, now)));
        return out;
    }

    /** Current view of one service without changing its baseline. */
    public Optional<Assessment> peek(String service) {
        ServiceState state = services.get(service);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            WindowStats stats = state.window.stats(clock.instant());
            return Optional.of(classify(service, stats, state.baselineP95));
        }
    }

    public List<String> knownServices() {
        return services.keySet().stream().sorted().toList();
    }

    private Assessment assess(String service, ServiceState state, Instant now) {
        synchronized (state) {
            WindowStats stats = state.window.stats(now);
            Assessment assessment = classify(service, stats, state.baselineP95);
            if (assessment.judged() && !assessment.has(AnomalyType.LATENCY_REGRESSION)) {
                state.baselineP95 = state.baselineP95 == null
                        ? stats.p95Ms()
                        : props.baselineAlpha() * stats.p95Ms() + (1 - props.baselineAlpha()) * state.baselineP95;
            }
            return assessment;
        }
    }

    private Assessment classify(String service, WindowStats stats, Double baseline) {
        if (stats.requests() < props.minSamples()) {
            return new Assessment(service, stats, baseline, false, EnumSet.noneOf(AnomalyType.class));
        }
        EnumSet<AnomalyType> anomalies = EnumSet.noneOf(AnomalyType.class);
        if (stats.errorRate() >= props.errorRateThreshold()) {
            anomalies.add(AnomalyType.ERROR_RATE_SPIKE);
        }
        if (baseline != null
                && stats.p95Ms() >= props.latencyFloorMs()
                && stats.p95Ms() > baseline * props.latencyFactor()) {
            anomalies.add(AnomalyType.LATENCY_REGRESSION);
        }
        return new Assessment(service, stats, baseline, true, anomalies);
    }
}
