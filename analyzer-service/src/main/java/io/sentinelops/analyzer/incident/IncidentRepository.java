package io.sentinelops.analyzer.incident;

import io.sentinelops.analyzer.detection.AnomalyType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByServiceAndTypeAndStatusIn(String service, AnomalyType type,
                                                             Collection<IncidentStatus> statuses);

    List<Incident> findByStatusInOrderByOpenedAtDesc(Collection<IncidentStatus> statuses, Pageable page);

    List<Incident> findByServiceAndStatusInOrderByOpenedAtDesc(String service, Collection<IncidentStatus> statuses,
                                                               Pageable page);
}
