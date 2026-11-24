package io.sentinelops.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * One observed request made to a monitored service.
 *
 * @param eventId    client-generated id, used for idempotent ingestion
 * @param service    logical service name, also the Kafka partition key
 * @param endpoint   route or operation, e.g. {@code POST /payments}
 * @param statusCode HTTP-style status; 5xx counts as an error
 * @param latencyMs  observed latency in milliseconds
 * @param timestamp  when the request completed
 */
public record TelemetryEvent(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 64) String service,
        @Size(max = 128) String endpoint,
        @Min(100) @Max(599) int statusCode,
        @PositiveOrZero long latencyMs,
        @NotNull Instant timestamp) {

    public boolean isError() {
        return statusCode >= 500;
    }
}
