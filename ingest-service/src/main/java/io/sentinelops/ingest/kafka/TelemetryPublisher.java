package io.sentinelops.ingest.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentinelops.common.TelemetryEvent;
import io.sentinelops.common.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes telemetry to Kafka, keyed by service name so that all events for one service land
 * on the same partition and are therefore analysed by the same consumer, in order.
 */
@Service
public class TelemetryPublisher {

    private final KafkaTemplate<String, TelemetryEvent> kafka;
    private final Counter published;
    private final Counter failed;

    public TelemetryPublisher(KafkaTemplate<String, TelemetryEvent> kafka, MeterRegistry registry) {
        this.kafka = kafka;
        this.published = registry.counter("sentinel.ingest.published");
        this.failed = registry.counter("sentinel.ingest.publish.failed");
    }

    public CompletableFuture<Void> publish(TelemetryEvent event) {
        return kafka.send(Topics.TELEMETRY, event.service(), event)
                .handle((result, error) -> {
                    if (error != null) {
                        failed.increment();
                        throw new PublishException("Failed to publish event " + event.eventId(), error);
                    }
                    published.increment();
                    return null;
                });
    }

    public static class PublishException extends RuntimeException {
        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
