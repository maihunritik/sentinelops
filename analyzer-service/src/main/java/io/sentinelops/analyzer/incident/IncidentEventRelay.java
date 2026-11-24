package io.sentinelops.analyzer.incident;

import io.sentinelops.common.IncidentEvent;
import io.sentinelops.common.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes incident lifecycle events after commit, so downstream consumers (pagers, chat
 * notifiers) never hear about an incident that was rolled back.
 */
@Component
public class IncidentEventRelay {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventRelay.class);

    private final KafkaTemplate<String, IncidentEvent> kafka;

    public IncidentEventRelay(KafkaTemplate<String, IncidentEvent> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(IncidentChanged changed) {
        IncidentEvent e = changed.event();
        log.info("Incident {} {} for {} ({}, {})", e.incidentId(), e.status(), e.service(), e.type(), e.severity());
        try {
            kafka.send(Topics.INCIDENTS, e.service(), e)
                    .whenComplete((ok, err) -> {
                        if (err != null) {
                            log.warn("Could not publish incident event {}", e.incidentId(), err);
                        }
                    });
        } catch (RuntimeException err) {
            // The incident is already committed; a lost notification must not break detection.
            log.warn("Could not publish incident event {}", e.incidentId(), err);
        }
    }
}
