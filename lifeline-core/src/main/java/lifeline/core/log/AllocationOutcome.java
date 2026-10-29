package lifeline.core.log;

/**
 * Outcome status recorded for an allocation event.
 */
public enum AllocationOutcome {
    NOTIFIED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    EXHAUSTED,
    DONATED,
    RESOLVED_TIER0
}
