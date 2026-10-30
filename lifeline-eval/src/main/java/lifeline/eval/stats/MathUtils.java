package lifeline.eval.stats;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Common mathematical and statistical utility routines.
 */
public final class MathUtils {

    private MathUtils() {
    }

    /**
     * Approximates the error function erf(x) using the Abramowitz and Stegun (1964) formula 7.1.26.
     * Maximum error is strictly less than 1.5e-7.
     *
     * @param x input value
     * @return erf(x)
     */
    public static double erf(double x) {
        if (Double.isNaN(x)) {
            return Double.NaN;
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? 1.0 : -1.0;
        }
        if (x == 0.0) {
            return 0.0;
        }

        double sign = x < 0 ? -1.0 : 1.0;
        double absX = Math.abs(x);

        // Constants from Abramowitz & Stegun 7.1.26
        final double p = 0.3275911;
        final double a1 = 0.254829592;
        final double a2 = -0.284496736;
        final double a3 = 1.421413741;
        final double a4 = -1.453152027;
        final double a5 = 1.061405429;

        double t = 1.0 / (1.0 + p * absX);
        double poly = (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t;
        double result = 1.0 - poly * Math.exp(-absX * absX);

        return sign * result;
    }

    /**
     * Standard normal cumulative distribution function Phi(z).
     *
     * @param z standard score
     * @return probability P(Z <= z)
     */
    public static double standardNormalCdf(double z) {
        if (Double.isNaN(z)) {
            return Double.NaN;
        }
        if (z < -8.0) {
            return 0.0;
        }
        if (z > 8.0) {
            return 1.0;
        }
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    /**
     * Computes 1-based mid-ranks for an array of values, properly assigning average ranks to tied values.
     *
     * @param values input values (must not be null)
     * @return array of assigned ranks corresponding to each element in input order
     */
    public static double[] computeRanks(double[] values) {
        int n = values.length;
        if (n == 0) {
            return new double[0];
        }

        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }

        // Sort indices according to values
        Arrays.sort(indices, Comparator.comparingDouble(i -> values[i]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            // Find all elements tied with values[indices[i]]
            while (j < n - 1 && Double.compare(values[indices[j]], values[indices[j + 1]]) == 0) {
                j++;
            }

            // Sum of 1-based ranks from i + 1 to j + 1
            double midRank = (i + 1 + j + 1) / 2.0;
            for (int k = i; k <= j; k++) {
                ranks[indices[k]] = midRank;
            }
            i = j + 1;
        }

        return ranks;
    }
}

