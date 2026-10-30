package lifeline.eval.metric;

/**
 * An immutable tidy observation record conforming to the evaluation protocol.
 * (experiment, system, response_model, seed, metric, value)
 */
public record TidyMetricRow(
    String experiment,
    String system,
    String responseModel,
    long seed,
    String metric,
    double value
) {
    public String toCsvLine() {
        return String.format("%s,%s,%s,%d,%s,%.6f",
            experiment, system, responseModel, seed, metric, value
        );
    }
}
