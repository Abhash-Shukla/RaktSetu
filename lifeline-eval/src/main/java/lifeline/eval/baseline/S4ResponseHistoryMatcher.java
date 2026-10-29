package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.HistoryStats;
import lifeline.sim.engine.Matcher;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System S4: Response-history-only baseline.
 * Ranks strictly by observable historical acceptance probability (smoothed with Beta(1, 2) prior).
 * Does not use location, distance, or blood-group rarity.
 */
public final class S4ResponseHistoryMatcher implements Matcher {

    @Override
    public String name() {
        return "S4_HISTORY_ONLY";
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        return eligiblePool.stream()
            .filter(d -> !alreadyContacted.contains(d.id()))
            .max(Comparator
                .comparingDouble((DonorSnapshot d) -> computeSmoothedAcceptanceRate(d.historyStats()))
                .thenComparing(DonorSnapshot::id, Comparator.reverseOrder())
            )
            .map(DonorSnapshot::id);
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted,
        Instant currentTime
    ) {
        return selectNextCandidate(request, eligiblePool, alreadyContacted);
    }

    /**
     * Smoothed empirical acceptance rate with Beta(alpha=1, beta=2) prior per P-23.
     */
    public static double computeSmoothedAcceptanceRate(HistoryStats stats) {
        if (stats == null) {
            return 1.0 / 3.0;
        }
        return (stats.accepts() + 1.0) / (stats.contacts() + 3.0);
    }
}
