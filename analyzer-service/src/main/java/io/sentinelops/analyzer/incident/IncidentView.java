package io.sentinelops.analyzer.incident;

import io.sentinelops.analyzer.detection.AnomalyType;

import java.time.Instant;

/** Read model returned by the REST API and MCP tools, and stored in the Redis cache. */
public record IncidentView(
        long id,
        String service,
        AnomalyType type,
        Severity severity,
        IncidentStatus status,
        String summary,
        Instant openedAt,
        Instant updatedAt,
        Instant resolvedAt,
        String acknowledgedBy,
        String note,
        double peakErrorRate,
        long peakP95Ms,
        Long baselineP95Ms) {

    public static IncidentView of(Incident i) {
        return new IncidentView(i.getId(), i.getService(), i.getType(), i.getSeverity(), i.getStatus(),
                i.getSummary(), i.getOpenedAt(), i.getUpdatedAt(), i.getResolvedAt(), i.getAcknowledgedBy(),
                i.getNote(), i.getPeakErrorRate(), i.getPeakP95Ms(), i.getBaselineP95Ms());
    }
}
