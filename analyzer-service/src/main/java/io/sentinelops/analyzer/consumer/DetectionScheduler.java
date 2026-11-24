package io.sentinelops.analyzer.consumer;

import io.sentinelops.analyzer.detection.AnomalyDetector;
import io.sentinelops.analyzer.incident.IncidentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Periodically evaluates every service window and reconciles incidents. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "sentinel.detection.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DetectionScheduler {

    private final AnomalyDetector detector;
    private final IncidentService incidents;
    private final Clock clock;

    public DetectionScheduler(AnomalyDetector detector, IncidentService incidents, Clock clock) {
        this.detector = detector;
        this.incidents = incidents;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sentinel.detection.evaluation-interval:PT5S}")
    public void tick() {
        incidents.reconcile(detector.evaluate(clock.instant()));
    }
}
