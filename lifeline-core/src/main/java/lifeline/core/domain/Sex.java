package lifeline.core.domain;

/**
 * Biological sex of the donor.
 * <p>
 * Per AGENTS.md Section 1, donor sex is used ONLY to choose the statutory cooldown
 * interval (90 days for men, 120 days for women per NBTC guidelines).
 * It is never used as a ranking or scoring factor.
 */
public enum Sex {
    MALE,
    FEMALE
}
