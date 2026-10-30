package lifeline.eval.stats;

/**
 * Result of a paired Wilcoxon signed-rank test.
 *
 * @param statistic test statistic W = min(W+, W-)
 * @param wPlus     sum of ranks for positive differences
 * @param wMinus    sum of ranks for negative differences
 * @param zScore    standardized score (0.0 if exact)
 * @param pValue    two-sided p-value
 * @param nTotal    total number of paired samples
 * @param nNonZero  number of non-zero differences (effective sample size Nr)
 * @param exact     true if calculated via exact distribution, false if normal approximation
 */
public record WilcoxonResult(
    double statistic,
    double wPlus,
    double wMinus,
    double zScore,
    double pValue,
    int nTotal,
    int nNonZero,
    boolean exact
) {
}

