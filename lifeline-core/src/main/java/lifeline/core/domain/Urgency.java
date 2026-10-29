package lifeline.core.domain;

/**
 * Urgency tier for a blood requirement.
 * Selects the matching weight profile (CRITICAL, URGENT, ROUTINE).
 */
public enum Urgency {
    CRITICAL,
    URGENT,
    ROUTINE
}
