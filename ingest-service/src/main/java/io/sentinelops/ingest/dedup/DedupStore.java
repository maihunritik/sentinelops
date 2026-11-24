package io.sentinelops.ingest.dedup;

/**
 * Remembers recently seen event ids so that client retries are not counted twice.
 */
public interface DedupStore {

    /**
     * Atomically records the id.
     *
     * @return {@code true} if this is the first time the id was seen inside the TTL window
     */
    boolean markIfFirstSeen(String eventId);
}
