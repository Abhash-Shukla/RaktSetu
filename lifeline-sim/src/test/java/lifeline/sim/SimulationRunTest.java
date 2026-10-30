package lifeline.sim;

import lifeline.core.domain.*;
import lifeline.sim.engine.Matcher;
import lifeline.sim.engine.SimulationEvent;
import lifeline.sim.engine.SimulationEventType;
import lifeline.sim.engine.SimulationResult;
import lifeline.sim.engine.SimulationRun;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.response.ResponseModel;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRunTest {

    /**
     * Simple deterministic first-eligible candidate matcher for testing.
     */
    private static final Matcher FIRST_ELIGIBLE_MATCHER = new Matcher() {
        @Override
        public String name() {
            return "FIRST_ELIGIBLE";
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
    };

    @Test
    @DisplayName("Same seed produces identical event log: timestamps, event types, donor IDs, and attempt counts")
    void sameSeedProducesIdenticalEventLog() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        Instant start = world.referenceTime();
        List<BloodRequest> requests = List.of(
            new BloodRequest("REQ-01", BloodGroup.O_POS, 1, Urgency.CRITICAL, "Z01", start, start.plus(Duration.ofHours(2))),
            new BloodRequest("REQ-02", BloodGroup.A_POS, 2, Urgency.URGENT, "Z02", start.plus(Duration.ofMinutes(15)), start.plus(Duration.ofHours(6))),
            new BloodRequest("REQ-03", BloodGroup.B_POS, 1, Urgency.ROUTINE, "Z03", start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofHours(24)))
        );

        ResponseModel model = new M1BaselineLogisticModel(params);
        SimulationRun run1 = new SimulationRun();
        SimulationRun run2 = new SimulationRun();

        SimulationResult res1 = run1.run(world, FIRST_ELIGIBLE_MATCHER, model, requests, 12345L);
        SimulationResult res2 = run2.run(world, FIRST_ELIGIBLE_MATCHER, model, requests, 12345L);

        assertThat(res1.eventLog()).hasSize(res2.eventLog().size());
        for (int i = 0; i < res1.eventLog().size(); i++) {
            SimulationEvent e1 = res1.eventLog().get(i);
            SimulationEvent e2 = res2.eventLog().get(i);

            assertThat(e1.timestamp()).isEqualTo(e2.timestamp());
            assertThat(e1.type()).isEqualTo(e2.type());
            assertThat(e1.requestId()).isEqualTo(e2.requestId());
            assertThat(e1.donorId()).isEqualTo(e2.donorId());
            assertThat(e1.attemptNumber()).isEqualTo(e2.attemptNumber());
            assertThat(e1.message()).isEqualTo(e2.message());
        }

        assertThat(res1.fulfilledRequests()).isEqualTo(res2.fulfilledRequests());
        assertThat(res1.unfulfilledRequests()).isEqualTo(res2.unfulfilledRequests());
        assertThat(res1.totalNotificationsSent()).isEqualTo(res2.totalNotificationsSent());
    }

    @Test
    @DisplayName("Unfulfilled requests occur under tight attempt budgets (no trivial 100% fulfillment)")
    void unfulfilledRequestsOccurUnderTightBudgets() {
        // Budget = 1 contact only
        SimulationParameters tightBudgetParams = SimulationParameters.loadDefault().withAttemptBudget(1);

        WorldGenerator gen = new WorldGenerator(tightBudgetParams);
        World world = gen.generate(42L);

        Instant start = world.referenceTime();
        List<BloodRequest> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requests.add(new BloodRequest(
                "REQ-" + i,
                BloodGroup.O_NEG, // rare type
                2, // requires 2 units
                Urgency.CRITICAL,
                "Z01",
                start.plus(Duration.ofMinutes(i * 10)),
                start.plus(Duration.ofMinutes(i * 10 + 120))
            ));
        }

        ResponseModel model = new M1BaselineLogisticModel(tightBudgetParams);
        SimulationRun run = new SimulationRun();
        SimulationResult result = run.run(world, FIRST_ELIGIBLE_MATCHER, model, requests, 999L);

        assertThat(result.unfulfilledRequests())
            .as("Under attempt budget of 1 and 2 units required, unfulfilled requests must occur")
            .isGreaterThan(0);

        assertThat(result.fulfillmentRate()).isLessThan(1.0);
    }

    @Test
    @DisplayName("Busy donor enforcement: active notified donors are not offered to concurrent requests")
    void busyDonorEnforcement() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        Instant start = world.referenceTime();
        // Two requests arriving at the exact same instant for the same blood group
        List<BloodRequest> concurrentRequests = List.of(
            new BloodRequest("REQ-A", BloodGroup.AB_NEG, 1, Urgency.CRITICAL, "Z01", start, start.plus(Duration.ofHours(2))),
            new BloodRequest("REQ-B", BloodGroup.AB_NEG, 1, Urgency.CRITICAL, "Z01", start, start.plus(Duration.ofHours(2)))
        );

        ResponseModel model = new M1BaselineLogisticModel(params);
        SimulationRun run = new SimulationRun();
        SimulationResult result = run.run(world, FIRST_ELIGIBLE_MATCHER, model, concurrentRequests, 42L);

        // Find the first notified donor for REQ-A and REQ-B
        String donorForA = null;
        String donorForB = null;

        for (SimulationEvent event : result.eventLog()) {
            if (event.type() == SimulationEventType.DONOR_NOTIFIED) {
                if ("REQ-A".equals(event.requestId()) && donorForA == null) {
                    donorForA = event.donorId();
                } else if ("REQ-B".equals(event.requestId()) && donorForB == null) {
                    donorForB = event.donorId();
                }
            }
        }

        if (donorForA != null && donorForB != null) {
            assertThat(donorForA)
                .as("Two concurrent requests dispatched at same instant must not notify the same busy donor")
                .isNotEqualTo(donorForB);
        }
    }

    @Test
    @DisplayName("Notification expiry: donor responses exceeding expiry threshold result in NOTIFICATION_EXPIRED")
    void notificationExpiryEnforced() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        Instant start = world.referenceTime();
        List<BloodRequest> requests = List.of(
            new BloodRequest("REQ-EXP", BloodGroup.O_POS, 1, Urgency.CRITICAL, "Z01", start, start.plus(Duration.ofHours(2)))
        );

        // Model that forces a long response delay (120 min), exceeding CRITICAL 20 min expiry
        ResponseModel slowModel = new ResponseModel() {
            @Override
            public String name() {
                return "SLOW_EXPIRY_TEST";
            }

            @Override
            public double trueAcceptanceProbability(lifeline.sim.model.LatentDonor donor, BloodRequest req, double dist, int contacts, double tod) {
                return 1.0;
            }

            @Override
            public Duration drawResponseDelay(lifeline.sim.model.LatentDonor donor, java.util.random.RandomGenerator rng) {
                return Duration.ofMinutes(120);
            }
        };

        SimulationRun run = new SimulationRun();
        SimulationResult result = run.run(world, FIRST_ELIGIBLE_MATCHER, slowModel, requests, 42L);

        boolean foundExpired = result.eventLog().stream()
            .anyMatch(e -> e.type() == SimulationEventType.NOTIFICATION_EXPIRED);

        assertThat(foundExpired)
            .as("Delay of 120m for critical request must trigger NOTIFICATION_EXPIRED (threshold 20m)")
            .isTrue();
    }
}
