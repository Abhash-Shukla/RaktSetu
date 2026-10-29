package lifeline.eval.baseline;

import lifeline.core.domain.*;
import lifeline.eval.metric.EvaluationMetrics;
import lifeline.eval.metric.MetricCalculator;
import lifeline.sim.engine.Matcher;
import lifeline.sim.engine.SimulationEvent;
import lifeline.sim.engine.SimulationEventType;
import lifeline.sim.engine.SimulationResult;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.oracle.SimulatedOracle;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.response.ResponseModel;
import lifeline.sim.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineMatcherTest {

    private ZoneMap zoneMap;
    private BloodRequest testRequest;
    private List<DonorSnapshot> pool;
    private Instant now;
    private Oracle oracle;
    private World world;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-29T12:00:00Z");
        zoneMap = new ZoneMap(Map.of(
            "Z1", new GeoCoordinate(0.0, 0.0),
            "Z2", new GeoCoordinate(10.0, 0.0),
            "Z3", new GeoCoordinate(50.0, 0.0)
        ), 1.3);

        testRequest = new BloodRequest(
            "REQ-01",
            BloodGroup.O_POS,
            1,
            Urgency.CRITICAL,
            "Z1",
            now,
            now.plus(Duration.ofHours(2))
        );

        DonorSnapshot d1 = new DonorSnapshot("DONOR-1", BloodGroup.O_POS, Sex.MALE, "Z1", LocalDate.now().minusDays(150), true, null, new HistoryStats(5, 4, 1, 0, now.minus(Duration.ofDays(10))));
        DonorSnapshot d2 = new DonorSnapshot("DONOR-2", BloodGroup.O_POS, Sex.MALE, "Z2", LocalDate.now().minusDays(200), true, null, new HistoryStats(2, 0, 2, 0, now.minus(Duration.ofDays(20))));
        DonorSnapshot d3 = new DonorSnapshot("DONOR-3", BloodGroup.O_POS, Sex.FEMALE, "Z3", LocalDate.now().minusDays(300), true, null, new HistoryStats(10, 1, 9, 0, now.minus(Duration.ofDays(30))));

        pool = List.of(d1, d2, d3);

        Map<String, LatentDonor> latentMap = new HashMap<>();
        latentMap.put("DONOR-1", new LatentDonor(d1, 0.8, 0.95, 20.0, 15.0, 0.8, 0.0));
        latentMap.put("DONOR-2", new LatentDonor(d2, 0.3, 0.80, 40.0, 10.0, 0.8, 0.0));
        latentMap.put("DONOR-3", new LatentDonor(d3, 0.1, 0.75, 60.0, 5.0, 0.8, 0.0));

        SimulationParameters params = SimulationParameters.loadDefault();
        ResponseModel rm = new M1BaselineLogisticModel(params);
        oracle = new SimulatedOracle(latentMap, rm);
        world = new World(zoneMap, latentMap, List.of(), params, now);
    }

    @Test
    @DisplayName("Baselines S0-S8 return only donors present in eligiblePool and not in alreadyContacted")
    void allBaselinesReturnOnlyFilteredPoolDonors() {
        SplittableRandom rng = new SplittableRandom(42L);
        List<String> systems = List.of("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8");
        Set<String> poolIds = Set.of("DONOR-1", "DONOR-2", "DONOR-3");

        for (String sysId : systems) {
            Matcher matcher = BaselineMatcherFactory.create(sysId, world, oracle, rng, 42L);

            // First contact: should pick someone from pool
            Optional<String> firstPick = matcher.selectNextCandidate(testRequest, pool, Collections.emptySet(), now);
            assertThat(firstPick).isPresent();
            assertThat(firstPick.get()).isIn(poolIds);

            // Second contact with first excluded: must NOT pick first
            Set<String> alreadyContacted = Set.of(firstPick.get());
            Optional<String> secondPick = matcher.selectNextCandidate(testRequest, pool, alreadyContacted, now);
            assertThat(secondPick).isPresent();
            assertThat(secondPick.get()).isIn(poolIds);
            assertThat(secondPick.get()).isNotEqualTo(firstPick.get());

            // All contacted: must return empty
            Set<String> allContacted = Set.of("DONOR-1", "DONOR-2", "DONOR-3");
            Optional<String> emptyPick = matcher.selectNextCandidate(testRequest, pool, allContacted, now);
            assertThat(emptyPick).isEmpty();
        }
    }

    @Test
    @DisplayName("S3 never uses history: ranker score and selection are invariant to historyStats changes")
    void s3NeverUsesHistory() {
        S3RuleBasedMatcher matcher = new S3RuleBasedMatcher(zoneMap);

        DonorSnapshot donorZeroHistory = new DonorSnapshot(
            "DONOR-A", BloodGroup.O_POS, Sex.MALE, "Z1",
            LocalDate.of(2026, 1, 1), true, null,
            HistoryStats.empty()
        );

        DonorSnapshot donorLotsOfHistory = new DonorSnapshot(
            "DONOR-A", BloodGroup.O_POS, Sex.MALE, "Z1",
            LocalDate.of(2026, 1, 1), true, null,
            new HistoryStats(50, 45, 5, 0, now.minus(Duration.ofDays(1)))
        );

        LocalDate evalDate = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        double score1 = matcher.computeRuleScore(donorZeroHistory, testRequest, evalDate);
        double score2 = matcher.computeRuleScore(donorLotsOfHistory, testRequest, evalDate);

        // Scores MUST be exactly identical because historyStats is not a feature in S3
        assertThat(score1)
            .as("S3 score must be identical regardless of historyStats")
            .isEqualTo(score2);

        // Selection among candidates should be completely invariant to modifying historyStats
        DonorSnapshot donorOther = new DonorSnapshot(
            "DONOR-B", BloodGroup.O_POS, Sex.MALE, "Z2",
            LocalDate.of(2026, 1, 1), true, null,
            new HistoryStats(100, 100, 0, 0, now)
        );

        Optional<String> pick1 = matcher.selectNextCandidate(testRequest, List.of(donorZeroHistory, donorOther), Collections.emptySet(), now);
        Optional<String> pick2 = matcher.selectNextCandidate(testRequest, List.of(donorLotsOfHistory, donorOther), Collections.emptySet(), now);

        assertThat(pick1).isEqualTo(pick2);
    }

    @Test
    @DisplayName("Oracle regret is 0 for the oracle baseline S8")
    void oracleRegretIsZeroForOracle() {
        S8OracleMatcher oracleMatcher = new S8OracleMatcher(oracle, zoneMap);

        Optional<String> chosenId = oracleMatcher.selectNextCandidate(testRequest, pool, Collections.emptySet(), now);
        assertThat(chosenId).isPresent();

        // Simulate event log with the Oracle's choice as the first notification
        List<SimulationEvent> events = List.of(
            new SimulationEvent(now, SimulationEventType.REQUEST_ARRIVED, testRequest.id(), null, 0, "Arrival"),
            new SimulationEvent(now, SimulationEventType.DONOR_NOTIFIED, testRequest.id(), chosenId.get(), 1, "Notified"),
            new SimulationEvent(now.plus(Duration.ofMinutes(15)), SimulationEventType.DONOR_ACCEPTED, testRequest.id(), chosenId.get(), 1, "Accepted"),
            new SimulationEvent(now.plus(Duration.ofMinutes(15)), SimulationEventType.REQUEST_FULFILLED, testRequest.id(), null, 1, "Fulfilled")
        );

        SimulationResult result = new SimulationResult(events, 1, 1, 0, 1, 1, 0, 0, Duration.ofMinutes(15));
        EvaluationMetrics metrics = MetricCalculator.calculate(result, List.of(testRequest), world, oracle);

        // Oracle ranking regret must be strictly 0.0
        assertThat(metrics.rankingRegret())
            .as("Oracle regret must be 0 for the oracle baseline")
            .isEqualTo(0.0);

        // Oracle NDCG gap must be strictly 0.0
        assertThat(metrics.ndcgGap())
            .as("Oracle NDCG gap must be 0 for the oracle baseline")
            .isEqualTo(0.0);
    }
}
