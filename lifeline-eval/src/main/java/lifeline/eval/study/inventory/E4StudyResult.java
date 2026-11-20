package lifeline.eval.study.inventory;

import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.stats.StatisticalComparison;

import java.nio.file.Path;
import java.util.List;

/**
 * Result bundle for the E4 Inventory-Level Sweep Study.
 */
public record E4StudyResult(
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
