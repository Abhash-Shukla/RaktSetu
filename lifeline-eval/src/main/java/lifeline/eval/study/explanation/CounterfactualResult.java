package lifeline.eval.study.explanation;

/**
 * Result metrics for counterfactual explanation faithfulness tests (Claim C5 / E7).
 * <p>
 * Counterfactual Definition:
 * For an evaluated candidate donor whose explanation highlights a positive top-contributing factor,
 * the counterfactual test ablates (zeros out) that specific factor's contribution while keeping
 * all other contributions and candidates unchanged. The candidates are then re-ranked using the
 * engine's deterministic ranking rule (descending score, tie-breaking by donor ID ascending).
 *
 * @param definition explicit description of the perturbation and counterfactual evaluation protocol
 * @param top1FlipDenominator number of competitive requests evaluated (pool size >= 2, top-1 donor has positive top contribution)
 * @param top1FlipNumerator number of requests where the top-1 candidate was displaced from rank 1 after top-factor ablation
 * @param top1FlipRate fraction of requests exhibiting a counterfactual top-1 flip (numerator / denominator)
 * @param top1MeanRankDrop mean rank drop max(0, rank_counterfactual - 1) specifically for top-1 candidates
 * @param individualShiftDenominator total candidate donors evaluated with a positive top factor contribution
 * @param individualRankChangedCount number of candidate donors whose ranking position changed under counterfactual ablation
 * @param individualRankChangeRate fraction of candidate donors whose rank shifted (individualRankChangedCount / individualShiftDenominator)
 * @param meanRankShift mean absolute shift |rank_counterfactual - rank_original| across all candidates
 * @param meanRankDrop mean rank drop max(0, rank_counterfactual - rank_original) across all candidates
 */
public record CounterfactualResult(
    String definition,
    long top1FlipDenominator,
    long top1FlipNumerator,
    double top1FlipRate,
    double top1MeanRankDrop,
    long individualShiftDenominator,
    long individualRankChangedCount,
    double individualRankChangeRate,
    double meanRankShift,
    double meanRankDrop
) {}

