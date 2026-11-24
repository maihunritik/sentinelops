package io.sentinelops.analyzer.incident;

public class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException(long id) {
        super("Incident " + id + " not found");
    }
}
