package lifeline.eval.stats;

import java.util.Arrays;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Bootstrap confidence interval calculator using non-parametric percentile resampling.
 * Uses an injected seeded RandomGenerator for deterministic reproducibility.
 */
public final class BootstrapConfidenceInterval {

    public static final int DEFAULT_BOOTSTRAP_RESAMPLES = 2000;
    public static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;

    /**
     * Minimum sample size required to attempt percentile bootstrap resampling.
     * <p>
     * Methodological Note: While non-parametric percentile bootstrap can mathematically
     * resample for N >= 6, small samples (N < 10) have substantial quantile estimation variance
     * and coverage error (Efron &amp; Tibshirani 1993, Chernick 2007).
     * For pre-registered paper reporting per docs/EVALUATION_PROTOCOL.md, 95% bootstrap CIs
     * must be computed over the full N=30 reporting seeds.
     */
    public static final int MIN_BOOTSTRAP_SAMPLE_SIZE = 6;

    private BootstrapConfidenceInterval() {
    }

    /**
     * Computes bootstrap percentile confidence interval for the sample mean.
     * Requires at least {@value #MIN_BOOTSTRAP_SAMPLE_SIZE} observations.
     * For smaller samples, returns point estimate with NaN confidence limits.
     *
     * @param sample          observed data
     * @param confidenceLevel nominal confidence level (e.g. 0.95)
     * @param numResamples    number of bootstrap iterations B (e.g. 2000)
     * @param seed            random seed for reproducible resampling
     * @return ConfidenceInterval for the mean
     */
    public static ConfidenceInterval bootstrapMean(
        double[] sample,
        double confidenceLevel,
        int numResamples,
        long seed
    ) {
        Objects.requireNonNull(sample, "sample cannot be null");
        if (sample.length < MIN_BOOTSTRAP_SAMPLE_SIZE) {
            double est = sample.length == 0 ? 0.0 : computeMean(sample);
            return new ConfidenceInterval(est, Double.NaN, Double.NaN, confidenceLevel);
        }

        double pointEstimate = computeMean(sample);
        RandomGenerator rng = new java.util.SplittableRandom(seed);
        int n = sample.length;
        double[] bootMeans = new double[numResamples];

        for (int b = 0; b < numResamples; b++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                int idx = rng.nextInt(n);
                sum += sample[idx];
            }
            bootMeans[b] = sum / n;
        }

        Arrays.sort(bootMeans);
        double alpha = 1.0 - confidenceLevel;
        int lowerIdx = (int) Math.floor((alpha / 2.0) * numResamples);
        int upperIdx = (int) Math.ceil((1.0 - alpha / 2.0) * numResamples) - 1;

        lowerIdx = Math.max(0, Math.min(numResamples - 1, lowerIdx));
        upperIdx = Math.max(0, Math.min(numResamples - 1, upperIdx));

        return new ConfidenceInterval(pointEstimate, bootMeans[lowerIdx], bootMeans[upperIdx], confidenceLevel);
    }

    public static ConfidenceInterval bootstrapMean(double[] sample, long seed) {
        return bootstrapMean(sample, DEFAULT_CONFIDENCE_LEVEL, DEFAULT_BOOTSTRAP_RESAMPLES, seed);
    }

    /**
     * Computes bootstrap percentile confidence interval for the sample median.
     *
     * @param sample          observed data
     * @param confidenceLevel nominal confidence level (e.g. 0.95)
     * @param numResamples    number of bootstrap iterations B (e.g. 2000)
     * @param seed            random seed for reproducible resampling
     * @return ConfidenceInterval for the median
     */
    public static ConfidenceInterval bootstrapMedian(
        double[] sample,
        double confidenceLevel,
        int numResamples,
        long seed
    ) {
        Objects.requireNonNull(sample, "sample cannot be null");
        if (sample.length < MIN_BOOTSTRAP_SAMPLE_SIZE) {
            double est = sample.length == 0 ? 0.0 : computeMedian(sample);
            return new ConfidenceInterval(est, Double.NaN, Double.NaN, confidenceLevel);
        }

        double pointEstimate = computeMedian(sample);
        RandomGenerator rng = new java.util.SplittableRandom(seed);
        int n = sample.length;
        double[] bootMedians = new double[numResamples];
        double[] resampleBuffer = new double[n];

        for (int b = 0; b < numResamples; b++) {
            for (int i = 0; i < n; i++) {
                int idx = rng.nextInt(n);
                resampleBuffer[i] = sample[idx];
            }
            bootMedians[b] = computeMedian(resampleBuffer);
        }

        Arrays.sort(bootMedians);
        double alpha = 1.0 - confidenceLevel;
        int lowerIdx = (int) Math.floor((alpha / 2.0) * numResamples);
        int upperIdx = (int) Math.ceil((1.0 - alpha / 2.0) * numResamples) - 1;

        lowerIdx = Math.max(0, Math.min(numResamples - 1, lowerIdx));
        upperIdx = Math.max(0, Math.min(numResamples - 1, upperIdx));

        return new ConfidenceInterval(pointEstimate, bootMedians[lowerIdx], bootMedians[upperIdx], confidenceLevel);
    }

    public static ConfidenceInterval bootstrapMedian(double[] sample, long seed) {
        return bootstrapMedian(sample, DEFAULT_CONFIDENCE_LEVEL, DEFAULT_BOOTSTRAP_RESAMPLES, seed);
    }

    public static double computeMean(double[] data) {
        double sum = 0.0;
        for (double d : data) {
            sum += d;
        }
        return sum / data.length;
    }

    public static double computeMedian(double[] data) {
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 1) {
            return sorted[n / 2];
        } else {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        }
    }

    public static double computeStandardDeviation(double[] data) {
        if (data.length <= 1) {
            return 0.0;
        }
        double mean = computeMean(data);
        double sumSq = 0.0;
        for (double d : data) {
            double diff = d - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (data.length - 1));
    }
}

