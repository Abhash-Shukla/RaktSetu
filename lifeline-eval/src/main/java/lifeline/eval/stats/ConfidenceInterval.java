package lifeline.eval.stats;

import java.util.Locale;

/**
 * Immutable confidence interval holding point estimate, lower and upper bounds, and confidence level.
 */
public record ConfidenceInterval(
    double estimate,
    double lower,
    double upper,
    double confidenceLevel
) {
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.4f [%.4f, %.4f]", estimate, lower, upper);
    }
}

