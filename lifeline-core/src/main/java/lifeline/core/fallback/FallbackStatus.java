package lifeline.core.fallback;

/**
 * Status resulting from a fallback coordination step.
 */
public enum FallbackStatus {
    NEXT_MATCH,
    FULFILLED,
    EXHAUSTED
}
