package lifeline.core.config;

/**
 * Normalised factor weight profile for scoring donor candidates.
 *
 * @param proximity      weight for proximity/distance term (higher = closer donors favored)
 * @param rarity         weight for rarity conservation term (higher = rare types conserved)
 * @param responsiveness weight for historical responsiveness term (0.0 in locked spec)
 */
public record WeightProfile(
    double proximity,
    double rarity,
    double responsiveness
) {
    public WeightProfile {
        if (proximity < 0.0 || rarity < 0.0 || responsiveness < 0.0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        double sum = proximity + rarity + responsiveness;
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalArgumentException("Weight profile terms must sum to 1.0, got: " + sum);
        }
    }
}
