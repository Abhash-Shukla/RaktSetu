package lifeline.eval.stats;

/**
 * Distinguishes the evaluation purpose and experimental status of a dataset
 * strictly per docs/EVALUATION_PROTOCOL.md Section 6.
 * <ul>
 *   <li>{@link #FINAL_REPORTING}: Evaluated on all 30 reporting seeds (1-30). Pre-registered paper conclusions.</li>
 *   <li>{@link #INCOMPLETE_REPORTING}: Evaluated on a strict subset of reporting seeds (1-30, count &lt; 30).</li>
 *   <li>{@link #EXPLORATORY_TUNING}: Evaluated on tuning seeds (&gt;= 1000). Exploratory analysis only;
 *       must not be cited as pre-registered paper findings.</li>
 * </ul>
 */
public enum EvaluationDatasetType {
    FINAL_REPORTING("Final Protocol Reporting (Seeds 1-30)"),
    INCOMPLETE_REPORTING("Incomplete Reporting (Subset of Seeds 1-30)"),
    EXPLORATORY_TUNING("Exploratory / Tuning (Seeds 1000+)");

    private final String description;

    EvaluationDatasetType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}

