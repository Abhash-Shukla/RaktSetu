package lifeline.eval.study.scalability;

import java.util.Arrays;
import java.util.Objects;

/**
 * Robust numerical percentile and summary statistics calculator for latency distributions.
 */
public final class PercentileCalculator {

    private PercentileCalculator() {}

    /**
     * Calculates the p-th percentile (0 <= p <= 100) using linear interpolation between closest ranks.
     *
     * @param sortedValues array of values sorted in ascending order
     * @param p percentile rank between 0.0 and 100.0
     * @return interpolated percentile value
     */
    public static double percentile(double[] sortedValues, double p) {
        Objects.requireNonNull(sortedValues, "sortedValues cannot be null");
        if (sortedValues.length == 0) {
            throw new IllegalArgumentException("Cannot compute percentile of an empty array");
        }
        if (p < 0.0 || p > 100.0) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100, got " + p);
        }
        if (sortedValues.length == 1) {
            return sortedValues[0];
        }

        double rank = (p / 100.0) * (sortedValues.length - 1);
        int lowIndex = (int) Math.floor(rank);
        int highIndex = (int) Math.ceil(rank);
        double fraction = rank - lowIndex;

        return sortedValues[lowIndex] + fraction * (sortedValues[highIndex] - sortedValues[lowIndex]);
    }

    /**
     * Computes the arithmetic mean of an array of values.
     */
    public static double mean(double[] values) {
        Objects.requireNonNull(values, "values cannot be null");
        if (values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Computes sample standard deviation of an array of values.
     */
    public static double standardDeviation(double[] values, double mean) {
        Objects.requireNonNull(values, "values cannot be null");
        if (values.length <= 1) return 0.0;
        double sumSq = 0.0;
        for (double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (values.length - 1));
    }
}

