package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.sim.engine.Matcher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System S0: FCFS / broadcast to all eligible compatible donors.
 * Sees eligibility only; selects the first eligible donor in registration/pool order.
 */
public final class S0FcfsMatcher implements Matcher {

    @Override
    public String name() {
        return "S0_FCFS";
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        return eligiblePool.stream()
            .map(DonorSnapshot::id)
            .filter(id -> !alreadyContacted.contains(id))
            .findFirst();
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
}
