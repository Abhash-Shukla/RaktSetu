package lifeline.eval.study.explanation;

/**
 * Result metrics for the score additivity verification (Claim C5 / SRS EXP-3).
 *
 * @param totalEvaluations total number of scored candidate donors evaluated
 * @param maxAbsoluteError maximum absolute error between match score and sum of contributions (epsilon_add)
 * @param meanAbsoluteError mean absolute error across all evaluated donors
 * @param numericalTolerance strict tolerance threshold applied (e.g. 1.0e-12)
 * @param violationCount count of evaluations where absolute error exceeded tolerance
 * @param passed true if violationCount == 0 and maxAbsoluteError <= numericalTolerance
 */
public record ScoreAdditivityResult(
    long totalEvaluations,
    double maxAbsoluteError,
    double meanAbsoluteError,
    double numericalTolerance,
    long violationCount,
    boolean passed
) {}

