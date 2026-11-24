package io.sentinelops.analyzer.incident;

import java.util.EnumSet;
import java.util.Set;

public enum IncidentStatus {
    OPEN, ACKNOWLEDGED, RESOLVED;

    /** Statuses that still need attention. */
    public static final Set<IncidentStatus> ACTIVE = EnumSet.of(OPEN, ACKNOWLEDGED);
}
