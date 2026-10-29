package lifeline.core.rank;

import lifeline.core.explain.Explanation;
import lifeline.core.factor.FactorContribution;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable ranked candidate donor match.
 *
 * @param donorId       unique donor identifier
 * @param score         total score (sum of all factor contributions)
 * @param contributions breakdown of each factor's contribution
 * @param explanation   traceable plain-language explanation and flags
 */
public record RankedMatch(
    String donorId,
    double score,
    List<FactorContribution> contributions,
    Explanation explanation
) {
    public RankedMatch {
        Objects.requireNonNull(donorId, "donorId cannot be null");
        Objects.requireNonNull(contributions, "contributions cannot be null");
        Objects.requireNonNull(explanation, "explanation cannot be null");
        contributions = Collections.unmodifiableList(List.copyOf(contributions));
    }
}
