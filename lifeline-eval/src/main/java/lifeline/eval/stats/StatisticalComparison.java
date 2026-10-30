package lifeline.eval.stats;

import java.util.Locale;

/**
 * Encapsulates the complete statistical evaluation between a candidate system (e.g. S6)
 * and a baseline system (e.g. S0, S2, S3, S4, S5) across paired seeds.
 */
public record StatisticalComparison(
    String experiment,
    EvaluationDatasetType datasetType,
    String responseModel,
    String candidateSystem,
    String baselineSystem,
    String metric,
    MetricDirection direction,
    int sampleSize,
    double candidateMean,
    double candidateMeanCiLower,
    double candidateMeanCiUpper,
    double candidateMedian,
    double baselineMean,
    double baselineMeanCiLower,
    double baselineMeanCiUpper,
    double baselineMedian,
    double hodgesLehmannShift,
    double cliffsDelta,
    EffectSizeCalculator.Magnitude cliffsMagnitude,
    double wilcoxonStatistic,
    double rawPValue,
    double holmPValue,
    int wins,
    int losses,
    int ties,
    ComparisonDecision decision
) {
    public static String csvHeader() {
        return "experiment,dataset_type,response_model,candidate_system,baseline_system,metric,direction,seeds," +
            "candidate_mean,candidate_ci_lower,candidate_ci_upper,candidate_median," +
            "baseline_mean,baseline_ci_lower,baseline_ci_upper,baseline_median," +
            "hodges_lehmann_shift,cliffs_delta,cliffs_magnitude,wilcoxon_stat,raw_p,holm_p," +
            "wins,losses,ties,decision";
    }

    public String toCsvLine() {
        return String.format(Locale.ROOT,
            "%s,%s,%s,%s,%s,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%.2f,%.6f,%.6f,%d,%d,%d,%s",
            experiment,
            datasetType,
            responseModel,
            candidateSystem,
            baselineSystem,
            metric,
            direction,
            sampleSize,
            candidateMean,
            candidateMeanCiLower,
            candidateMeanCiUpper,
            candidateMedian,
            baselineMean,
            baselineMeanCiLower,
            baselineMeanCiUpper,
            baselineMedian,
            hodgesLehmannShift,
            cliffsDelta,
            cliffsMagnitude,
            wilcoxonStatistic,
            rawPValue,
            holmPValue,
            wins,
            losses,
            ties,
            decision
        );
    }
}

