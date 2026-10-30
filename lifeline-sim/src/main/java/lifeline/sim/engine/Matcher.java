package lifeline.sim.engine;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface implemented by candidate selection strategies and baselines (S0 to S8).
 */
public interface Matcher {

    /**
     * Unique identifier for this matching algorithm.
     */
    String name();

    /**
     * Selects the next candidate donor to notify for an active blood request.
     *
     * @param request          target blood requirement
     * @param eligiblePool     available candidates (filtered, outside cooldown, not busy)
     * @param alreadyContacted donor IDs previously contacted for this specific request
     * @return selected candidate donor ID, or empty if none selected
     */
    Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    );

    /**
     * Selects the next candidate donor to notify, passing the current simulation timestamp.
     * Defaults to delegating to {@link #selectNextCandidate(BloodRequest, List, Set)}.
     */
    default Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted,
        java.time.Instant currentTime
    ) {
        return selectNextCandidate(request, eligiblePool, alreadyContacted);
    }
}
