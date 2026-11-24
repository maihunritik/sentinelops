package io.sentinelops.analyzer.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentinelops.analyzer.detection.AnomalyDetector;
import io.sentinelops.common.TelemetryEvent;
import io.sentinelops.common.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryConsumer {

    private final AnomalyDetector detector;
    private final Counter consumed;

    public TelemetryConsumer(AnomalyDetector detector, MeterRegistry meters) {
        this.detector = detector;
        this.consumed = meters.counter("sentinel.analyzer.consumed");
    }

    @KafkaListener(topics = Topics.TELEMETRY, groupId = "${sentinel.kafka.group-id:analyzer}",
            concurrency = "${sentinel.kafka.concurrency:3}", batch = "true")
    public void onBatch(java.util.List<TelemetryEvent> events) {
        for (TelemetryEvent event : events) {
            if (event != null) {
                detector.record(event);
                consumed.increment();
            }
        }
    }
}
