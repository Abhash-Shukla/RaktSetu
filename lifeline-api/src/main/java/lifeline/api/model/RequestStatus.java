package lifeline.api.model;

public enum RequestStatus {
    PENDING_REVIEW,
    OPEN,
    RESOLVED_TIER0,
    FULFILLED,
    EXHAUSTED,
    CANCELLED,
    REJECTED
}
