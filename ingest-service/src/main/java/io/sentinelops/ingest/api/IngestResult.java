package io.sentinelops.ingest.api;

/** Summary returned to the caller after an ingest request. */
public record IngestResult(int accepted, int duplicates) {
}
