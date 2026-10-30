package lifeline.eval.stats;

import java.util.*;

/**
 * Non-parametric effect size calculations:
 * <ul>
 *   <li>Hodges-Lehmann estimator of location shift for paired observations.</li>
 *   <li>Cliff's delta non-parametric effect size and magnitude classification.</li>
 * </ul>
 */
public final class EffectSizeCalculator {

    public enum Magnitude {
        NEGLIGIBLE,
        SMALL,
        MEDIUM,
        LARGE
    }

    public record CliffsDeltaResult(double delta, Magnitude magnitude) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.4f (%s)", delta, magnitude);
        }
    }

    private EffectSizeCalculator() {
    }

    /**
     * Calculates the Hodges-Lehmann pseudo-median difference for paired samples (x_i, y_i).
     * Computes the median of all Walsh pairwise averages (d_i + d_j) / 2 where d = x - y.
     *
     * @param x first condition values
     * @param y second condition values
     * @return Hodges-Lehmann location shift estimate
     */
    public static double hodgesLehmannShift(double[] x, double[] y) {
        Objects.requireNonNull(x, "x cannot be null");
        Objects.requireNonNull(y, "y cannot be null");
        if (x.length != y.length) {
            throw new IllegalArgumentException("Paired arrays must have identical length");
        }
        int n = x.length;
        if (n == 0) {
            return 0.0;
        }

        double[] diffs = new double[n];
        for (int i = 0; i < n; i++) {
            diffs[i] = x[i] - y[i];
        }
        return hodgesLehmannShiftFromDifferences(diffs);
    }

    /**
     * Calculates the Hodges-Lehmann pseudo-median directly from an array of differences.
     *
     * @param diffs array of differences
     * @return Hodges-Lehmann median of Walsh averages
     */
    public static double hodgesLehmannShiftFromDifferences(double[] diffs) {
        Objects.requireNonNull(diffs, "diffs cannot be null");
        int n = diffs.length;
        if (n == 0) {
            return 0.0;
        }

        int totalAverages = (n * (n + 1)) / 2;
        double[] walshAverages = new double[totalAverages];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                walshAverages[idx++] = (diffs[i] + diffs[j]) / 2.0;
            }
        }

        Arrays.sort(walshAverages);
        if (totalAverages % 2 == 1) {
            return walshAverages[totalAverages / 2];
        } else {
            return (walshAverages[totalAverages / 2 - 1] + walshAverages[totalAverages / 2]) / 2.0;
        }
    }

    /**
     * Calculates Cliff's delta between two distributions x and y.
     *
     * @param x sample from first distribution
     * @param y sample from second distribution
     * @return Cliff's delta result with magnitude classification
     */
    public static CliffsDeltaResult cliffsDelta(double[] x, double[] y) {
        Objects.requireNonNull(x, "x cannot be null");
        Objects.requireNonNull(y, "y cannot be null");
        if (x.length == 0 || y.length == 0) {
            return new CliffsDeltaResult(0.0, Magnitude.NEGLIGIBLE);
        }

        long countGreater = 0;
        long countLesser = 0;

        for (double xi : x) {
            for (double yj : y) {
                if (xi > yj) {
                    countGreater++;
                } else if (xi < yj) {
                    countLesser++;
                }
            }
        }

        double delta = (double) (countGreater - countLesser) / ((double) x.length * y.length);
        Magnitude mag = classifyMagnitude(delta);
        return new CliffsDeltaResult(delta, mag);
    }

    /**
     * Classifies Cliff's delta magnitude according to Romano et al. (2006).
     * |delta| &lt; 0.147: negligible
     * 0.147 &lt;= |delta| &lt; 0.330: small
     * 0.330 &lt;= |delta| &lt; 0.474: medium
     * |delta| &gt;= 0.474: large
     */
    public static Magnitude classifyMagnitude(double delta) {
        if (Double.isNaN(delta)) {
            return Magnitude.NEGLIGIBLE;
        }
        double absD = Math.abs(delta);
        if (absD < 0.147) {
            return Magnitude.NEGLIGIBLE;
        } else if (absD < 0.330) {
            return Magnitude.SMALL;
        } else if (absD < 0.474) {
            return Magnitude.MEDIUM;
        } else {
            return Magnitude.LARGE;
        }
    }
}

