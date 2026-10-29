package lifeline.eval.runner;

import lifeline.eval.metric.TidyMetricRow;

import java.nio.file.Path;
import java.util.List;

/**
 * Encapsulates the output artifacts and execution summary of an {@link ExperimentRunner} execution.
 */
public record ExperimentRunResult(
    String experiment,
    Path csvPath,
    Path manifestPath,
    int totalMetricRows,
    int totalSimulationRuns,
    double durationSeconds,
    List<TidyMetricRow> rows
) {
}
