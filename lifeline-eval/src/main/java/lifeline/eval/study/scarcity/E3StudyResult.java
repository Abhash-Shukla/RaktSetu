package lifeline.eval.study.scarcity;

import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.stats.StatisticalComparison;

import java.nio.file.Path;
import java.util.List;

/**
 * Result bundle for the E3 Scarcity Sweep Study.
 */
public record E3StudyResult(
    Path csvPath,
    Path manifestPath,
    Path statCsvPath,
    Path statReportPath,
    int totalRows,
    int totalSimulations,
    int worldGenerations,
    double durationSeconds,
    List<TidyMetricRow> rows,
    List<StatisticalComparison> comparisons
) {
}

