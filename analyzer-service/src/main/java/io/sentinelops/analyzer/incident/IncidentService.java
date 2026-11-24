package io.sentinelops.analyzer.incident;

import io.micrometer.core.instrument.MeterRegistry;
import io.sentinelops.analyzer.detection.AnomalyType;
import io.sentinelops.analyzer.detection.Assessment;
import io.sentinelops.analyzer.detection.DetectionProperties;
import io.sentinelops.analyzer.detection.WindowStats;
import io.sentinelops.common.IncidentEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns detector assessments into incident lifecycle changes: open on the first anomalous
 * evaluation, update peaks while it lasts, resolve after a few consecutive healthy evaluations.
 */
@Service
public class IncidentService {

    public static final String CACHE = "incidents";

    private final IncidentRepository repository;
    private final ApplicationEventPublisher events;
    private final CacheManager cacheManager;
    private final DetectionProperties props;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Map<String, Integer> healthyStreak = new ConcurrentHashMap<>();

    public IncidentService(IncidentRepository repository, ApplicationEventPublisher events, CacheManager cacheManager,
                           DetectionProperties props, Clock clock, MeterRegistry meters) {
        this.repository = repository;
        this.events = events;
        this.cacheManager = cacheManager;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
    }

    @Transactional
    public void reconcile(List<Assessment> assessments) {
        Instant now = clock.instant();
        for (Assessment a : assessments) {
            if (!a.judged()) {
                continue; // not enough traffic to open or close anything
            }
            for (AnomalyType type : AnomalyType.values()) {
                Optional<Incident> active = repository.findFirstByServiceAndTypeAndStatusIn(
                        a.service(), type, IncidentStatus.ACTIVE);
                String key = a.service() + "|" + type;
                if (a.has(type)) {
                    healthyStreak.remove(key);
                    Severity severity = severity(type, a);
                    String summary = summarize(type, a);
                    if (active.isEmpty()) {
                        Incident created = repository.save(new Incident(a.service(), type, severity, summary, now,
                                a.stats().errorRate(), a.stats().p95Ms(), round(a.baselineP95())));
                        meters.counter("sentinel.incidents.opened", "type", type.name()).increment();
                        publish(created);
                    } else {
                        active.get().observe(a.stats().errorRate(), a.stats().p95Ms(), severity, summary, now);
                        evict(active.get().getId());
                    }
                } else if (active.isPresent()) {
                    int streak = healthyStreak.merge(key, 1, Integer::sum);
                    if (streak >= props.recoveryEvaluations()) {
                        healthyStreak.remove(key);
                        active.get().resolve(now);
                        evict(active.get().getId());
                        meters.counter("sentinel.incidents.resolved", "type", type.name()).increment();
                        publish(active.get());
                    }
                }
            }
        }
    }

    @Cacheable(cacheNames = CACHE, key = "#id")
    @Transactional(readOnly = true)
    public IncidentView get(long id) {
        return repository.findById(id).map(IncidentView::of).orElseThrow(() -> new IncidentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<IncidentView> list(Set<IncidentStatus> statuses, String service, int limit) {
        Set<IncidentStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(IncidentStatus.class) : statuses;
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<Incident> found = service == null || service.isBlank()
                ? repository.findByStatusInOrderByOpenedAtDesc(filter, page)
                : repository.findByServiceAndStatusInOrderByOpenedAtDesc(service, filter, page);
        return found.stream().map(IncidentView::of).toList();
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    @Transactional
    public IncidentView acknowledge(long id, String by, String note) {
        Incident incident = repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
        incident.acknowledge(by, note, clock.instant());
        publish(incident);
        return IncidentView.of(incident);
    }

    private void publish(Incident i) {
        events.publishEvent(new IncidentChanged(new IncidentEvent(i.getId(), i.getService(), i.getType().name(),
                i.getSeverity().name(), i.getStatus().name(), i.getSummary(), i.getUpdatedAt())));
    }

    private void evict(Long id) {
        var cache = cacheManager.getCache(CACHE);
        if (cache != null && id != null) {
            cache.evict(id);
        }
    }

    static Severity severity(AnomalyType type, Assessment a) {
        WindowStats s = a.stats();
        return switch (type) {
            case ERROR_RATE_SPIKE -> s.errorRate() >= 0.5 ? Severity.CRITICAL : Severity.HIGH;
            case LATENCY_REGRESSION -> a.baselineP95() != null && s.p95Ms() >= 4 * a.baselineP95()
                    ? Severity.HIGH : Severity.MEDIUM;
        };
    }

    static String summarize(AnomalyType type, Assessment a) {
        WindowStats s = a.stats();
        return switch (type) {
            case ERROR_RATE_SPIKE -> "%s: %.0f%% of %d requests failed with 5xx in the last window"
                    .formatted(a.service(), s.errorRate() * 100, s.requests());
            case LATENCY_REGRESSION -> "%s: p95 latency %d ms vs baseline %d ms (%.1fx)"
                    .formatted(a.service(), s.p95Ms(), round(a.baselineP95()),
                            s.p95Ms() / Objects.requireNonNull(a.baselineP95()));
        };
    }

    private static Long round(Double d) {
        return d == null ? null : Math.round(d);
    }
}
