package lifeline.eval.study.sensitivity;

import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.stats.StatisticalComparison;

import java.nio.file.Path;
import java.util.List;

/**
 * Result bundle for Experiment E6 (Weight Sensitivity Study).
 */
public record E6StudyResult(
    Path reportingCsvPath,
    Path tuningSummaryCsvPath,
    Path manifestPath,
    Path statCsvPath,
    Path statReportPath,
    int totalReportingRows,
    int totalSimulations,
    double durationSeconds,
    List<TidyMetricRow> reportingRows,
    List<StatisticalComparison> comparisons
) {
}
