package io.sentinelops.analyzer.incident;

import io.sentinelops.common.IncidentEvent;

/** Spring application event, relayed to Kafka only after the database transaction commits. */
public record IncidentChanged(IncidentEvent event) {
}
