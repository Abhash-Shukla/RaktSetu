package lifeline.core.fallback;

import lifeline.core.rank.RankedMatch;

import java.util.Objects;

/**
 * Immutable outcome of an outreach step in the fallback coordinator.
 *
 * @param status        outcome status (NEXT_MATCH, FULFILLED, or EXHAUSTED)
 * @param currentMatch  active match surfaced, or null if exhausted
 * @param attemptNumber current outreach attempt sequence number
 * @param message       descriptive status explanation
 */
public record FallbackResult(
    FallbackStatus status,
    RankedMatch currentMatch,
    int attemptNumber,
    String message
) {
    public FallbackResult {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public boolean isExhausted() {
        return status == FallbackStatus.EXHAUSTED;
    }

    public boolean isFulfilled() {
        return status == FallbackStatus.FULFILLED;
    }

    public boolean hasNextMatch() {
        return status == FallbackStatus.NEXT_MATCH;
    }

    public static FallbackResult nextMatch(RankedMatch match, int attempt) {
        return new FallbackResult(
            FallbackStatus.NEXT_MATCH,
            match,
            attempt,
            "Surfaced next candidate: " + match.donorId()
        );
    }

    public static FallbackResult fulfilled(RankedMatch match, int attempts) {
        return new FallbackResult(
            FallbackStatus.FULFILLED,
            match,
            attempts,
            "Requirement fulfilled by donor: " + match.donorId()
        );
    }

    public static FallbackResult exhausted(int attempts, String reason) {
        return new FallbackResult(
            FallbackStatus.EXHAUSTED,
            null,
            attempts,
            reason
        );
    }
}
