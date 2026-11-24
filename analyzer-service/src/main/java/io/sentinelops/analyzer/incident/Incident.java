package io.sentinelops.analyzer.incident;

import io.sentinelops.analyzer.detection.AnomalyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(nullable = false)
    private String summary;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    private String note;

    @Column(name = "peak_error_rate", nullable = false)
    private double peakErrorRate;

    @Column(name = "peak_p95_ms", nullable = false)
    private long peakP95Ms;

    @Column(name = "baseline_p95_ms")
    private Long baselineP95Ms;

    @Version
    private long version;

    protected Incident() {
    }

    public Incident(String service, AnomalyType type, Severity severity, String summary, Instant at,
                    double errorRate, long p95Ms, Long baselineP95Ms) {
        this.service = service;
        this.type = type;
        this.severity = severity;
        this.summary = summary;
        this.status = IncidentStatus.OPEN;
        this.openedAt = at;
        this.updatedAt = at;
        this.peakErrorRate = errorRate;
        this.peakP95Ms = p95Ms;
        this.baselineP95Ms = baselineP95Ms;
    }

    /** Records a new observation while the incident is still active; peaks only ever grow. */
    public void observe(double errorRate, long p95Ms, Severity newSeverity, String newSummary, Instant at) {
        this.peakErrorRate = Math.max(peakErrorRate, errorRate);
        this.peakP95Ms = Math.max(peakP95Ms, p95Ms);
        if (newSeverity.compareTo(severity) > 0) {
            this.severity = newSeverity;
            this.summary = newSummary;
        }
        this.updatedAt = at;
    }

    public void acknowledge(String by, String note, Instant at) {
        if (status == IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Incident " + id + " is already resolved");
        }
        this.status = IncidentStatus.ACKNOWLEDGED;
        this.acknowledgedBy = by;
        this.note = note;
        this.updatedAt = at;
    }

    public void resolve(Instant at) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = at;
        this.updatedAt = at;
    }

    public Long getId() { return id; }
    public String getService() { return service; }
    public AnomalyType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public String getNote() { return note; }
    public double getPeakErrorRate() { return peakErrorRate; }
    public long getPeakP95Ms() { return peakP95Ms; }
    public Long getBaselineP95Ms() { return baselineP95Ms; }
}
