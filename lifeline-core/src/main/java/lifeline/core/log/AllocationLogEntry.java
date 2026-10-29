package lifeline.core.log;

import lifeline.core.explain.Explanation;
import lifeline.core.factor.FactorContribution;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable audit entry in the allocation event stream.
 * Serves as the source of truth for persistent match logs.
 *
 * @param requestId           target blood request ID
 * @param step                monotonic step count within this request's outreach sequence
 * @param donorId             donor evaluated or contacted (or null if request-level event like EXHAUSTED)
 * @param score               composite score assigned to this candidate
 * @param factorContributions breakdown of each factor's contribution
 * @param explanation         plain-language explanation and clinical flags
 * @param outcome             event outcome (e.g. NOTIFIED, DECLINED, EXHAUSTED)
 * @param timestamp           event recording time
 */
public record AllocationLogEntry(
    String requestId,
    int step,
    String donorId,
    double score,
    List<FactorContribution> factorContributions,
    Explanation explanation,
    AllocationOutcome outcome,
    Instant timestamp
) {
    public AllocationLogEntry {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(outcome, "outcome cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        factorContributions = factorContributions != null
            ? Collections.unmodifiableList(List.copyOf(factorContributions))
            : List.of();
    }
}
