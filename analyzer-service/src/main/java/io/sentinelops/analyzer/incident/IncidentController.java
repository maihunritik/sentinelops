package io.sentinelops.analyzer.incident;

import io.sentinelops.analyzer.detection.AnomalyDetector;
import io.sentinelops.analyzer.detection.Assessment;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class IncidentController {

    public record AcknowledgeRequest(@NotBlank String acknowledgedBy, String note) {
    }

    private final IncidentService incidents;
    private final AnomalyDetector detector;

    public IncidentController(IncidentService incidents, AnomalyDetector detector) {
        this.incidents = incidents;
        this.detector = detector;
    }

    @GetMapping("/incidents")
    public List<IncidentView> list(@RequestParam(required = false) Set<IncidentStatus> status,
                                   @RequestParam(required = false) String service,
                                   @RequestParam(defaultValue = "50") int limit) {
        return incidents.list(status, service, limit);
    }

    @GetMapping("/incidents/{id}")
    public IncidentView get(@PathVariable long id) {
        return incidents.get(id);
    }

    @PostMapping("/incidents/{id}/acknowledge")
    public IncidentView acknowledge(@PathVariable long id,
                                    @RequestBody @jakarta.validation.Valid AcknowledgeRequest request) {
        return incidents.acknowledge(id, request.acknowledgedBy(), request.note());
    }

    @GetMapping("/services/health")
    public List<Assessment> health() {
        return detector.knownServices().stream().flatMap(s -> detector.peek(s).stream()).toList();
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    ProblemDetail notFound(IncidentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
