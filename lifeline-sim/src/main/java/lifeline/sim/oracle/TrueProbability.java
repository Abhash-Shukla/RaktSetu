package lifeline.sim.oracle;

/**
 * Value object encapsulating the ground-truth acceptance probability.
 * Accessible ONLY via the {@link Oracle} interface for evaluation baselines and metric computation.
 */
public record TrueProbability(double value) {
    public TrueProbability {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("TrueProbability value must be in [0.0, 1.0], got: " + value);
        }
    }
}
