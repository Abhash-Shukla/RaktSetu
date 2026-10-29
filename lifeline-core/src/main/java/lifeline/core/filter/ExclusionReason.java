package lifeline.core.filter;

/**
 * Machine-readable reason why a candidate donor was excluded from matching.
 */
public enum ExclusionReason {
    /**
     * Donor ABO/Rh blood phenotype is not clinically compatible with the request.
     */
    INCOMPATIBLE_BLOOD_GROUP,

    /**
     * Donor availability flag is set to false.
     */
    UNAVAILABLE,

    /**
     * Donor account is currently paused (e.g., active recipient / patient).
     */
    PAUSED,

    /**
     * Donor has an outstanding notification for another active request and cannot be double-notified.
     */
    OUTSTANDING_NOTIFICATION,

    /**
     * Donor last donated inside the statutory cooldown window (90 days for men, 120 days for women).
     */
    IN_COOLDOWN
}
