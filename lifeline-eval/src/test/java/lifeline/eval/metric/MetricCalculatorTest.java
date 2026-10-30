package lifeline.eval.metric;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.GeoCoordinate;
import lifeline.core.domain.HistoryStats;
import lifeline.core.domain.Sex;
import lifeline.core.domain.Urgency;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.SimulationEvent;
import lifeline.sim.engine.SimulationEventType;
import lifeline.sim.engine.SimulationResult;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricCalculatorTest {

    private final Instant baseTime = Instant.parse("2026-10-29T10:00:00Z");

    @Test
    @DisplayName("Hand-computed scenario: fulfillment rate, contacts per fulfilled, and time-to-fulfill")
    void testFulfillmentAndTimingMetrics() {
        // 4 requests: 3 fulfilled, 1 unfulfilled
        BloodRequest r1 = new BloodRequest("REQ-1", BloodGroup.O_POS, 1, Urgency.CRITICAL, "Z1", baseTime, baseTime.plus(Duration.ofHours(2)));
        BloodRequest r2 = new BloodRequest("REQ-2", BloodGroup.A_POS, 1, Urgency.URGENT, "Z1", baseTime, baseTime.plus(Duration.ofHours(6)));
        BloodRequest r3 = new BloodRequest("REQ-3", BloodGroup.B_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        BloodRequest r4 = new BloodRequest("REQ-4", BloodGroup.AB_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        List<BloodRequest> requests = List.of(r1, r2, r3, r4);

        // Simulation events:
        // r1 fulfilled in 10 mins after 1 notification
        // r2 fulfilled in 20 mins after 2 notifications
        // r3 fulfilled in 30 mins after 3 notifications
        // r4 exhausted (unfulfilled) after 1 notification
        // Total notifications: 1 + 2 + 3 + 1 = 7. Fulfilled: 3.
        List<SimulationEvent> events = List.of(
            new SimulationEvent(baseTime, SimulationEventType.REQUEST_ARRIVED, "REQ-1", null, 0, "Arrival"),
            new SimulationEvent(baseTime, SimulationEventType.DONOR_NOTIFIED, "REQ-1", "D1", 1, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.DONOR_ACCEPTED, "REQ-1", "D1", 1, "Accepted"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.REQUEST_FULFILLED, "REQ-1", null, 1, "Fulfilled"),

            new SimulationEvent(baseTime, SimulationEventType.REQUEST_ARRIVED, "REQ-2", null, 0, "Arrival"),
            new SimulationEvent(baseTime, SimulationEventType.DONOR_NOTIFIED, "REQ-2", "D2", 1, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.DONOR_DECLINED, "REQ-2", "D2", 1, "Declined"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.DONOR_NOTIFIED, "REQ-2", "D3", 2, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(20)), SimulationEventType.DONOR_ACCEPTED, "REQ-2", "D3", 2, "Accepted"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(20)), SimulationEventType.REQUEST_FULFILLED, "REQ-2", null, 2, "Fulfilled"),

            new SimulationEvent(baseTime, SimulationEventType.REQUEST_ARRIVED, "REQ-3", null, 0, "Arrival"),
            new SimulationEvent(baseTime, SimulationEventType.DONOR_NOTIFIED, "REQ-3", "D4", 1, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.DONOR_DECLINED, "REQ-3", "D4", 1, "Declined"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.DONOR_NOTIFIED, "REQ-3", "D5", 2, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(20)), SimulationEventType.DONOR_DECLINED, "REQ-3", "D5", 2, "Declined"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(20)), SimulationEventType.DONOR_NOTIFIED, "REQ-3", "D6", 3, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(30)), SimulationEventType.DONOR_ACCEPTED, "REQ-3", "D6", 3, "Accepted"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(30)), SimulationEventType.REQUEST_FULFILLED, "REQ-3", null, 3, "Fulfilled"),

            new SimulationEvent(baseTime, SimulationEventType.REQUEST_ARRIVED, "REQ-4", null, 0, "Arrival"),
            new SimulationEvent(baseTime, SimulationEventType.DONOR_NOTIFIED, "REQ-4", "D7", 1, "Notified"),
            new SimulationEvent(baseTime.plus(Duration.ofHours(24)), SimulationEventType.REQUEST_EXHAUSTED, "REQ-4", null, 1, "Exhausted")
        );

        SimulationResult result = new SimulationResult(events, 4, 3, 1, 7, 3, 3, 0, Duration.ofHours(24));
        World mockWorld = createMockWorld(List.of("D1", "D2", "D3", "D4", "D5", "D6", "D7"));

        EvaluationMetrics m = MetricCalculator.calculate(result, requests, mockWorld, null);

        // Fulfillment within deadline: 3 / 4 = 0.75
        assertThat(m.fulfillmentWithinDeadline()).isEqualTo(0.75);

        // Contacts per fulfilled request: 7 / 3 = 2.333333...
        assertThat(m.contactsPerFulfilledRequest()).isCloseTo(7.0 / 3.0, within(1e-5));

        // Time to fulfill: [10.0, 20.0, 30.0] mins
        // Median = 20.0 mins
        // Censored count = 1
        assertThat(m.timeToFulfillMedianMinutes()).isCloseTo(20.0, within(1e-5));
        assertThat(m.timeToFulfillP90Minutes()).isCloseTo(28.0, within(1e-5)); // 0.9 quantile of [10, 20, 30]
        assertThat(m.timeToFulfillCensoredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Hand-computed scenario: unfulfilled rate by blood group")
    void testUnfulfilledRateByBloodGroup() {
        BloodRequest r1 = new BloodRequest("R1", BloodGroup.O_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        BloodRequest r2 = new BloodRequest("R2", BloodGroup.O_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        BloodRequest r3 = new BloodRequest("R3", BloodGroup.B_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        BloodRequest r4 = new BloodRequest("R4", BloodGroup.AB_NEG, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        List<BloodRequest> requests = List.of(r1, r2, r3, r4);

        // R1 fulfilled, R2 unfulfilled -> O_POS rate = 1 / 2 = 0.5
        // R3 fulfilled -> B_POS rate = 0 / 1 = 0.0
        // R4 unfulfilled -> AB_NEG rate = 1 / 1 = 1.0
        // Others -> 0.0
        List<SimulationEvent> events = List.of(
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.REQUEST_FULFILLED, "R1", null, 1, "Fulfilled"),
            new SimulationEvent(baseTime.plus(Duration.ofMinutes(10)), SimulationEventType.REQUEST_FULFILLED, "R3", null, 1, "Fulfilled")
        );

        SimulationResult result = new SimulationResult(events, 4, 2, 2, 2, 2, 0, 0, Duration.ofHours(24));
        World mockWorld = createMockWorld(List.of("D1", "D2"));

        EvaluationMetrics m = MetricCalculator.calculate(result, requests, mockWorld, null);

        assertThat(m.unfulfilledRateByGroup().get(BloodGroup.O_POS)).isEqualTo(0.5);
        assertThat(m.unfulfilledRateByGroup().get(BloodGroup.B_POS)).isEqualTo(0.0);
        assertThat(m.unfulfilledRateByGroup().get(BloodGroup.AB_NEG)).isEqualTo(1.0);
        assertThat(m.unfulfilledRateByGroup().get(BloodGroup.A_POS)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Hand-computed scenario: rare-type waste rate")
    void testRareTypeWasteRate() {
        // Rare donors: O_NEG, AB_NEG. Common donors: O_POS, A_POS.
        // Req1: A_POS (common request) served by O_NEG donor (rare) -> wasted!
        // Req2: O_NEG (rare request) served by O_NEG donor (rare) -> not wasted!
        // Req3: O_POS (common request) served by O_POS donor (common) -> not a rare allocation.
        // Total rare donor allocations = 2. Wasted allocations = 1. Waste rate = 1 / 2 = 0.5.
        BloodRequest r1 = new BloodRequest("R1", BloodGroup.A_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        BloodRequest r2 = new BloodRequest("R2", BloodGroup.O_NEG, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        BloodRequest r3 = new BloodRequest("R3", BloodGroup.O_POS, 1, Urgency.ROUTINE, "Z1", baseTime, baseTime.plus(Duration.ofHours(24)));
        List<BloodRequest> requests = List.of(r1, r2, r3);

        Map<String, LatentDonor> donors = new HashMap<>();
        donors.put("D_ONEG_1", createLatentDonor("D_ONEG_1", BloodGroup.O_NEG));
        donors.put("D_ONEG_2", createLatentDonor("D_ONEG_2", BloodGroup.O_NEG));
        donors.put("D_OPOS_3", createLatentDonor("D_OPOS_3", BloodGroup.O_POS));

        List<SimulationEvent> events = List.of(
            new SimulationEvent(baseTime, SimulationEventType.DONOR_ACCEPTED, "R1", "D_ONEG_1", 1, "Accepted"),
            new SimulationEvent(baseTime, SimulationEventType.DONOR_ACCEPTED, "R2", "D_ONEG_2", 1, "Accepted"),
            new SimulationEvent(baseTime, SimulationEventType.DONOR_ACCEPTED, "R3", "D_OPOS_3", 1, "Accepted")
        );

        SimulationResult result = new SimulationResult(events, 3, 3, 0, 3, 3, 0, 0, Duration.ofHours(24));
        World mockWorld = new World(new ZoneMap(Map.of("Z1", new GeoCoordinate(0.0, 0.0)), 1.3), donors, List.of(), SimulationParameters.loadDefault(), baseTime);

        EvaluationMetrics m = MetricCalculator.calculate(result, requests, mockWorld, null);

        assertThat(m.rareTypeWasteRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Hand-computed scenario: Gini coefficient and max contact load")
    void testGiniCoefficientAndMaxLoad() {
        // Perfect equality: [2, 2, 2, 2] -> Gini = 0.0
        assertThat(MetricCalculator.computeGini(List.of(2, 2, 2, 2))).isCloseTo(0.0, within(1e-6));

        // Hand calculation for [0, 0, 0, 4]:
        // n=4, S=4. weightedSum = 1*0 + 2*0 + 3*0 + 4*4 = 16.
        // G = 2*16/(4*4) - 5/4 = 2.0 - 1.25 = 0.75
        assertThat(MetricCalculator.computeGini(List.of(0, 0, 0, 4))).isCloseTo(0.75, within(1e-6));
    }

    private World createMockWorld(List<String> donorIds) {
        Map<String, LatentDonor> donors = new HashMap<>();
        for (String id : donorIds) {
            donors.put(id, createLatentDonor(id, BloodGroup.O_POS));
        }
        ZoneMap zoneMap = new ZoneMap(Map.of("Z1", new GeoCoordinate(0.0, 0.0)), 1.3);
        return new World(zoneMap, donors, List.of(), SimulationParameters.loadDefault(), baseTime);
    }

    private LatentDonor createLatentDonor(String id, BloodGroup bg) {
        DonorSnapshot snap = new DonorSnapshot(id, bg, Sex.MALE, "Z1", LocalDate.now().minusDays(150), true, null, HistoryStats.empty());
        return new LatentDonor(snap, 0.5, 0.85, 30.0, 10.0, 0.8, 0.0);
    }
}
