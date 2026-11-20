package lifeline.eval.baseline;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.factor.ProximityFactor;
import lifeline.core.factor.RarityFactor;
import lifeline.sim.engine.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class E2AblationMatcherTest {

    private ZoneMap zoneMap;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-30T12:00:00Z");
        zoneMap = new ZoneMap(Map.of(
            "Z1", new GeoCoordinate(0.0, 0.0),
            "Z2", new GeoCoordinate(10.0, 0.0),
            "Z3", new GeoCoordinate(50.0, 0.0)
        ), 1.3);
    }

    @Test
    @DisplayName("S6_NO_PROXIMITY: only RARITY is active; distance does not affect candidate scores")
    void testNoProximityAblation() {
        E2AblationMatcher matcher = E2AblationMatcher.createNoProximity(zoneMap);

        assertThat(matcher.name()).isEqualTo("S6_NO_PROXIMITY");
        assertThat(matcher.activeFactors()).containsExactly(RarityFactor.NAME);

        BloodRequest req = new BloodRequest(
            "REQ-1", BloodGroup.O_POS, 1, Urgency.URGENT, "Z1", now, now.plus(Duration.ofHours(6))
        );

        // Two O+ donors: d1 is at Z1 (dist 0), d2 is at Z3 (dist 50).
        // Since proximity is disabled and both are exact match (rarity penalty = 0.0),
        // both score 0.0 and tie-break goes to donor ID ascending (D1 before D2).
        DonorSnapshot d1 = new DonorSnapshot("D1", BloodGroup.O_POS, Sex.MALE, "Z1", LocalDate.now().minusDays(150), true, null, HistoryStats.empty());
        DonorSnapshot d2 = new DonorSnapshot("D2", BloodGroup.O_POS, Sex.MALE, "Z3", LocalDate.now().minusDays(150), true, null, HistoryStats.empty());

        Optional<String> pick = matcher.selectNextCandidate(req, List.of(d2, d1), Collections.emptySet(), now);
        assertThat(pick).contains("D1");

        // Swap order in pool: result must remain D1 by deterministic tie-breaking
        Optional<String> pick2 = matcher.selectNextCandidate(req, List.of(d1, d2), Collections.emptySet(), now);
        assertThat(pick2).contains("D1");
    }

    @Test
    @DisplayName("S6_NO_RARITY: only PROXIMITY is active; rare donor is not penalized when non-exact match")
    void testNoRarityAblation() {
        E2AblationMatcher matcher = E2AblationMatcher.createNoRarity(zoneMap);

        assertThat(matcher.name()).isEqualTo("S6_NO_RARITY");
        assertThat(matcher.activeFactors()).containsExactly(ProximityFactor.NAME);

        // Request for A+ at Z1
        BloodRequest req = new BloodRequest(
            "REQ-A", BloodGroup.A_POS, 1, Urgency.URGENT, "Z1", now, now.plus(Duration.ofHours(6))
        );

        // Both donors at Z1 (distance 0).
        // D1_RARE is O- (universal rare type, non-exact compatible for A+)
        DonorSnapshot dRare = new DonorSnapshot("D1_RARE", BloodGroup.O_NEG, Sex.MALE, "Z1", LocalDate.now().minusDays(150), true, null, HistoryStats.empty());
        // D2_EXACT is A+ (exact match for A+)
        DonorSnapshot dExact = new DonorSnapshot("D2_EXACT", BloodGroup.A_POS, Sex.MALE, "Z1", LocalDate.now().minusDays(150), true, null, HistoryStats.empty());

        // In normal S6:
        // D2_EXACT has proximity 0.65 and rarity 0.0 -> score = 0.65
        // D1_RARE has proximity 0.65 and rarity -0.34 -> score = 0.31
        // D2_EXACT wins!
        Matcher normalS6 = BaselineMatcherFactory.create("S6", dummyWorld(), null, new SplittableRandom(42L), 42L);
        Optional<String> normalPick = normalS6.selectNextCandidate(req, List.of(dRare, dExact), Collections.emptySet());
        assertThat(normalPick).contains("D2_EXACT");

        // In S6_NO_RARITY:
        // Rarity penalty is inactive. Both donors score proximity 0.65.
        // Tie-break by donor ID ascending: "D1_RARE" < "D2_EXACT" -> D1_RARE wins!
        Optional<String> ablatedPick = matcher.selectNextCandidate(req, List.of(dRare, dExact), Collections.emptySet());
        assertThat(ablatedPick).contains("D1_RARE");
    }

    @Test
    @DisplayName("S6_NO_FRESHNESS: blindly trusts reported location; stale location behaves differently from S6")
    void testNoFreshnessAblation() {
        E2AblationMatcher ablatedMatcher = E2AblationMatcher.createNoFreshness(zoneMap);
        assertThat(ablatedMatcher.freshnessWindow()).isEqualTo(E2AblationMatcher.INFINITE_FRESHNESS_WINDOW);

        BloodRequest req = new BloodRequest(
            "REQ-1", BloodGroup.O_POS, 1, Urgency.URGENT, "Z1", now, now.plus(Duration.ofHours(6))
        );

        // Donor with homeZone Z3 (far, 50km), but a STALE reported location at Z1 (confirmed 48 hours ago, older than 24h)
        CurrentLocation staleLocAtZ1 = new CurrentLocation("Z1", now.minus(Duration.ofHours(48)));
        DonorSnapshot dStale = new DonorSnapshot("D_STALE", BloodGroup.O_POS, Sex.MALE, "Z3", LocalDate.now().minusDays(150), true, staleLocAtZ1, HistoryStats.empty());

        // Donor at homeZone Z2 (closer, 10km) with no reported location
        DonorSnapshot dHomeZ2 = new DonorSnapshot("D_HOME_Z2", BloodGroup.O_POS, Sex.MALE, "Z2", LocalDate.now().minusDays(150), true, (CurrentLocation) null, HistoryStats.empty());

        List<DonorSnapshot> pool = List.of(dStale, dHomeZ2);

        // Under normal S6:
        // dStale's location is 48h old > 24h freshness window -> reverted to homeZone Z3 (50km away).
        // dHomeZ2 is at Z2 (10km away).
        // Therefore, normal S6 selects dHomeZ2!
        Matcher normalS6 = BaselineMatcherFactory.create("S6", dummyWorld(), null, new SplittableRandom(42L), 42L);
        Optional<String> normalPick = normalS6.selectNextCandidate(req, pool, Collections.emptySet());
        assertThat(normalPick).contains("D_HOME_Z2");

        // Under S6_NO_FRESHNESS:
        // Freshness check is bypassed (infinite window). dStale is blindly evaluated as being at Z1 (distance 0km).
        // Distance 0km beats distance 10km.
        // Therefore, S6_NO_FRESHNESS selects dStale!
        Optional<String> ablatedPick = ablatedMatcher.selectNextCandidate(req, pool, Collections.emptySet());
        assertThat(ablatedPick).contains("D_STALE");
    }

    @Test
    @DisplayName("Freshness ablation preserves simulator ground truth: engine is fooled by stale location but simulator evaluates true homeZone distance")
    void testFreshnessAblationDoesNotCorruptSimulatorGroundTruth() {
        E2AblationMatcher ablatedMatcher = E2AblationMatcher.createNoFreshness(zoneMap);

        BloodRequest req = new BloodRequest(
            "REQ-1", BloodGroup.O_POS, 1, Urgency.URGENT, "Z1", now, now.plus(Duration.ofHours(6))
        );

        // Donor with homeZone Z3 (50km from Z1), but STALE reported location at Z1 (confirmed 48 hours ago)
        CurrentLocation staleLocAtZ1 = new CurrentLocation("Z1", now.minus(Duration.ofHours(48)));
        DonorSnapshot dStale = new DonorSnapshot("D_STALE", BloodGroup.O_POS, Sex.MALE, "Z3", LocalDate.now().minusDays(150), true, staleLocAtZ1, HistoryStats.empty());

        // 1. Engine Ranking Behavior: S6_NO_FRESHNESS blindly treats dStale as being at Z1
        Optional<String> enginePick = ablatedMatcher.selectNextCandidate(req, List.of(dStale), Collections.emptySet());
        assertThat(enginePick).contains("D_STALE");

        // 2. Simulator Ground Truth Behavior: verify SimulationRun computes true physical location
        lifeline.sim.model.SimulationParameters params = lifeline.sim.model.SimulationParameters.loadDefault();
        Duration age = Duration.between(staleLocAtZ1.confirmedAt(), now);
        boolean simulatorIsFresh = !age.isNegative() && age.compareTo(Duration.ofHours(params.freshnessWindowHours())) <= 0;

        // Ground truth strictly verifies age (48h) > freshnessWindowHours (24h)
        assertThat(simulatorIsFresh).isFalse();

        // Simulator ground truth places donor at homeZone Z3 (50km away)
        String simulatorTrueZone = simulatorIsFresh ? staleLocAtZ1.zone() : dStale.homeZone();
        assertThat(simulatorTrueZone).isEqualTo("Z3");

        double trueDistanceKm = zoneMap.distanceKm(simulatorTrueZone, req.zone());
        assertThat(trueDistanceKm).isEqualTo(zoneMap.distanceKm("Z3", "Z1"));
        assertThat(trueDistanceKm).isGreaterThan(0.0);

        // Verify response model evaluates true distance (50km), producing lower acceptance than if at Z1 (0km)
        lifeline.sim.model.LatentDonor latent = new lifeline.sim.model.LatentDonor(dStale, 0.8, 0.95, 20.0, 15.0, 0.8, 0.0);
        lifeline.sim.response.ResponseModel rm = new lifeline.sim.response.M1BaselineLogisticModel(params);

        double pAtTrueDistance = rm.trueAcceptanceProbability(latent, req, trueDistanceKm, 0, 12.0);
        double pAtReportedDistance = rm.trueAcceptanceProbability(latent, req, 0.0, 0, 12.0);

        // True acceptance probability must be strictly lower due to true physical distance
        assertThat(pAtTrueDistance).isLessThan(pAtReportedDistance);
    }

    @Test
    @DisplayName("S6_SINGLE_URGENCY: applies uniform URGENT profile (0.65 proximity / 0.35 rarity) across all urgencies")
    void testSingleUrgencyAblation() {
        E2AblationMatcher matcher = E2AblationMatcher.createSingleUrgency(zoneMap);

        UrgencyProfile profile = matcher.urgencyProfile();
        WeightProfile criticalW = profile.weightsFor(Urgency.CRITICAL);
        WeightProfile urgentW = profile.weightsFor(Urgency.URGENT);
        WeightProfile routineW = profile.weightsFor(Urgency.ROUTINE);

        // All three urgency tiers MUST use the exact approved URGENT profile (0.65 / 0.35 / 0.0)
        assertThat(criticalW.proximity()).isEqualTo(0.65);
        assertThat(criticalW.rarity()).isEqualTo(0.35);
        assertThat(criticalW.responsiveness()).isEqualTo(0.0);

        assertThat(urgentW.proximity()).isEqualTo(0.65);
        assertThat(urgentW.rarity()).isEqualTo(0.35);
        assertThat(urgentW.responsiveness()).isEqualTo(0.0);

        assertThat(routineW.proximity()).isEqualTo(0.65);
        assertThat(routineW.rarity()).isEqualTo(0.35);
        assertThat(routineW.responsiveness()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("S7_NO_RESPONSIVENESS: active factors are PROXIMITY and RARITY; preserves S7 extension weight profiles")
    void testS7NoResponsivenessAblation() {
        E2AblationMatcher matcher = E2AblationMatcher.createS7NoResponsiveness(zoneMap);

        assertThat(matcher.name()).isEqualTo("S7_NO_RESPONSIVENESS");
        assertThat(matcher.activeFactors()).containsExactlyInAnyOrder(ProximityFactor.NAME, RarityFactor.NAME);

        // Preserves S7's extension weight profiles
        UrgencyProfile profile = matcher.urgencyProfile();
        WeightProfile criticalW = profile.weightsFor(Urgency.CRITICAL);
        WeightProfile urgentW = profile.weightsFor(Urgency.URGENT);
        WeightProfile routineW = profile.weightsFor(Urgency.ROUTINE);

        assertThat(criticalW.proximity()).isEqualTo(0.60);
        assertThat(criticalW.rarity()).isEqualTo(0.10);
        assertThat(criticalW.responsiveness()).isEqualTo(0.30); // Note: factor is inactive so this weight is uncomputed

        assertThat(urgentW.proximity()).isEqualTo(0.45);
        assertThat(urgentW.rarity()).isEqualTo(0.25);
        assertThat(urgentW.responsiveness()).isEqualTo(0.30);

        assertThat(routineW.proximity()).isEqualTo(0.30);
        assertThat(routineW.rarity()).isEqualTo(0.45);
        assertThat(routineW.responsiveness()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("BaselineMatcherFactory creates all five E2 ablation matchers correctly")
    void testFactoryCreation() {
        SplittableRandom rng = new SplittableRandom(42L);

        List<String> ablationIds = List.of(
            "S6_NO_PROXIMITY",
            "S6_NO_RARITY",
            "S6_NO_FRESHNESS",
            "S6_SINGLE_URGENCY",
            "S7_NO_RESPONSIVENESS"
        );

        for (String id : ablationIds) {
            Matcher m = BaselineMatcherFactory.create(id, dummyWorld(), null, rng, 42L);
            assertThat(m).isNotNull();
            assertThat(m.name()).isEqualTo(id);
        }
    }

    private lifeline.sim.world.World dummyWorld() {
        return new lifeline.sim.world.World(
            zoneMap,
            Collections.emptyMap(),
            Collections.emptyList(),
            lifeline.sim.model.SimulationParameters.loadDefault(),
            now
        );
    }
}
