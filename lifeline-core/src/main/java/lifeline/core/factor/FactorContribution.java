package lifeline.core.factor;

import java.util.Objects;

/**
 * Traceable breakdown of a single factor's contribution to a candidate donor's ranking score.
 * <p>
 * Under SRS EXP-1 and EXP-3:
 * {@code contribution == rawValue * weight}, and contributions sum exactly to the final score.
 *
 * @param factorName   identifier of the factor (e.g., "PROXIMITY", "RARITY")
 * @param rawValue     unweighted raw score or metric computed by the factor
 * @param weight       urgency-derived weight applied to this factor
 * @param contribution weighted contribution (weight * rawValue)
 * @param reasonCode   machine-readable reason or flag code (e.g. "STALE_LOCATION", "RARE_TYPE_CONSERVED")
 */
public record FactorContribution(
    String factorName,
    double rawValue,
    double weight,
    double contribution,
    String reasonCode
) {
    public FactorContribution {
        Objects.requireNonNull(factorName, "factorName cannot be null");
        Objects.requireNonNull(reasonCode, "reasonCode cannot be null");
    }

    /**
     * Constructs a contribution, strictly computing {@code contribution = rawValue * weight}.
     */
    public static FactorContribution of(String factorName, double rawValue, double weight, String reasonCode) {
        return new FactorContribution(factorName, rawValue, weight, rawValue * weight, reasonCode);
    }
}
