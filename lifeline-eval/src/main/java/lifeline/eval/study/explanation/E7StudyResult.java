package lifeline.eval.study.explanation;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated experimental result for E7 — Explanation Faithfulness (Claim C5).
 */
public record E7StudyResult(
    Instant timestamp,
    E7Config config,
    ScoreAdditivityResult additivityResult,
    CounterfactualResult counterfactualResult,
    ReasonCodeCoverageResult coverageResult,
    List<SampleExplanation> sampleExplanations
) {
    public boolean overallPassed() {
        return additivityResult.passed() && coverageResult.passed();
    }
}

