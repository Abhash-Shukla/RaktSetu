package lifeline.eval.stats;

import java.util.*;

/**
 * Paired Wilcoxon Signed-Rank Test.
 * <p>
 * Non-parametric hypothesis test for paired data across seeds.
 * Handles zero differences (Pratt/Wilcoxon standard exclusion), ties via mid-ranks,
 * and supports exact permutation distribution for N_r &lt;= 20 and normal approximation
 * with continuity and tie corrections for larger samples.
 */
public final class WilcoxonSignedRankTest {

    public static final int EXACT_MAX_N = 20;

    private WilcoxonSignedRankTest() {
    }

    /**
     * Executes a paired Wilcoxon signed-rank test between two paired arrays.
     *
     * @param x first condition values (e.g. S6)
     * @param y second condition values (e.g. S0)
     * @return test outcome with test statistic, p-value, and sample counts
     */
    public static WilcoxonResult test(double[] x, double[] y) {
        return test(x, y, false);
    }

    /**
     * Executes a paired Wilcoxon signed-rank test between two paired arrays.
     *
     * @param x                  first condition values
     * @param y                  second condition values
     * @param forceNormalApprox  true to force asymptotic normal approximation regardless of sample size
     * @return test outcome
     */
    public static WilcoxonResult test(double[] x, double[] y, boolean forceNormalApprox) {
        Objects.requireNonNull(x, "x cannot be null");
        Objects.requireNonNull(y, "y cannot be null");
        if (x.length != y.length) {
            throw new IllegalArgumentException("Paired arrays must have identical length: x=" + x.length + ", y=" + y.length);
        }

        double[] diffs = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            diffs[i] = x[i] - y[i];
        }
        return testDifferences(diffs, forceNormalApprox);
    }

    /**
     * Executes Wilcoxon signed-rank test directly on paired differences (d_i = x_i - y_i).
     *
     * @param diffs              differences between pairs
     * @param forceNormalApprox  true to force normal approximation
     * @return test outcome
     */
    public static WilcoxonResult testDifferences(double[] diffs, boolean forceNormalApprox) {
        Objects.requireNonNull(diffs, "diffs cannot be null");
        int nTotal = diffs.length;

        // 1. Filter out zero differences (standard Wilcoxon 1945 approach)
        List<Double> nonZeroDiffs = new ArrayList<>();
        for (double d : diffs) {
            if (Double.compare(d, 0.0) != 0 && Math.abs(d) > 1e-12) {
                nonZeroDiffs.add(d);
            }
        }

        int nNonZero = nonZeroDiffs.size();
        if (nNonZero == 0) {
            return new WilcoxonResult(0.0, 0.0, 0.0, 0.0, 1.0, nTotal, 0, true);
        }

        // 2. Rank absolute differences using mid-ranks for ties
        double[] absDiffs = new double[nNonZero];
        for (int i = 0; i < nNonZero; i++) {
            absDiffs[i] = Math.abs(nonZeroDiffs.get(i));
        }
        double[] ranks = MathUtils.computeRanks(absDiffs);

        // 3. Compute rank sums W+ and W-
        double wPlus = 0.0;
        double wMinus = 0.0;
        for (int i = 0; i < nNonZero; i++) {
            if (nonZeroDiffs.get(i) > 0.0) {
                wPlus += ranks[i];
            } else {
                wMinus += ranks[i];
            }
        }

        double statistic = Math.min(wPlus, wMinus);

        // 4. Decide exact vs normal approximation
        boolean useExact = !forceNormalApprox && nNonZero <= EXACT_MAX_N;
        if (useExact) {
            double pValue = calculateExactPValue(ranks, statistic);
            return new WilcoxonResult(statistic, wPlus, wMinus, 0.0, pValue, nTotal, nNonZero, true);
        } else {
            // Normal approximation with tie and continuity corrections
            double mean = (nNonZero * (nNonZero + 1.0)) / 4.0;

            // Tie correction: count tie group sizes
            Map<Double, Integer> tieCounts = new HashMap<>();
            for (double ad : absDiffs) {
                tieCounts.merge(ad, 1, Integer::sum);
            }

            double tieCorrection = 0.0;
            for (int t : tieCounts.values()) {
                if (t > 1) {
                    tieCorrection += (Math.pow(t, 3) - t);
                }
            }

            double variance = (nNonZero * (nNonZero + 1.0) * (2.0 * nNonZero + 1.0) - 0.5 * tieCorrection) / 24.0;
            if (variance <= 0.0) {
                return new WilcoxonResult(statistic, wPlus, wMinus, 0.0, 1.0, nTotal, nNonZero, false);
            }

            double stdDev = Math.sqrt(variance);

            // Continuity correction towards the mean
            double z;
            if (wPlus > mean) {
                z = (wPlus - mean - 0.5) / stdDev;
            } else if (wPlus < mean) {
                z = (wPlus - mean + 0.5) / stdDev;
            } else {
                z = 0.0;
            }

            double pValue = 2.0 * (1.0 - MathUtils.standardNormalCdf(Math.abs(z)));
            pValue = Math.min(1.0, Math.max(0.0, pValue));

            return new WilcoxonResult(statistic, wPlus, wMinus, z, pValue, nTotal, nNonZero, false);
        }
    }

    /**
     * Calculates exact two-sided p-value for the rank sum distribution under H0.
     * Mid-ranks are doubled to integer values s_i = 2 * r_i.
     */
    private static double calculateExactPValue(double[] ranks, double statistic) {
        int n = ranks.length;
        int[] scaledRanks = new int[n];
        int sumScaled = 0;
        for (int i = 0; i < n; i++) {
            scaledRanks[i] = (int) Math.round(ranks[i] * 2.0);
            sumScaled += scaledRanks[i];
        }

        int scaledTarget = (int) Math.round(statistic * 2.0);

        // DP array: count combinations yielding sum s
        long[] dp = new long[sumScaled + 1];
        dp[0] = 1L;

        for (int r : scaledRanks) {
            for (int s = sumScaled; s >= r; s--) {
                dp[s] += dp[s - r];
            }
        }

        long totalCombos = 1L << n; // 2^n
        long favorableCombos = 0L;
        for (int s = 0; s <= scaledTarget; s++) {
            favorableCombos += dp[s];
        }

        double p = (2.0 * favorableCombos) / (double) totalCombos;
        return Math.min(1.0, Math.max(0.0, p));
    }
}

