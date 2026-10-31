package lifeline.eval.baseline;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.PopulationFrequencyTable;
import lifeline.core.domain.Urgency;
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
 * Matcher for E2 leave-one-component-out ablation study variants per docs/EVALUATION_PROTOCOL.md Section 5.
 * <p>
 * Implements the five approved E2 ablation variants:
 * <ul>
 *   <li>{@code S6_NO_PROXIMITY}: Proximity factor disabled; scored purely by rarity.</li>
 *   <li>{@code S6_NO_RARITY}: Rarity factor disabled; scored purely by proximity.</li>
 *   <li>{@code S6_NO_FRESHNESS}: Infinite freshness window (100 years); blindly trusts reported location.</li>
 *   <li>{@code S6_SINGLE_URGENCY}: Uniform static URGENT weight profile (0.65 proximity / 0.35 rarity) across all urgencies.</li>
 *   <li>{@code S7_NO_RESPONSIVENESS}: Responsiveness disabled; preserves S7's proximity and rarity weight balance.</li>
 * </ul>
 */
public final class E2AblationMatcher implements Matcher {

    public static final Duration DEFAULT_FRESHNESS_WINDOW = Duration.ofHours(24);
    public static final Duration INFINITE_FRESHNESS_WINDOW = Duration.ofDays(36500); // 100 years: blindly trust reported locations

    private final String systemId;
    private final ZoneMap zoneMap;
    private final UrgencyProfile urgencyProfile;
    private final Duration freshnessWindow;
    private final PopulationFrequencyTable frequencyTable;
    private final Set<String> activeFactors;
    private final Ranker ranker;

    public E2AblationMatcher(
        String systemId,
        ZoneMap zoneMap,
        UrgencyProfile urgencyProfile,
        Duration freshnessWindow,
        PopulationFrequencyTable frequencyTable,
        Set<String> activeFactors
    ) {
        this.systemId = Objects.requireNonNull(systemId, "systemId cannot be null");
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        this.urgencyProfile = Objects.requireNonNull(urgencyProfile, "urgencyProfile cannot be null");
        this.freshnessWindow = Objects.requireNonNull(freshnessWindow, "freshnessWindow cannot be null");
        this.frequencyTable = Objects.requireNonNull(frequencyTable, "frequencyTable cannot be null");
        this.activeFactors = Set.copyOf(Objects.requireNonNull(activeFactors, "activeFactors cannot be null"));
        this.ranker = new Ranker();
    }

    /**
     * S6 Ablation 1: No proximity factor. Active factor is RARITY only.
     */
    public static E2AblationMatcher createNoProximity(ZoneMap zoneMap) {
        return new E2AblationMatcher(
            "S6_NO_PROXIMITY",
            zoneMap,
            UrgencyProfile.lockedSpec(),
            DEFAULT_FRESHNESS_WINDOW,
            PopulationFrequencyTable.defaultIndianPopulation(),
            Set.of(RarityFactor.NAME)
        );
    }

    /**
     * S6 Ablation 2: No rarity factor. Active factor is PROXIMITY only.
     */
    public static E2AblationMatcher createNoRarity(ZoneMap zoneMap) {
        return new E2AblationMatcher(
            "S6_NO_RARITY",
            zoneMap,
            UrgencyProfile.lockedSpec(),
            DEFAULT_FRESHNESS_WINDOW,
            PopulationFrequencyTable.defaultIndianPopulation(),
            Set.of(ProximityFactor.NAME)
        );
    }

    /**
     * S6 Ablation 3: No location freshness handling. Uses infinite freshness window so stale coordinates are blindly trusted.
     */
    public static E2AblationMatcher createNoFreshness(ZoneMap zoneMap) {
        return new E2AblationMatcher(
            "S6_NO_FRESHNESS",
            zoneMap,
            UrgencyProfile.lockedSpec(),
            INFINITE_FRESHNESS_WINDOW,
            PopulationFrequencyTable.defaultIndianPopulation(),
            Set.of(ProximityFactor.NAME, RarityFactor.NAME)
        );
    }

    /**
     * S6 Ablation 4: Single urgency profile. Uses the approved URGENT profile (0.65 / 0.35) across CRITICAL, URGENT, ROUTINE.
     */
    public static E2AblationMatcher createSingleUrgency(ZoneMap zoneMap) {
        WeightProfile urgent = UrgencyProfile.lockedSpec().weightsFor(Urgency.URGENT);
        Map<Urgency, WeightProfile> uniformMap = new EnumMap<>(Urgency.class);
        for (Urgency u : Urgency.values()) {
            uniformMap.put(u, urgent);
        }
        return new E2AblationMatcher(
            "S6_SINGLE_URGENCY",
            zoneMap,
            new UrgencyProfile(uniformMap),
            DEFAULT_FRESHNESS_WINDOW,
            PopulationFrequencyTable.defaultIndianPopulation(),
            Set.of(ProximityFactor.NAME, RarityFactor.NAME)
        );
    }

    /**
     * S7 Ablation: No responsiveness factor. Uses S7 extension weights with responsiveness factor disabled.
     */
    public static E2AblationMatcher createS7NoResponsiveness(ZoneMap zoneMap) {
        return new E2AblationMatcher(
            "S7_NO_RESPONSIVENESS",
            zoneMap,
            UrgencyProfile.extensionSpec(),
            DEFAULT_FRESHNESS_WINDOW,
            PopulationFrequencyTable.defaultIndianPopulation(),
            Set.of(ProximityFactor.NAME, RarityFactor.NAME)
        );
    }

    @Override
    public String name() {
        return systemId;
    }

    public String systemId() {
        return systemId;
    }

    public UrgencyProfile urgencyProfile() {
        return urgencyProfile;
    }

    public Duration freshnessWindow() {
        return freshnessWindow;
    }

    public Set<String> activeFactors() {
        return activeFactors;
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
            activeFactors
        );

        List<RankedMatch> ranked = ranker.rank(candidates, request, ctx, 1);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ranked.get(0).donorId());
    }
}

