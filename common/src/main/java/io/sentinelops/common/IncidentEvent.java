package io.sentinelops.common;

import java.time.Instant;

/** Lifecycle notification emitted whenever an incident is opened or resolved. */
public record IncidentEvent(
        long incidentId,
        String service,
        String type,
        String severity,
        String status,
        String summary,
        Instant at) {
}
