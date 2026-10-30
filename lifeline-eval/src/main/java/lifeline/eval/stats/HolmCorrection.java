package lifeline.eval.stats;

import java.util.*;

/**
 * Holm Step-Down Multiple-Hypothesis Testing Procedure (Holm 1979).
 * <p>
 * Controls the family-wise error rate (FWER) at level alpha while being
 * uniformly more powerful than the single-step Bonferroni correction.
 */
public final class HolmCorrection {

    private HolmCorrection() {
    }

    /**
     * Adjusts a list of raw p-values using the Holm step-down procedure.
     * Preserves original index order in the returned array.
     *
     * @param rawPValues array of raw unadjusted p-values (all must be in [0, 1])
     * @return array of Holm-adjusted p-values in the same order
     */
    public static double[] adjust(double[] rawPValues) {
        Objects.requireNonNull(rawPValues, "rawPValues cannot be null");
        int m = rawPValues.length;
        if (m == 0) {
            return new double[0];
        }
        if (m == 1) {
            return new double[]{Math.min(1.0, Math.max(0.0, rawPValues[0]))};
        }

        // Validate values
        for (int i = 0; i < m; i++) {
            if (Double.isNaN(rawPValues[i]) || rawPValues[i] < 0.0 || rawPValues[i] > 1.0) {
                throw new IllegalArgumentException("p-value at index " + i + " is out of range [0, 1]: " + rawPValues[i]);
            }
        }

        Integer[] indices = new Integer[m];
        for (int i = 0; i < m; i++) {
            indices[i] = i;
        }

        // Sort indices by raw p-value ascending
        Arrays.sort(indices, Comparator.comparingDouble(i -> rawPValues[i]));

        double[] adjustedSorted = new double[m];
        for (int k = 0; k < m; k++) {
            int originalIdx = indices[k];
            // Multiplier for k-th sorted element (1-based index is k + 1): m - (k + 1) + 1 = m - k
            int multiplier = m - k;
            adjustedSorted[k] = multiplier * rawPValues[originalIdx];
        }

        // Enforce monotonicity: adjusted[k] >= adjusted[k - 1]
        for (int k = 1; k < m; k++) {
            adjustedSorted[k] = Math.max(adjustedSorted[k], adjustedSorted[k - 1]);
        }

        // Cap at 1.0 and map back to original indices
        double[] result = new double[m];
        for (int k = 0; k < m; k++) {
            int originalIdx = indices[k];
            result[originalIdx] = Math.min(1.0, Math.max(0.0, adjustedSorted[k]));
        }

        return result;
    }

    /**
     * Determines whether each hypothesis in a family is rejected at significance level alpha.
     *
     * @param rawPValues array of unadjusted p-values
     * @param alpha      family-wise error rate threshold (e.g. 0.05)
     * @return boolean array where true indicates hypothesis is rejected
     */
    public static boolean[] isRejected(double[] rawPValues, double alpha) {
        double[] adjusted = adjust(rawPValues);
        boolean[] rejected = new boolean[adjusted.length];
        for (int i = 0; i < adjusted.length; i++) {
            rejected[i] = adjusted[i] < alpha;
        }
        return rejected;
    }
}

