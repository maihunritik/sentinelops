package io.sentinelops.common;

/** Kafka topic names shared by every service. */
public final class Topics {

    /** Raw request telemetry published by the ingest service, keyed by service name. */
    public static final String TELEMETRY = "sentinel.telemetry.v1";

    /** Incident lifecycle events (opened / resolved) published by the analyzer. */
    public static final String INCIDENTS = "sentinel.incidents.v1";

    private Topics() {
    }
}
