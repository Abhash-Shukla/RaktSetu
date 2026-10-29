package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.Matcher;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * System S2: Nearest donor.
 * Sees eligibility and distance (ranks strictly by road/zone distance to the hospital zone).
 */
public final class S2NearestDonorMatcher implements Matcher {

    private final ZoneMap zoneMap;

    public S2NearestDonorMatcher(ZoneMap zoneMap) {
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
    }

    @Override
    public String name() {
        return "S2_NEAREST";
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        return eligiblePool.stream()
            .filter(d -> !alreadyContacted.contains(d.id()))
            .min(Comparator
                .comparingDouble((DonorSnapshot d) -> zoneMap.distanceKm(d.homeZone(), request.zone()))
                .thenComparing(DonorSnapshot::id)
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
}
