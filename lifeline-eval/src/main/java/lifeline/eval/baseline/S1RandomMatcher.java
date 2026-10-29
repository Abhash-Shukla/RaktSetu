package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.sim.engine.Matcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * System S1: Random selection among eligible compatible donors.
 * Sees eligibility only.
 */
public final class S1RandomMatcher implements Matcher {

    private final RandomGenerator rng;

    public S1RandomMatcher(RandomGenerator rng) {
        this.rng = Objects.requireNonNull(rng, "rng cannot be null");
    }

    @Override
    public String name() {
        return "S1_RANDOM";
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        List<String> candidates = new ArrayList<>();
        for (DonorSnapshot d : eligiblePool) {
            if (!alreadyContacted.contains(d.id())) {
                candidates.add(d.id());
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int idx = rng.nextInt(candidates.size());
        return Optional.of(candidates.get(idx));
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
