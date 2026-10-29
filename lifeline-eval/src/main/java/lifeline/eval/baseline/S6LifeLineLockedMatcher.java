package lifeline.eval.baseline;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.PopulationFrequencyTable;
import lifeline.core.domain.ZoneMap;
import lifeline.core.factor.Context;
import lifeline.core.factor.ProximityFactor;
import lifeline.core.factor.RarityFactor;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import lifeline.sim.engine.Matcher;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * System S6: LifeLine Engine Locked Specification.
 * Configured with UrgencyProfile.lockedSpec() (Proximity and Rarity; Responsiveness disabled).
 */
public final class S6LifeLineLockedMatcher implements Matcher {

    private final ZoneMap zoneMap;
    private final UrgencyProfile urgencyProfile;
    private final Ranker ranker;
    private final Duration freshnessWindow;
    private final PopulationFrequencyTable frequencyTable;

    public S6LifeLineLockedMatcher(
        ZoneMap zoneMap,
        UrgencyProfile urgencyProfile,
        Duration freshnessWindow,
        PopulationFrequencyTable frequencyTable
    ) {
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        this.urgencyProfile = Objects.requireNonNull(urgencyProfile, "urgencyProfile cannot be null");
        this.freshnessWindow = Objects.requireNonNull(freshnessWindow, "freshnessWindow cannot be null");
        this.frequencyTable = Objects.requireNonNull(frequencyTable, "frequencyTable cannot be null");
        this.ranker = new Ranker();
    }

    public S6LifeLineLockedMatcher(ZoneMap zoneMap) {
        this(
            zoneMap,
            UrgencyProfile.lockedSpec(),
            Duration.ofHours(24),
            PopulationFrequencyTable.defaultIndianPopulation()
        );
    }

    @Override
    public String name() {
        return "S6_LIFELINE_LOCKED";
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
        List<DonorSnapshot> candidates = eligiblePool.stream()
            .filter(d -> !alreadyContacted.contains(d.id()))
            .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        WeightProfile weights = urgencyProfile.weightsFor(request.urgency());
        Context ctx = new Context(
            zoneMap,
            freshnessWindow,
            currentTime,
            frequencyTable,
            Context.DEFAULT_HALF_DISTANCE_KM,
            Context.DEFAULT_PRIOR_ALPHA,
            Context.DEFAULT_PRIOR_BETA,
            weights,
            Set.of(ProximityFactor.NAME, RarityFactor.NAME)
        );

        List<RankedMatch> ranked = ranker.rank(candidates, request, ctx, 1);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ranked.get(0).donorId());
    }
}
