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
import lifeline.core.factor.ResponsivenessFactor;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import lifeline.sim.engine.Matcher;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * System S7: LifeLine Engine with Responsiveness Extension (toggle enabled).
 * Configured with UrgencyProfile.extensionSpec() (Proximity, Rarity, and Responsiveness).
 */
public final class S7LifeLineExtensionMatcher implements Matcher {

    private final ZoneMap zoneMap;
    private final UrgencyProfile urgencyProfile;
    private final Ranker ranker;
    private final Duration freshnessWindow;
    private final PopulationFrequencyTable frequencyTable;

    public S7LifeLineExtensionMatcher(
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

    public S7LifeLineExtensionMatcher(ZoneMap zoneMap) {
        this(
            zoneMap,
            UrgencyProfile.extensionSpec(),
            Duration.ofHours(24),
            PopulationFrequencyTable.defaultIndianPopulation()
        );
    }

    @Override
    public String name() {
        return "S7_LIFELINE_EXTENSION";
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
            Set.of(ProximityFactor.NAME, RarityFactor.NAME, ResponsivenessFactor.NAME)
        );

        List<RankedMatch> ranked = ranker.rank(candidates, request, ctx, 1);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ranked.get(0).donorId());
    }
}
