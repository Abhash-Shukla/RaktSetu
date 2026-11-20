package lifeline.eval.study.scarcity;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.Urgency;
import lifeline.eval.metric.EvaluationMetrics;
import lifeline.eval.metric.MetricCalculator;
import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.stats.MetricDirection;
import lifeline.sim.engine.SimulationEvent;
import lifeline.sim.engine.SimulationEventType;
import lifeline.sim.engine.SimulationResult;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import lifeline.sim.model.SimulationParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class E3MetricsTest {

    @Test
    @DisplayName("MetricDirection maps rare_request_fulfillment and common_request_fulfillment to HIGHER_IS_BETTER")
    void testMetricDirections() {
        assertThat(MetricDirection.forMetric("rare_request_fulfillment"))
            .isEqualTo(MetricDirection.HIGHER_IS_BETTER);
        assertThat(MetricDirection.forMetric("common_request_fulfillment"))
            .isEqualTo(MetricDirection.HIGHER_IS_BETTER);
        assertThat(MetricDirection.forMetric("fulfillment_within_deadline"))
            .isEqualTo(MetricDirection.HIGHER_IS_BETTER);
        assertThat(MetricDirection.forMetric("rare_type_waste_rate"))
            .isEqualTo(MetricDirection.LOWER_IS_BETTER);
    }

    @Test
    @DisplayName("MetricCalculator correctly calculates rare and common request fulfillment")
    void testMetricCalculation() {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        Instant deadline = now.plus(Duration.ofHours(4));

        // 2 rare requests: REQ-1 (O-), REQ-2 (B-)
        // 2 common requests: REQ-3 (O+), REQ-4 (A+)
        List<BloodRequest> requests = List.of(
            new BloodRequest("REQ-1", BloodGroup.O_NEG, 1, Urgency.URGENT, "Z1", now, deadline),
            new BloodRequest("REQ-2", BloodGroup.B_NEG, 1, Urgency.URGENT, "Z1", now, deadline),
            new BloodRequest("REQ-3", BloodGroup.O_POS, 1, Urgency.URGENT, "Z1", now, deadline),
            new BloodRequest("REQ-4", BloodGroup.A_POS, 1, Urgency.URGENT, "Z1", now, deadline)
        );

        // Fulfill REQ-1 (rare) and REQ-3 (common), leave REQ-2 and REQ-4 unfulfilled
        List<SimulationEvent> events = List.of(
            new SimulationEvent(now.plusSeconds(600), SimulationEventType.REQUEST_FULFILLED, "REQ-1", "D1", 1, "Fulfilled"),
            new SimulationEvent(now.plusSeconds(1200), SimulationEventType.REQUEST_FULFILLED, "REQ-3", "D2", 1, "Fulfilled")
        );

        SimulationResult result = new SimulationResult(
            events,
            4, // totalRequests
            2, // fulfilled
            2, // unfulfilled
            2, // total notifications
            2, // accepted
            0, // declined
            0, // expired notifications
            Duration.ofHours(24)
        );

        WorldGenerator worldGen = new WorldGenerator(SimulationParameters.loadDefault());
        World world = worldGen.generate(1001L);

        EvaluationMetrics metrics = MetricCalculator.calculate(result, requests, world, null);

        // Rare fulfillment: 1 fulfilled out of 2 = 0.50
        assertThat(metrics.rareRequestFulfillment()).isCloseTo(0.50, within(1e-9));

        // Common fulfillment: 1 fulfilled out of 2 = 0.50
        assertThat(metrics.commonRequestFulfillment()).isCloseTo(0.50, within(1e-9));

        // Overall fulfillment: 2 out of 4 = 0.50
        assertThat(metrics.fulfillmentWithinDeadline()).isCloseTo(0.50, within(1e-9));

        // Check tidy rows contain both metrics
        List<TidyMetricRow> rows = metrics.toTidyRows("E3_1.0X", "S6", "M1", 1001L);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.metric()).isEqualTo("rare_request_fulfillment");
            assertThat(r.value()).isCloseTo(0.50, within(1e-9));
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.metric()).isEqualTo("common_request_fulfillment");
            assertThat(r.value()).isCloseTo(0.50, within(1e-9));
        });
    }

    @Test
    @DisplayName("Empty or zero-category request lists default to 0.0 without division by zero")
    void testZeroCategoryHandling() {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        Instant deadline = now.plus(Duration.ofHours(4));

        // Only common requests
        List<BloodRequest> requests = List.of(
            new BloodRequest("REQ-1", BloodGroup.O_POS, 1, Urgency.URGENT, "Z1", now, deadline)
        );

        SimulationResult result = new SimulationResult(
            List.of(), 1, 0, 1, 0, 0, 0, 0, Duration.ofHours(24)
        );

        WorldGenerator worldGen = new WorldGenerator(SimulationParameters.loadDefault());
        World world = worldGen.generate(1001L);

        EvaluationMetrics metrics = MetricCalculator.calculate(result, requests, world, null);

        assertThat(metrics.rareRequestFulfillment()).isEqualTo(0.0);
        assertThat(metrics.commonRequestFulfillment()).isEqualTo(0.0);
    }
}
