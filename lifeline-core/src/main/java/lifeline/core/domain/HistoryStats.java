package lifeline.core.domain;

import java.time.Instant;

/**
 * Observable historical outreach statistics for a donor.
 * Computed strictly from recorded simulation or production events.
 *
 * @param contacts      total notifications sent to this donor
 * @param accepts       number of requests accepted
 * @param declines      number of requests declined
 * @param noResponses   number of requests expired without response
 * @param lastContactAt timestamp of the most recent notification, or null if never contacted
 */
public record HistoryStats(
    int contacts,
    int accepts,
    int declines,
    int noResponses,
    Instant lastContactAt
) {
    public HistoryStats {
        if (contacts < 0 || accepts < 0 || declines < 0 || noResponses < 0) {
            throw new IllegalArgumentException("History counts cannot be negative");
        }
    }

    /**
     * Default empty statistics for a newly registered donor.
     */
    public static HistoryStats empty() {
        return new HistoryStats(0, 0, 0, 0, null);
    }
}
