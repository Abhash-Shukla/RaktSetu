package lifeline.core.explain;

import java.util.Objects;

/**
 * Plain-language explanation for a ranked donor match.
 * Generated deterministically via template logic (no LLM).
 *
 * @param summary                human-readable explanatory text for blood bank staff
 * @param topContributingFactor  name of the factor contributing most significantly to the score
 * @param staleLocationFlag      true if current location was stale and home zone was used
 * @param rareTypeConservedFlag  true if rare-type conservation penalty was applied
 */
public record Explanation(
    String summary,
    String topContributingFactor,
    boolean staleLocationFlag,
    boolean rareTypeConservedFlag
) {
    public Explanation {
        Objects.requireNonNull(summary, "summary cannot be null");
        Objects.requireNonNull(topContributingFactor, "topContributingFactor cannot be null");
    }
}
