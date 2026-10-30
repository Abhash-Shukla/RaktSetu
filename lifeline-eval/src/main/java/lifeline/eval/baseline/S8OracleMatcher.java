package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.Matcher;
import lifeline.sim.oracle.Oracle;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * System S8: Upper-bound Oracle.
 * Reads ground-truth acceptance probabilities through the simulation {@link Oracle}
 * and ranks candidates by descending true probability.
 */
public final class S8OracleMatcher implements Matcher {

    private final Oracle oracle;
    private final ZoneMap zoneMap;

    public S8OracleMatcher(Oracle oracle, ZoneMap zoneMap) {
        this.oracle = Objects.requireNonNull(oracle, "oracle cannot be null");
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
    }

    @Override
    public String name() {
        return "S8_ORACLE";
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        return selectNextCandidate(request, eligiblePool, alreadyContacted, request.createdAt());
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted,
        Instant currentTime
    ) {
        double timeOfDayHour = currentTime.atZone(ZoneOffset.UTC).getHour()
            + currentTime.atZone(ZoneOffset.UTC).getMinute() / 60.0;

        return eligiblePool.stream()
            .filter(d -> !alreadyContacted.contains(d.id()))
            .max(Comparator
                .comparingDouble((DonorSnapshot d) -> {
                    double distKm = zoneMap.distanceKm(d.homeZone(), request.zone());
                    return oracle.trueAcceptanceProbability(d.id(), request, distKm, 0, timeOfDayHour);
                })
                .thenComparing(DonorSnapshot::id, Comparator.reverseOrder())
            )
            .map(DonorSnapshot::id);
    }
}
