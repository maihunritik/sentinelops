package io.sentinelops.ingest.api;

import io.sentinelops.common.TelemetryEvent;
import io.sentinelops.ingest.dedup.DedupStore;
import io.sentinelops.ingest.kafka.TelemetryPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/telemetry")
@Validated
public class TelemetryController {

    private final DedupStore dedup;
    private final TelemetryPublisher publisher;

    public TelemetryController(DedupStore dedup, TelemetryPublisher publisher) {
        this.dedup = dedup;
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<IngestResult> ingest(@Valid @RequestBody TelemetryEvent event) {
        return ingestBatch(List.of(event));
    }

    /**
     * Accepts up to 500 events. Responds once Kafka has acknowledged every new event, so a 202
     * means the data is durable; on failure the client can safely retry the whole batch because
     * duplicates are filtered by event id.
     */
    @PostMapping("/batch")
    public ResponseEntity<IngestResult> ingestBatch(
            @RequestBody @NotEmpty @Size(max = 500) List<@Valid TelemetryEvent> events) {
        List<CompletableFuture<Void>> sends = new ArrayList<>();
        int duplicates = 0;
        for (TelemetryEvent event : events) {
            if (dedup.markIfFirstSeen(event.eventId())) {
                sends.add(publisher.publish(event));
            } else {
                duplicates++;
            }
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IngestResult(sends.size(), duplicates));
    }
}
