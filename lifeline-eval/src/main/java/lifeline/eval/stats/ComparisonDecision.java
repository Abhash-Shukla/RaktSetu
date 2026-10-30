package lifeline.eval.stats;

/**
 * Categorical decision outcome according to EVALUATION_PROTOCOL.md Section 6.
 * An improvement is claimed only if Holm-adjusted p &lt; 0.05 AND the effect is non-negligible.
 */
public enum ComparisonDecision {
    IMPROVEMENT,
    DEGRADATION,
    NO_MEANINGFUL_DIFFERENCE,
    INSUFFICIENT_DATA
}

